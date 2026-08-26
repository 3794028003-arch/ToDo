package com.example.localfirst.database

import androidx.room.withTransaction
import com.example.localfirst.sync.LocalTask
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.SyncOperation
import com.example.localfirst.sync.SyncStore

class RoomSyncStore(
    private val database: TaskDatabase,
) : SyncStore {
    override suspend fun recoverExpiredLeases(nowMillis: Long) {
        database.syncOperationDao().recoverExpiredLeases(
            nowMillis = nowMillis,
            inFlightState = OperationState.IN_FLIGHT,
            pendingState = OperationState.PENDING,
        )
    }

    override suspend fun dueOperations(nowMillis: Long): List<SyncOperation> =
        database.syncOperationDao().dueOperations(
            nowMillis = nowMillis,
            pendingState = OperationState.PENDING,
            retryState = OperationState.RETRY_WAIT,
            terminalStates = TERMINAL_OPERATION_STATES,
        ).map(SyncOperationEntity::toDomain)

    override suspend fun markInFlight(operationIds: Set<String>, leaseUntilMillis: Long) {
        if (operationIds.isEmpty()) return
        database.syncOperationDao().markInFlight(
            operationIds = operationIds,
            leaseUntilMillis = leaseUntilMillis,
            inFlightState = OperationState.IN_FLIGHT,
        )
    }

    override suspend fun markSynced(operationId: String, serverVersion: Long) {
        database.withTransaction {
            val operation = database.syncOperationDao().findById(operationId) ?: return@withTransaction
            database.syncOperationDao().markSynced(
                operationId = operationId,
                serverVersion = serverVersion,
                syncedState = OperationState.SYNCED,
            )
            database.taskDao().findById(operation.taskId)?.let { task ->
                if (operation.type == OperationType.DELETE && task.permanentDeletionRequested) {
                    database.syncOperationDao().deleteForTask(operation.taskId)
                    database.taskDao().deleteById(operation.taskId)
                } else {
                    database.taskDao().upsert(task.copy(serverVersion = serverVersion))
                }
            }
        }
    }

    override suspend fun markRetry(
        operationId: String,
        errorCode: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
    ) {
        database.syncOperationDao().markRetry(
            operationId = operationId,
            errorCode = errorCode,
            attemptCount = attemptCount,
            nextAttemptAtMillis = nextAttemptAtMillis,
            retryState = OperationState.RETRY_WAIT,
        )
    }

    override suspend fun resolveServerDeleted(
        operationId: String,
        taskId: String,
        deletedAtMillis: Long,
        tombstoneVersion: Long,
    ) {
        database.withTransaction {
            val operation = database.syncOperationDao().findById(operationId) ?: return@withTransaction
            database.syncOperationDao().markResolvedConflict(
                operationId = operationId,
                serverVersion = tombstoneVersion,
                errorCode = SERVER_DELETED_ERROR_CODE,
                resolvedState = OperationState.RESOLVED_CONFLICT,
            )
            database.taskDao().findById(taskId)?.let { task ->
                val deletedTask = task.copy(
                        serverVersion = tombstoneVersion,
                        deletedAtMillis = deletedAtMillis,
                        serverDeletionNoticePending = true,
                        serverDeletionNoticeSequence = operation.queueSequence,
                    )
                database.taskDao().upsert(deletedTask)
            }
            database.syncOperationDao().supersedeLaterOperations(
                taskId = taskId,
                afterSequence = operation.queueSequence,
                terminalStates = TERMINAL_OPERATION_STATES,
                supersededState = OperationState.SUPERSEDED,
                errorCode = SERVER_DELETED_ERROR_CODE,
            )
            database.taskDao().findById(taskId)?.takeIf(TaskEntity::permanentDeletionRequested)?.let {
                database.syncOperationDao().deleteForTask(taskId)
                database.taskDao().deleteById(taskId)
            }
        }
    }

    override suspend fun resolveVersionConflict(
        operationId: String,
        taskId: String,
        serverVersion: Long,
    ) {
        database.withTransaction {
            val operation = database.syncOperationDao().findById(operationId) ?: return@withTransaction
            database.syncOperationDao().markResolvedConflict(
                operationId = operation.operationId,
                serverVersion = serverVersion,
                errorCode = VERSION_CONFLICT_ERROR_CODE,
                resolvedState = OperationState.RESOLVED_CONFLICT,
            )
            database.taskDao().findById(taskId)?.let { task ->
                database.taskDao().upsert(task.copy(serverVersion = serverVersion))
            }
        }
    }

    override suspend fun operation(operationId: String): SyncOperation? =
        database.syncOperationDao().findById(operationId)?.toDomain()

    override suspend fun task(taskId: String): LocalTask? =
        database.taskDao().findById(taskId)?.toDomain()

    override suspend fun allOperations(): List<SyncOperation> =
        database.syncOperationDao().all().map(SyncOperationEntity::toDomain)
}
