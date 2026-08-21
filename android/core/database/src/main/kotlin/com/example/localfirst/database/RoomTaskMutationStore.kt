package com.example.localfirst.database

import androidx.room.withTransaction
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus

class RoomTaskMutationStore(
    private val database: TaskDatabase,
) {
    suspend fun createTaskAndEnqueue(
        taskId: String,
        title: String,
        operationId: String,
        queueSequence: Long? = null,
    ) = database.withTransaction {
        val sequence = queueSequence ?: database.syncOperationDao().nextQueueSequence()
        database.taskDao().upsert(
            TaskEntity(
                id = taskId,
                title = title,
                status = TaskStatus.TODO,
                localRevision = 1,
                serverVersion = null,
                deletedAtMillis = null,
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
                localRevision = nextRevision,
        )
    }

    suspend fun updateTitleAndEnqueue(
        taskId: String,
        title: String,
        operationId: String,
        queueSequence: Long? = null,
    ) = mutateExistingAndEnqueue(
        taskId = taskId,
        operationId = operationId,
        queueSequence = queueSequence,
        type = OperationType.UPDATE,
        title = title,
    ) { current, nextRevision ->
        current.copy(
                title = title,
                localRevision = nextRevision,
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
            deletedAtMillis = deletedAtMillis,
        )
    }

    suspend fun pendingOperationCount(): Int =
        database.syncOperationDao().countNonTerminal(TERMINAL_OPERATION_STATES)

    private suspend fun mutateExistingAndEnqueue(
        taskId: String,
        operationId: String,
        queueSequence: Long?,
        type: OperationType,
        title: String? = null,
        desiredStatus: TaskStatus? = null,
        mutateTask: (current: TaskEntity, nextRevision: Long) -> TaskEntity,
    ) = database.withTransaction {
        val current = checkNotNull(database.taskDao().findById(taskId)) {
            "Task $taskId does not exist"
        }
        val sequence = queueSequence ?: database.syncOperationDao().nextQueueSequence()
        val nextRevision = current.localRevision + 1
        val predecessor = database.syncOperationDao().latestBefore(taskId, sequence)

        database.taskDao().upsert(mutateTask(current, nextRevision))
        database.syncOperationDao().insert(
            pendingOperation(
                operationId = operationId,
                taskId = taskId,
                queueSequence = sequence,
                taskRevision = nextRevision,
                predecessorOperationId = predecessor?.operationId,
                type = type,
                title = title,
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
    desiredStatus = desiredStatus,
    baseServerVersion = baseServerVersion,
    state = OperationState.PENDING,
    attemptCount = 0,
    nextAttemptAtMillis = null,
    leaseUntilMillis = null,
    acknowledgedServerVersion = null,
    lastErrorCode = null,
)
