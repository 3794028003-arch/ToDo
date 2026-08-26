package com.example.localfirst.backend.web

import com.example.localfirst.backend.sync.BackendOperationResult
import com.example.localfirst.backend.sync.IncomingSyncOperation
import com.example.localfirst.backend.sync.ServerOperationType
import com.example.localfirst.backend.sync.ServerTaskStatus

data class SyncBatchRequest(
    val operations: List<SyncOperationRequest>,
)

data class SyncOperationRequest(
    val operationId: String,
    val requestHash: String,
    val taskId: String,
    val type: ServerOperationType,
    val title: String? = null,
    val reminderAtMillis: Long? = null,
    val reminderRepeat: String? = null,
    val isPinned: Boolean? = null,
    val startDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val desiredStatus: ServerTaskStatus? = null,
    val baseServerVersion: Long? = null,
) {
    fun toDomain(): IncomingSyncOperation = IncomingSyncOperation(
        operationId = operationId,
        requestHash = requestHash,
        taskId = taskId,
        type = type,
        title = title,
        reminderAtMillis = reminderAtMillis,
        reminderRepeat = reminderRepeat,
        isPinned = isPinned,
        startDateMillis = startDateMillis,
        dueDateMillis = dueDateMillis,
        desiredStatus = desiredStatus,
        baseServerVersion = baseServerVersion,
    )
}

data class SyncBatchResponse(
    val results: List<SyncOperationResponse>,
)

data class SyncOperationResponse(
    val operationId: String,
    val status: String,
    val serverVersion: Long? = null,
    val tombstoneVersion: Long? = null,
    val deletedAtMillis: Long? = null,
)

fun BackendOperationResult.toResponse(): SyncOperationResponse = when (this) {
    is BackendOperationResult.Applied -> SyncOperationResponse(
        operationId = operationId,
        status = "APPLIED",
        serverVersion = serverVersion,
    )

    is BackendOperationResult.TaskDeleted -> SyncOperationResponse(
        operationId = operationId,
        status = "TASK_DELETED",
        tombstoneVersion = tombstoneVersion,
        deletedAtMillis = deletedAtMillis,
    )

    is BackendOperationResult.VersionConflict -> SyncOperationResponse(
        operationId = operationId,
        status = "VERSION_CONFLICT",
        serverVersion = serverVersion,
    )
}
