package com.example.localfirst.backend.sync

enum class ServerTaskStatus {
    TODO,
    DOING,
    DONE,
}

data class ServerTask(
    val id: String,
    val title: String,
    val status: ServerTaskStatus,
    val version: Long,
    val deletedAtMillis: Long? = null,
)

enum class ServerOperationType {
    CREATE,
    UPDATE,
    CHANGE_STATUS,
    DELETE,
}

data class IncomingSyncOperation(
    val operationId: String,
    val requestHash: String,
    val taskId: String,
    val type: ServerOperationType,
    val title: String? = null,
    val desiredStatus: ServerTaskStatus? = null,
    val baseServerVersion: Long? = null,
)

sealed interface BackendOperationResult {
    val operationId: String

    data class Applied(
        override val operationId: String,
        val serverVersion: Long,
    ) : BackendOperationResult

    data class TaskDeleted(
        override val operationId: String,
        val tombstoneVersion: Long,
        val deletedAtMillis: Long,
    ) : BackendOperationResult

    data class VersionConflict(
        override val operationId: String,
        val serverVersion: Long,
    ) : BackendOperationResult
}
