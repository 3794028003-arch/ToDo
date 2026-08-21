package com.example.localfirst.network

import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.PushResult
import com.example.localfirst.sync.SyncOperation
import com.example.localfirst.sync.TaskStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class SyncBatchRequest(
    val operations: List<SyncOperationRequest>,
)

internal data class SyncOperationRequest(
    val operationId: String,
    val requestHash: String,
    val taskId: String,
    val type: OperationType,
    val title: String?,
    val desiredStatus: TaskStatus?,
    val baseServerVersion: Long?,
)

internal data class SyncBatchResponse(
    val results: List<SyncOperationResponse>,
)

internal data class SyncOperationResponse(
    val operationId: String,
    val status: String,
    val serverVersion: Long? = null,
    val tombstoneVersion: Long? = null,
    val deletedAtMillis: Long? = null,
)

internal fun SyncOperation.toRequest(): SyncOperationRequest = SyncOperationRequest(
    operationId = operationId,
    requestHash = requestHash(),
    taskId = taskId,
    type = type,
    title = title,
    desiredStatus = desiredStatus,
    baseServerVersion = baseServerVersion,
)

internal fun SyncOperationResponse.toDomain(): PushResult = when (status) {
    "APPLIED" -> PushResult.Applied(
        operationId = operationId,
        serverVersion = requireNotNull(serverVersion),
    )

    "TASK_DELETED" -> PushResult.ServerDeleted(
        operationId = operationId,
        deletedAtMillis = requireNotNull(deletedAtMillis),
        tombstoneVersion = requireNotNull(tombstoneVersion),
    )

    "VERSION_CONFLICT" -> PushResult.VersionConflict(
        operationId = operationId,
        serverVersion = requireNotNull(serverVersion),
    )

    else -> error("Unsupported sync response status: $status")
}

private fun SyncOperation.requestHash(): String {
    val canonicalFields = listOf(
        "v1",
        operationId,
        taskId,
        type.name,
        title,
        desiredStatus?.name,
        baseServerVersion?.toString(),
    ).joinToString(separator = "") { value ->
        value?.let { "${it.length}:$it" } ?: "-1:"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalFields.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
