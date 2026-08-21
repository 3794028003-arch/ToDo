package com.example.localfirst.sync

interface SyncStore {
    suspend fun recoverExpiredLeases(nowMillis: Long)

    suspend fun dueOperations(nowMillis: Long): List<SyncOperation>

    suspend fun markInFlight(operationIds: Set<String>, leaseUntilMillis: Long)

    suspend fun markSynced(operationId: String, serverVersion: Long)

    suspend fun markRetry(
        operationId: String,
        errorCode: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
    )

    suspend fun resolveServerDeleted(
        operationId: String,
        taskId: String,
        deletedAtMillis: Long,
        tombstoneVersion: Long,
    )

    suspend fun resolveVersionConflict(
        operationId: String,
        taskId: String,
        serverVersion: Long,
    )

    suspend fun operation(operationId: String): SyncOperation?

    suspend fun task(taskId: String): LocalTask?

    suspend fun allOperations(): List<SyncOperation>
}

interface SyncApi {
    suspend fun push(operations: List<SyncOperation>): List<PushResult>
}

fun interface SyncClock {
    fun nowMillis(): Long
}

fun interface RetryPolicy {
    fun delayMillis(attemptCount: Int): Long
}
