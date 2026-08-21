package com.example.localfirst.sync

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.yield

internal class DurableTestBacking {
    val tasks = linkedMapOf<String, LocalTask>()
    val operations = linkedMapOf<String, SyncOperation>()
}

internal class ReopenableFakeSyncStore(
    private val backing: DurableTestBacking,
) : SyncStore {
    fun seedTask(task: LocalTask) {
        backing.tasks[task.id] = task
    }

    fun seedOperation(operation: SyncOperation) {
        backing.operations[operation.operationId] = operation
    }

    override suspend fun recoverExpiredLeases(nowMillis: Long) {
        backing.operations.replaceAll { _, operation ->
            if (operation.state == OperationState.IN_FLIGHT &&
                operation.leaseUntilMillis != null &&
                operation.leaseUntilMillis <= nowMillis
            ) {
                operation.copy(state = OperationState.PENDING, leaseUntilMillis = null)
            } else {
                operation
            }
        }
    }

    override suspend fun dueOperations(nowMillis: Long): List<SyncOperation> =
        backing.operations.values
            .asSequence()
            .filter { operation ->
                operation.state == OperationState.PENDING ||
                    (operation.state == OperationState.RETRY_WAIT &&
                        requireNotNull(operation.nextAttemptAtMillis) <= nowMillis)
            }
            .filter { operation ->
                val predecessor = operation.predecessorOperationId
                    ?.let(backing.operations::get)
                predecessor == null || predecessor.state.isTerminal
            }
            .sortedBy(SyncOperation::queueSequence)
            .toList()

    override suspend fun markInFlight(
        operationIds: Set<String>,
        leaseUntilMillis: Long,
    ) {
        operationIds.forEach { operationId ->
            backing.operations.computeIfPresent(operationId) { _, operation ->
                operation.copy(
                    state = OperationState.IN_FLIGHT,
                    leaseUntilMillis = leaseUntilMillis,
                )
            }
        }
    }

    override suspend fun markSynced(operationId: String, serverVersion: Long) {
        backing.operations.computeIfPresent(operationId) { _, operation ->
            operation.copy(
                state = OperationState.SYNCED,
                acknowledgedServerVersion = serverVersion,
                leaseUntilMillis = null,
                nextAttemptAtMillis = null,
            )
        }
    }

    override suspend fun markRetry(
        operationId: String,
        errorCode: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
    ) {
        backing.operations.computeIfPresent(operationId) { _, operation ->
            operation.copy(
                state = OperationState.RETRY_WAIT,
                attemptCount = attemptCount,
                nextAttemptAtMillis = nextAttemptAtMillis,
                leaseUntilMillis = null,
                lastErrorCode = errorCode,
            )
        }
    }

    override suspend fun resolveServerDeleted(
        operationId: String,
        taskId: String,
        deletedAtMillis: Long,
        tombstoneVersion: Long,
    ) {
        val conflicted = requireNotNull(backing.operations[operationId])
        backing.operations[operationId] = conflicted.copy(
            state = OperationState.RESOLVED_CONFLICT,
            acknowledgedServerVersion = tombstoneVersion,
            leaseUntilMillis = null,
            lastErrorCode = "TASK_DELETED",
        )
        backing.tasks.computeIfPresent(taskId) { _, task ->
            task.copy(
                deletedAtMillis = deletedAtMillis,
                serverVersion = tombstoneVersion,
            )
        }
        backing.operations.replaceAll { _, operation ->
            if (operation.taskId == taskId &&
                operation.queueSequence > conflicted.queueSequence &&
                !operation.state.isTerminal
            ) {
                operation.copy(
                    state = OperationState.SUPERSEDED,
                    leaseUntilMillis = null,
                    lastErrorCode = "SUPERSEDED_BY_SERVER_DELETION",
                )
            } else {
                operation
            }
        }
    }

    override suspend fun resolveVersionConflict(
        operationId: String,
        taskId: String,
        serverVersion: Long,
    ) {
        backing.operations.computeIfPresent(operationId) { _, operation ->
            operation.copy(
                state = OperationState.RESOLVED_CONFLICT,
                acknowledgedServerVersion = serverVersion,
                leaseUntilMillis = null,
                lastErrorCode = "VERSION_CONFLICT",
            )
        }
        backing.tasks.computeIfPresent(taskId) { _, task ->
            task.copy(serverVersion = serverVersion)
        }
    }

    override suspend fun operation(operationId: String): SyncOperation? =
        backing.operations[operationId]

    override suspend fun task(taskId: String): LocalTask? = backing.tasks[taskId]

    override suspend fun allOperations(): List<SyncOperation> =
        backing.operations.values.sortedBy(SyncOperation::queueSequence)
}

internal class MutableSyncClock(
    private var currentMillis: Long,
) : SyncClock {
    override fun nowMillis(): Long = currentMillis

    fun advanceBy(millis: Long) {
        currentMillis += millis
    }
}

internal class RecordingSyncApi(
    private val responder: (SyncOperation, Int) -> PushResult,
) : SyncApi {
    private val activeRequests = AtomicInteger(0)
    private val maximumActiveRequests = AtomicInteger(0)
    private val attempts = ConcurrentHashMap<String, AtomicInteger>()
    private val recordedBatches = mutableListOf<List<String>>()

    val maxConcurrentRequests: Int
        get() = maximumActiveRequests.get()

    val batches: List<List<String>>
        get() = synchronized(recordedBatches) { recordedBatches.map { it.toList() } }

    val sendOrder: List<String>
        get() = batches.flatten()

    fun attemptsFor(operationId: String): Int = attempts[operationId]?.get() ?: 0

    override suspend fun push(operations: List<SyncOperation>): List<PushResult> {
        val active = activeRequests.incrementAndGet()
        maximumActiveRequests.accumulateAndGet(active, ::maxOf)
        synchronized(recordedBatches) {
            recordedBatches += operations.map(SyncOperation::operationId)
        }
        return try {
            yield()
            operations.map { operation ->
                val attempt = attempts
                    .computeIfAbsent(operation.operationId) { AtomicInteger(0) }
                    .incrementAndGet()
                responder(operation, attempt)
            }
        } finally {
            activeRequests.decrementAndGet()
        }
    }
}

internal fun pendingOperation(
    operationId: String,
    taskId: String,
    queueSequence: Long,
    type: OperationType,
    predecessorOperationId: String? = null,
    desiredStatus: TaskStatus? = null,
    baseServerVersion: Long? = null,
): SyncOperation = SyncOperation(
    operationId = operationId,
    taskId = taskId,
    queueSequence = queueSequence,
    taskRevision = 1,
    predecessorOperationId = predecessorOperationId,
    type = type,
    title = if (type == OperationType.CREATE) "Task $taskId" else null,
    desiredStatus = desiredStatus,
    baseServerVersion = baseServerVersion,
)
