package com.example.localfirst.database

import com.example.localfirst.data.Task
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.sync.TaskStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTaskRepository(
    database: TaskDatabase,
    private val scheduleSync: () -> Unit,
    private val taskIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val operationIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : TaskRepository {
    private val mutations = RoomTaskMutationStore(database)
    private val taskDao = database.taskDao()

    override val tasks: Flow<List<Task>> = taskDao.observeActive().map { entities ->
        entities.map { entity ->
            Task(
                id = entity.id,
                title = entity.title,
                status = entity.status,
            )
        }
    }

    override val serverDeletionNotices: Flow<List<ServerDeletionNotice>> =
        taskDao.observePendingServerDeletionNotices().map { entities ->
            entities.map { entity ->
                ServerDeletionNotice(
                    taskId = entity.id,
                    title = entity.title,
                )
            }
        }

    override suspend fun createTask(title: String): String {
        val taskId = taskIdFactory()
        mutations.createTaskAndEnqueue(
            taskId = taskId,
            title = title,
            operationId = operationIdFactory(),
        )
        requestBackgroundSync()
        return taskId
    }

    override suspend fun updateTitle(taskId: String, title: String) {
        mutations.updateTitleAndEnqueue(
            taskId = taskId,
            title = title,
            operationId = operationIdFactory(),
        )
        requestBackgroundSync()
    }

    override suspend fun changeStatus(taskId: String, status: TaskStatus) {
        mutations.changeStatusAndEnqueue(
            taskId = taskId,
            status = status,
            operationId = operationIdFactory(),
        )
        requestBackgroundSync()
    }

    override suspend fun deleteTask(taskId: String) {
        mutations.deleteTaskAndEnqueue(
            taskId = taskId,
            deletedAtMillis = nowMillis(),
            operationId = operationIdFactory(),
        )
        requestBackgroundSync()
    }

    override suspend fun dismissServerDeletionNotice(taskId: String) {
        taskDao.dismissServerDeletionNotice(taskId)
    }

    private fun requestBackgroundSync() {
        try {
            scheduleSync()
        } catch (_: Exception) {
            // The durable outbox remains authoritative; a later app start can schedule it again.
        }
    }
}
