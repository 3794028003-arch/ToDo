package com.example.localfirst.sync

class SyncEngine(
    private val store: SyncStore,
    private val api: SyncApi,
    private val clock: SyncClock,
    private val retryPolicy: RetryPolicy,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val leaseMillis: Long = DEFAULT_LEASE_MILLIS,
) {
    suspend fun drain() {
        store.recoverExpiredLeases(clock.nowMillis())

        while (true) {
            val batch = selectBatch()
            if (batch.isEmpty()) return

            claim(batch)
            val results = push(batch)
            batch.forEach { operation ->
                applyResult(operation, results[operation.operationId])
            }
        }
    }

    private suspend fun selectBatch(): List<SyncOperation> =
        store.dueOperations(clock.nowMillis())
            .distinctBy(SyncOperation::taskId)
            .take(batchSize)

    private suspend fun claim(batch: List<SyncOperation>) {
        store.markInFlight(
            operationIds = batch.mapTo(mutableSetOf(), SyncOperation::operationId),
            leaseUntilMillis = clock.nowMillis() + leaseMillis,
        )
    }

    private suspend fun push(
        batch: List<SyncOperation>,
    ): Map<String, PushResult> = try {
        api.push(batch).associateBy(PushResult::operationId)
    } catch (_: Exception) {
        emptyMap()
    }

    private suspend fun applyResult(
        operation: SyncOperation,
        result: PushResult?,
    ) {
        when (result) {
            is PushResult.Applied -> store.markSynced(
                operationId = operation.operationId,
                serverVersion = result.serverVersion,
            )

            is PushResult.ServerDeleted -> store.resolveServerDeleted(
                operationId = operation.operationId,
                taskId = operation.taskId,
                deletedAtMillis = result.deletedAtMillis,
                tombstoneVersion = result.tombstoneVersion,
            )

            is PushResult.VersionConflict -> store.resolveVersionConflict(
                operationId = operation.operationId,
                taskId = operation.taskId,
                serverVersion = result.serverVersion,
            )

            is PushResult.Retryable -> scheduleRetry(operation, result.errorCode)
            null -> scheduleRetry(operation, TRANSPORT_FAILURE_CODE)
        }
    }

    private suspend fun scheduleRetry(operation: SyncOperation, errorCode: String) {
        val nextAttempt = operation.attemptCount + 1
        store.markRetry(
            operationId = operation.operationId,
            errorCode = errorCode,
            attemptCount = nextAttempt,
            nextAttemptAtMillis = clock.nowMillis() + retryPolicy.delayMillis(nextAttempt),
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 10
        const val DEFAULT_LEASE_MILLIS = 60_000L

        private const val TRANSPORT_FAILURE_CODE = "BATCH_TRANSPORT_FAILURE"
    }
}
