package com.example.localfirst.database

import com.example.localfirst.sync.LocalTask
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.SyncOperation

internal val TERMINAL_OPERATION_STATES: List<OperationState> =
    OperationState.entries.filter(OperationState::isTerminal)

internal const val SERVER_DELETED_ERROR_CODE = "SERVER_DELETED"
internal const val VERSION_CONFLICT_ERROR_CODE = "VERSION_CONFLICT"

internal fun SyncOperationEntity.toDomain(): SyncOperation = SyncOperation(
    operationId = operationId,
    taskId = taskId,
    queueSequence = queueSequence,
    taskRevision = taskRevision,
    predecessorOperationId = predecessorOperationId,
    type = type,
    title = title,
    reminderAtMillis = reminderAtMillis,
    reminderRepeat = reminderRepeat,
    isPinned = isPinned,
    startDateMillis = startDateMillis,
    dueDateMillis = dueDateMillis,
    desiredStatus = desiredStatus,
    baseServerVersion = baseServerVersion,
    state = state,
    attemptCount = attemptCount,
    nextAttemptAtMillis = nextAttemptAtMillis,
    leaseUntilMillis = leaseUntilMillis,
    acknowledgedServerVersion = acknowledgedServerVersion,
    lastErrorCode = lastErrorCode,
)

internal fun TaskEntity.toDomain(): LocalTask = LocalTask(
    id = id,
    status = status,
    serverVersion = serverVersion,
    deletedAtMillis = deletedAtMillis,
)
