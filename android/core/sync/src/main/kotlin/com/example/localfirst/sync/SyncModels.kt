package com.example.localfirst.sync

enum class TaskStatus {
    TODO,
    DOING,
    DONE,
}

data class LocalTask(
    val id: String,
    val status: TaskStatus,
    val serverVersion: Long? = null,
    val deletedAtMillis: Long? = null,
)

enum class OperationType {
    CREATE,
    UPDATE,
    CHANGE_STATUS,
    DELETE,
}

enum class OperationState {
    PENDING,
    IN_FLIGHT,
    RETRY_WAIT,
    SYNCED,
    FAILED_PERMANENT,
    RESOLVED_CONFLICT,
    SUPERSEDED,
    ;

    val isTerminal: Boolean
        get() = this == SYNCED ||
            this == FAILED_PERMANENT ||
            this == RESOLVED_CONFLICT ||
            this == SUPERSEDED
}

data class SyncOperation(
    val operationId: String,
    val taskId: String,
    val queueSequence: Long,
    val taskRevision: Long,
    val predecessorOperationId: String? = null,
    val type: OperationType,
    val title: String? = null,
    val desiredStatus: TaskStatus? = null,
    val baseServerVersion: Long? = null,
    val state: OperationState = OperationState.PENDING,
    val attemptCount: Int = 0,
    val nextAttemptAtMillis: Long? = null,
    val leaseUntilMillis: Long? = null,
    val acknowledgedServerVersion: Long? = null,
    val lastErrorCode: String? = null,
)

sealed interface PushResult {
    val operationId: String

    data class Applied(
        override val operationId: String,
        val serverVersion: Long,
    ) : PushResult

    data class Retryable(
        override val operationId: String,
        val errorCode: String,
    ) : PushResult

    data class ServerDeleted(
        override val operationId: String,
        val deletedAtMillis: Long,
        val tombstoneVersion: Long,
    ) : PushResult

    data class VersionConflict(
        override val operationId: String,
        val serverVersion: Long,
    ) : PushResult
}
