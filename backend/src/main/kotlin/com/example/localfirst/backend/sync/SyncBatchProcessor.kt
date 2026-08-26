package com.example.localfirst.backend.sync

import java.time.Clock

class SyncBatchProcessor(
    private val idempotency: IdempotencyExecutor,
    private val tasks: ServerTaskStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun process(
        operations: List<IncomingSyncOperation>,
    ): List<BackendOperationResult> = operations.map { operation ->
        idempotency.executeOnce(
            operationId = operation.operationId,
            requestHash = operation.requestHash,
        ) {
            processOne(operation)
        }
    }

    private fun processOne(
        operation: IncomingSyncOperation,
    ): BackendOperationResult {
        val current = tasks.find(operation.taskId)
        if (current?.deletedAtMillis != null) {
            return BackendOperationResult.TaskDeleted(
                operationId = operation.operationId,
                tombstoneVersion = current.version,
                deletedAtMillis = current.deletedAtMillis,
            )
        }

        return when (operation.type) {
            ServerOperationType.CREATE -> applied(
                operation = operation,
                changed = current ?: tasks.create(
                    taskId = operation.taskId,
                    title = requireNotNull(operation.title),
                    reminderAtMillis = operation.reminderAtMillis,
                    reminderRepeat = operation.reminderRepeat ?: "NONE",
                    isPinned = operation.isPinned ?: false,
                    startDateMillis = operation.startDateMillis,
                    dueDateMillis = operation.dueDateMillis,
                ),
            )

            ServerOperationType.CHANGE_STATUS -> applyVersioned(operation, current) { expectedVersion ->
                tasks.changeStatus(
                    taskId = operation.taskId,
                    status = requireNotNull(operation.desiredStatus),
                    expectedVersion = expectedVersion,
                )
            }

            ServerOperationType.UPDATE -> applyVersioned(operation, current) { expectedVersion ->
                tasks.updateDetails(
                    taskId = operation.taskId,
                    title = requireNotNull(operation.title),
                    reminderAtMillis = operation.reminderAtMillis,
                    reminderRepeat = operation.reminderRepeat,
                    isPinned = operation.isPinned,
                    startDateMillis = operation.startDateMillis,
                    dueDateMillis = operation.dueDateMillis,
                    expectedVersion = expectedVersion,
                )
            }

            ServerOperationType.DELETE -> applyVersioned(operation, current) { expectedVersion ->
                tasks.delete(
                    taskId = operation.taskId,
                    expectedVersion = expectedVersion,
                    deletedAtMillis = clock.millis(),
                )
            }
        }
    }

    private fun applyVersioned(
        operation: IncomingSyncOperation,
        current: ServerTask?,
        mutation: (expectedVersion: Long) -> ServerTask?,
    ): BackendOperationResult {
        val existing = requireNotNull(current) { "Task ${operation.taskId} does not exist" }
        val expectedVersion = requireNotNull(operation.baseServerVersion)
        return if (existing.version != expectedVersion) {
            BackendOperationResult.VersionConflict(
                operationId = operation.operationId,
                serverVersion = existing.version,
            )
        } else {
            mutation(expectedVersion)?.let { changed -> applied(operation, changed) }
                ?: versionConflictOrDeletion(operation)
        }
    }

    private fun versionConflictOrDeletion(operation: IncomingSyncOperation): BackendOperationResult {
        val latest = requireNotNull(tasks.find(operation.taskId))
        return if (latest.deletedAtMillis != null) {
            BackendOperationResult.TaskDeleted(
                operationId = operation.operationId,
                tombstoneVersion = latest.version,
                deletedAtMillis = latest.deletedAtMillis,
            )
        } else {
            BackendOperationResult.VersionConflict(
                operationId = operation.operationId,
                serverVersion = latest.version,
            )
        }
    }

    private fun applied(
        operation: IncomingSyncOperation,
        changed: ServerTask,
    ): BackendOperationResult.Applied = BackendOperationResult.Applied(
        operationId = operation.operationId,
        serverVersion = changed.version,
    )
}
