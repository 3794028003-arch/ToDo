package com.example.localfirst.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.localfirst.sync.OperationState

@Dao
interface SyncOperationDao {
    @Insert
    suspend fun insert(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations ORDER BY queueSequence")
    suspend fun all(): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun findById(operationId: String): SyncOperationEntity?

    @Query("SELECT * FROM sync_operations WHERE taskId = :taskId ORDER BY queueSequence DESC LIMIT 1")
    suspend fun latestForTask(taskId: String): SyncOperationEntity?

    @Query("DELETE FROM sync_operations WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Query("SELECT COALESCE(MAX(queueSequence), 0) + 1 FROM sync_operations")
    suspend fun nextQueueSequence(): Long

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE taskId = :taskId AND queueSequence < :beforeSequence
        ORDER BY queueSequence DESC
        LIMIT 1
        """,
    )
    suspend fun latestBefore(taskId: String, beforeSequence: Long): SyncOperationEntity?

    @Query(
        """
        UPDATE sync_operations
        SET state = :pendingState, leaseUntilMillis = NULL
        WHERE state = :inFlightState
          AND leaseUntilMillis IS NOT NULL
          AND leaseUntilMillis <= :nowMillis
        """,
    )
    suspend fun recoverExpiredLeases(
        nowMillis: Long,
        inFlightState: OperationState,
        pendingState: OperationState,
    ): Int

    @Query(
        """
        SELECT candidate.*
        FROM sync_operations AS candidate
        LEFT JOIN sync_operations AS predecessor
          ON predecessor.operationId = candidate.predecessorOperationId
        WHERE (
            candidate.state = :pendingState
            OR (
                candidate.state = :retryState
                AND candidate.nextAttemptAtMillis IS NOT NULL
                AND candidate.nextAttemptAtMillis <= :nowMillis
            )
        )
        AND (
            candidate.predecessorOperationId IS NULL
            OR predecessor.state IN (:terminalStates)
        )
        ORDER BY candidate.queueSequence
        """,
    )
    suspend fun dueOperations(
        nowMillis: Long,
        pendingState: OperationState,
        retryState: OperationState,
        terminalStates: List<OperationState>,
    ): List<SyncOperationEntity>

    @Query(
        """
        UPDATE sync_operations
        SET state = :inFlightState, leaseUntilMillis = :leaseUntilMillis
        WHERE operationId IN (:operationIds)
        """,
    )
    suspend fun markInFlight(
        operationIds: Set<String>,
        leaseUntilMillis: Long,
        inFlightState: OperationState,
    )

    @Query(
        """
        UPDATE sync_operations
        SET state = :syncedState,
            acknowledgedServerVersion = :serverVersion,
            leaseUntilMillis = NULL,
            lastErrorCode = NULL
        WHERE operationId = :operationId
        """,
    )
    suspend fun markSynced(
        operationId: String,
        serverVersion: Long,
        syncedState: OperationState,
    )

    @Query(
        """
        UPDATE sync_operations
        SET state = :retryState,
            attemptCount = :attemptCount,
            nextAttemptAtMillis = :nextAttemptAtMillis,
            leaseUntilMillis = NULL,
            lastErrorCode = :errorCode
        WHERE operationId = :operationId
        """,
    )
    suspend fun markRetry(
        operationId: String,
        errorCode: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
        retryState: OperationState,
    )

    @Query(
        """
        UPDATE sync_operations
        SET state = :resolvedState,
            acknowledgedServerVersion = :serverVersion,
            leaseUntilMillis = NULL,
            lastErrorCode = :errorCode
        WHERE operationId = :operationId
        """,
    )
    suspend fun markResolvedConflict(
        operationId: String,
        serverVersion: Long,
        errorCode: String,
        resolvedState: OperationState,
    )

    @Query(
        """
        UPDATE sync_operations
        SET state = :supersededState,
            leaseUntilMillis = NULL,
            lastErrorCode = :errorCode
        WHERE taskId = :taskId
          AND queueSequence > :afterSequence
          AND state NOT IN (:terminalStates)
        """,
    )
    suspend fun supersedeLaterOperations(
        taskId: String,
        afterSequence: Long,
        terminalStates: List<OperationState>,
        supersededState: OperationState,
        errorCode: String,
    )

    @Query("SELECT COUNT(*) FROM sync_operations WHERE state NOT IN (:terminalStates)")
    suspend fun countNonTerminal(terminalStates: List<OperationState>): Int
}
