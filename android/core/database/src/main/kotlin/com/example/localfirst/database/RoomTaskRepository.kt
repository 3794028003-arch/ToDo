package com.example.localfirst.database

import androidx.room.withTransaction
import com.example.localfirst.data.Task
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.data.RemoteTask
import com.example.localfirst.data.TaskReminderScheduler
import com.example.localfirst.sync.TaskStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    private val database: TaskDatabase,
    private val scheduleSync: () -> Unit,
    private val reminderScheduler: TaskReminderScheduler? = null,
    private val taskIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val operationIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : TaskRepository {
    private val mutations = RoomTaskMutationStore(database)
    private val taskDao = database.taskDao()

    override val tasks: Flow<List<Task>> = taskDao.observeActive().map { entities ->
        entities.map(TaskEntity::toTask)
    }

    override val deletedTasks: Flow<List<Task>> = taskDao.observeDeleted().map { entities ->
        val cutoff = nowMillis() - TRASH_RETENTION_MILLIS
        entities.filter { entity -> (entity.deletedAtMillis ?: Long.MIN_VALUE) >= cutoff }
            .map(TaskEntity::toTask)
    }

    override val serverDeletionNotices: Flow<List<ServerDeletionNotice>> =
        taskDao.observePendingServerDeletionNotices().map { entities ->
            entities.map { entity ->
                ServerDeletionNotice(
                    taskId = entity.id,
                    title = entity.title,
                    deletedAtMillis = checkNotNull(entity.deletedAtMillis),
                )
            }
        }

    override suspend fun createTask(
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ): String {
        val taskId = taskIdFactory()
        mutations.createTaskAndEnqueue(
            taskId = taskId,
            title = title,
            reminderAtMillis = reminderAtMillis,
            reminderRepeat = reminderRepeat,
            startDateMillis = startDateMillis,
            dueDateMillis = dueDateMillis,
            operationId = operationIdFactory(),
        )
        updateReminder(taskId, title, reminderAtMillis, reminderRepeat)
        requestBackgroundSync()
        return taskId
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ) {
        mutations.updateTaskAndEnqueue(
            taskId = taskId,
            title = title,
            reminderAtMillis = reminderAtMillis,
            reminderRepeat = reminderRepeat,
            startDateMillis = startDateMillis,
            dueDateMillis = dueDateMillis,
            operationId = operationIdFactory(),
        )
        updateReminder(taskId, title, reminderAtMillis, reminderRepeat)
        requestBackgroundSync()
    }

    override suspend fun changeStatus(taskId: String, status: TaskStatus) {
        if (status == TaskStatus.DONE) {
            val completion = mutations.completeAndEnqueue(
                taskId = taskId,
                nowMillis = nowMillis(),
                taskIdFactory = taskIdFactory,
                operationIdFactory = operationIdFactory,
            ) ?: return
            reminderScheduler?.cancel(taskId)
            completion.nextTaskId?.let { nextTaskId ->
                updateReminder(
                    taskId = nextTaskId,
                    title = completion.completedTitle,
                    reminderAtMillis = completion.nextReminderAtMillis,
                    reminderRepeat = completion.nextReminderRepeat,
                )
            }
        } else {
            mutations.changeStatusAndEnqueue(
                taskId = taskId,
                status = status,
                operationId = operationIdFactory(),
            )
        }
        requestBackgroundSync()
    }

    override suspend fun setPinned(taskId: String, isPinned: Boolean) {
        mutations.setPinnedAndEnqueue(
            taskId = taskId,
            isPinned = isPinned,
            operationId = operationIdFactory(),
        )
        requestBackgroundSync()
    }

    override suspend fun reorderTasks(taskIdsInDisplayOrder: List<String>) {
        database.withTransaction {
            taskIdsInDisplayOrder.forEachIndexed { index, taskId ->
                taskDao.setManualOrder(taskId, index.toLong())
            }
        }
    }

    override suspend fun deleteTask(taskId: String) {
        mutations.deleteTaskAndEnqueue(
            taskId = taskId,
            deletedAtMillis = nowMillis(),
            operationId = operationIdFactory(),
        )
        reminderScheduler?.cancel(taskId)
        requestBackgroundSync()
    }

    override suspend fun permanentlyDeleteTask(taskId: String) {
        mutations.requestPermanentDeletion(taskId)
        reminderScheduler?.cancel(taskId)
        requestBackgroundSync()
    }

    override suspend fun dismissServerDeletionNotices(taskIds: Set<String>) {
        if (taskIds.isNotEmpty()) taskDao.dismissServerDeletionNotices(taskIds)
    }

    override suspend fun mergeRemoteTasks(tasks: List<RemoteTask>) {
        tasks.forEach { remote ->
            val existing = taskDao.findById(remote.id)
            if (existing == null || (existing.serverVersion != null && remote.version >= existing.serverVersion)) {
                val newlyDeletedOnAnotherDevice =
                    existing?.deletedAtMillis == null && existing != null && remote.deletedAtMillis != null
                taskDao.upsert(
                    TaskEntity(
                        id = remote.id,
                        title = remote.title,
                        status = remote.status,
                        reminderAtMillis = remote.reminderAtMillis,
                        reminderRepeat = remote.reminderRepeat,
                        isPinned = remote.isPinned,
                        startDateMillis = remote.startDateMillis,
                        dueDateMillis = remote.dueDateMillis,
                        localRevision = existing?.localRevision ?: 1,
                        serverVersion = remote.version,
                        deletedAtMillis = remote.deletedAtMillis,
                        permanentDeletionRequested = existing?.permanentDeletionRequested ?: false,
                        serverDeletionNoticePending =
                            existing?.serverDeletionNoticePending == true || newlyDeletedOnAnotherDevice,
                        serverDeletionNoticeSequence = when {
                            existing?.serverDeletionNoticePending == true -> existing.serverDeletionNoticeSequence
                            newlyDeletedOnAnotherDevice -> remote.deletedAtMillis
                            else -> null
                        },
                        createdSequence = existing?.createdSequence ?: nowMillis(),
                        lastModifiedSequence = existing?.lastModifiedSequence,
                        manualOrder = existing?.manualOrder,
                    ),
                )
                updateReminder(
                    remote.id,
                    remote.title,
                    remote.reminderAtMillis.takeIf { remote.deletedAtMillis == null },
                    remote.reminderRepeat,
                )
            }
        }
    }

    private fun requestBackgroundSync() {
        try {
            scheduleSync()
        } catch (_: Exception) {
            // The durable outbox remains authoritative; a later app start can schedule it again.
        }
    }

    private fun updateReminder(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
    ) {
        if (reminderAtMillis == null) {
            reminderScheduler?.cancel(taskId)
        } else {
            reminderScheduler?.schedule(taskId, title, reminderAtMillis, reminderRepeat)
        }
    }

    private companion object {
        const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000
    }
}

private fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    status = status,
    reminderAtMillis = reminderAtMillis,
    reminderRepeat = reminderRepeat,
    isPinned = isPinned,
    startDateMillis = startDateMillis,
    dueDateMillis = dueDateMillis,
    deletedAtMillis = deletedAtMillis,
)
