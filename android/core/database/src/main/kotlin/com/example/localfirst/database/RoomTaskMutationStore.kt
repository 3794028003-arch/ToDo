package com.example.localfirst.database

import androidx.room.withTransaction
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.ReminderScheduleCalculator
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus

class RoomTaskMutationStore(
    private val database: TaskDatabase,
) {
    data class CompletionResult(
        val completedTitle: String,
        val nextTaskId: String? = null,
        val nextReminderAtMillis: Long? = null,
        val nextReminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    )

    suspend fun createTaskAndEnqueue(
        taskId: String,
        title: String,
        operationId: String,
        reminderAtMillis: Long? = null,
        reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
        queueSequence: Long? = null,
    ) = database.withTransaction {
        val sequence = queueSequence ?: database.syncOperationDao().nextQueueSequence()
        database.taskDao().upsert(
            TaskEntity(
                id = taskId,
                title = title,
                status = TaskStatus.TODO,
                reminderAtMillis = reminderAtMillis,
                reminderRepeat = reminderRepeat,
                isPinned = false,
                startDateMillis = startDateMillis,
                dueDateMillis = dueDateMillis,
                localRevision = 1,
                serverVersion = null,
                deletedAtMillis = null,
                createdSequence = sequence,
            ),
        )
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationId,
                taskId = taskId,
                queueSequence = sequence,
                taskRevision = 1,
                type = OperationType.CREATE,
                title = title,
                reminderAtMillis = reminderAtMillis,
                reminderRepeat = reminderRepeat.name,
                isPinned = false,
                startDateMillis = startDateMillis,
                dueDateMillis = dueDateMillis,
                desiredStatus = TaskStatus.TODO,
            ),
        )
    }

    suspend fun changeStatusAndEnqueue(
        taskId: String,
        status: TaskStatus,
        operationId: String,
        queueSequence: Long? = null,
    ) = mutateExistingAndEnqueue(
        taskId = taskId,
        operationId = operationId,
        queueSequence = queueSequence,
        type = OperationType.CHANGE_STATUS,
        desiredStatus = status,
    ) { current, nextRevision ->
        current.copy(
            status = status,
            reminderAtMillis = if (status == TaskStatus.DONE) null else current.reminderAtMillis,
            reminderRepeat = if (status == TaskStatus.DONE) ReminderRepeat.NONE else current.reminderRepeat,
            localRevision = nextRevision,
        )
    }

    suspend fun completeAndEnqueue(
        taskId: String,
        nowMillis: Long,
        taskIdFactory: () -> String,
        operationIdFactory: () -> String,
    ): CompletionResult? = database.withTransaction {
        val current = checkNotNull(database.taskDao().findById(taskId)) {
            "Task $taskId does not exist"
        }
        if (current.deletedAtMillis != null || current.status == TaskStatus.DONE) {
            return@withTransaction null
        }

        val completionSequence = database.syncOperationDao().nextQueueSequence()
        val completionRevision = current.localRevision + 1
        val predecessor = database.syncOperationDao().latestBefore(taskId, completionSequence)
        database.taskDao().upsert(
            current.copy(
                status = TaskStatus.DONE,
                reminderAtMillis = null,
                reminderRepeat = ReminderRepeat.NONE,
                localRevision = completionRevision,
                lastModifiedSequence = completionSequence,
            ),
        )
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationIdFactory(),
                taskId = taskId,
                queueSequence = completionSequence,
                taskRevision = completionRevision,
                predecessorOperationId = predecessor?.operationId,
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = expectedServerVersion(current, predecessor),
            ),
        )

        val nextReminder = current.reminderAtMillis?.let { scheduled ->
            ReminderScheduleCalculator.nextTriggerAfter(scheduled, current.reminderRepeat)?.let { next ->
                ReminderScheduleCalculator.nextFutureTrigger(
                    scheduledAtMillis = next,
                    repeat = current.reminderRepeat,
                    nowMillis = nowMillis,
                )
            }
        }
        if (current.reminderRepeat == ReminderRepeat.NONE || nextReminder == null) {
            return@withTransaction CompletionResult(completedTitle = current.title)
        }

        val nextTaskId = taskIdFactory()
        val createSequence = database.syncOperationDao().nextQueueSequence()
        database.taskDao().upsert(
            TaskEntity(
                id = nextTaskId,
                title = current.title,
                status = TaskStatus.TODO,
                reminderAtMillis = nextReminder,
                reminderRepeat = current.reminderRepeat,
                isPinned = current.isPinned,
                startDateMillis = current.startDateMillis,
                dueDateMillis = current.dueDateMillis,
                localRevision = 1,
                serverVersion = null,
                deletedAtMillis = null,
                createdSequence = createSequence,
            ),
        )
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationIdFactory(),
                taskId = nextTaskId,
                queueSequence = createSequence,
                taskRevision = 1,
                type = OperationType.CREATE,
                title = current.title,
                reminderAtMillis = nextReminder,
                reminderRepeat = current.reminderRepeat.name,
                isPinned = current.isPinned,
                startDateMillis = current.startDateMillis,
                dueDateMillis = current.dueDateMillis,
                desiredStatus = TaskStatus.TODO,
            ),
        )
        CompletionResult(
            completedTitle = current.title,
            nextTaskId = nextTaskId,
            nextReminderAtMillis = nextReminder,
            nextReminderRepeat = current.reminderRepeat,
        )
    }

    suspend fun updateTaskAndEnqueue(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
        operationId: String,
        queueSequence: Long? = null,
    ) = mutateExistingAndEnqueue(
        taskId = taskId,
        operationId = operationId,
        queueSequence = queueSequence,
        type = OperationType.UPDATE,
        title = title,
        reminderAtMillis = reminderAtMillis,
        reminderRepeat = reminderRepeat.name,
        startDateMillis = startDateMillis,
        dueDateMillis = dueDateMillis,
    ) { current, nextRevision ->
        current.copy(
            title = title,
            reminderAtMillis = reminderAtMillis,
            reminderRepeat = reminderRepeat,
            startDateMillis = startDateMillis,
            dueDateMillis = dueDateMillis,
            localRevision = nextRevision,
        )
    }

    suspend fun setPinnedAndEnqueue(
        taskId: String,
        isPinned: Boolean,
        operationId: String,
        queueSequence: Long? = null,
    ) = database.withTransaction {
        val current = checkNotNull(database.taskDao().findById(taskId)) {
            "Task $taskId does not exist"
        }
        if (current.isPinned == isPinned || current.deletedAtMillis != null) {
            return@withTransaction
        }
        val sequence = queueSequence ?: database.syncOperationDao().nextQueueSequence()
        val nextRevision = current.localRevision + 1
        val predecessor = database.syncOperationDao().latestBefore(taskId, sequence)
        database.taskDao().upsert(
            current.copy(
                isPinned = isPinned,
                startDateMillis = current.startDateMillis,
                dueDateMillis = current.dueDateMillis,
                localRevision = nextRevision,
                lastModifiedSequence = sequence,
            ),
        )
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationId,
                taskId = taskId,
                queueSequence = sequence,
                taskRevision = nextRevision,
                predecessorOperationId = predecessor?.operationId,
                type = OperationType.UPDATE,
                title = current.title,
                reminderAtMillis = current.reminderAtMillis,
                reminderRepeat = current.reminderRepeat.name,
                isPinned = isPinned,
                baseServerVersion = expectedServerVersion(current, predecessor),
            ),
        )
    }

    suspend fun deleteTaskAndEnqueue(
        taskId: String,
        deletedAtMillis: Long,
        operationId: String,
        queueSequence: Long? = null,
    ) = mutateExistingAndEnqueue(
        taskId = taskId,
        operationId = operationId,
        queueSequence = queueSequence,
        type = OperationType.DELETE,
    ) { current, nextRevision ->
        current.copy(
            localRevision = nextRevision,
            reminderAtMillis = null,
            reminderRepeat = ReminderRepeat.NONE,
            deletedAtMillis = deletedAtMillis,
        )
    }

    suspend fun requestPermanentDeletion(taskId: String) = database.withTransaction {
        val task = database.taskDao().findById(taskId) ?: return@withTransaction
        if (task.deletedAtMillis == null) return@withTransaction
        database.taskDao().requestPermanentDeletion(taskId)
        val latestOperation = database.syncOperationDao().latestForTask(taskId)
        if (latestOperation?.type == OperationType.DELETE && latestOperation.state in TERMINAL_OPERATION_STATES) {
            database.syncOperationDao().deleteForTask(taskId)
            database.taskDao().deleteById(taskId)
        }
    }

    suspend fun pendingOperationCount(): Int =
        database.syncOperationDao().countNonTerminal(TERMINAL_OPERATION_STATES)

    private suspend fun mutateExistingAndEnqueue(
        taskId: String,
        operationId: String,
        queueSequence: Long?,
        type: OperationType,
        title: String? = null,
        reminderAtMillis: Long? = null,
        reminderRepeat: String? = null,
        isPinned: Boolean? = null,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
        desiredStatus: TaskStatus? = null,
        mutateTask: (current: TaskEntity, nextRevision: Long) -> TaskEntity,
    ) = database.withTransaction {
        val current = checkNotNull(database.taskDao().findById(taskId)) {
            "Task $taskId does not exist"
        }
        if (current.deletedAtMillis != null ||
            (desiredStatus != null && current.status == desiredStatus)
        ) {
            return@withTransaction
        }
        val sequence = queueSequence ?: database.syncOperationDao().nextQueueSequence()
        val nextRevision = current.localRevision + 1
        val predecessor = database.syncOperationDao().latestBefore(taskId, sequence)

        val mutatedTask = mutateTask(current, nextRevision)
        val orderedTask = when (type) {
            OperationType.UPDATE,
            OperationType.CHANGE_STATUS -> mutatedTask.copy(lastModifiedSequence = sequence)

            OperationType.CREATE,
            OperationType.DELETE -> mutatedTask
        }
        database.taskDao().upsert(orderedTask)
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationId,
                taskId = taskId,
                queueSequence = sequence,
                taskRevision = nextRevision,
                predecessorOperationId = predecessor?.operationId,
                type = type,
                title = title,
                reminderAtMillis = reminderAtMillis,
                reminderRepeat = reminderRepeat,
                isPinned = isPinned,
                startDateMillis = startDateMillis,
                dueDateMillis = dueDateMillis,
                desiredStatus = desiredStatus,
                baseServerVersion = expectedServerVersion(current, predecessor),
            ),
        )
    }
}

private fun expectedServerVersion(
    current: TaskEntity,
    predecessor: SyncOperationEntity?,
): Long? = predecessor?.acknowledgedServerVersion
    ?: predecessor?.baseServerVersion?.plus(1)
    ?: if (predecessor?.type == OperationType.CREATE) 1 else current.serverVersion

private fun pendingOperation(
    operationId: String,
    taskId: String,
    queueSequence: Long,
    taskRevision: Long,
    type: OperationType,
    predecessorOperationId: String? = null,
    title: String? = null,
    reminderAtMillis: Long? = null,
    reminderRepeat: String? = null,
    isPinned: Boolean? = null,
    startDateMillis: Long? = null,
    dueDateMillis: Long? = null,
    desiredStatus: TaskStatus? = null,
    baseServerVersion: Long? = null,
): SyncOperationEntity = SyncOperationEntity(
    operationId = operationId,
    taskId = taskId,
    queueSequence = queueSequence,
    taskRevision = taskRevision,
    predecessorOperationId = predecessorOperationId,
    type = type,
    title = title,
    reminderAtMillis = reminderAtMillis,
    reminderRepeat = reminderRepeat,
    isPinned = isPinned,
    startDateMillis = startDateMillis,
    dueDateMillis = dueDateMillis,
    desiredStatus = desiredStatus,
    baseServerVersion = baseServerVersion,
    state = OperationState.PENDING,
    attemptCount = 0,
    nextAttemptAtMillis = null,
    leaseUntilMillis = null,
    acknowledgedServerVersion = null,
    lastErrorCode = null,
)
