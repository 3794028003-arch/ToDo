package com.example.localfirst.backend.sync

interface IdempotencyExecutor {
    fun executeOnce(
        operationId: String,
        requestHash: String,
        action: () -> BackendOperationResult,
    ): BackendOperationResult
}

interface ServerTaskStore {
    fun find(taskId: String): ServerTask?

    fun create(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String,
        isPinned: Boolean,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
    ): ServerTask

    fun changeStatus(
        taskId: String,
        status: ServerTaskStatus,
        expectedVersion: Long,
    ): ServerTask?

    fun updateDetails(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String?,
        isPinned: Boolean?,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
        expectedVersion: Long,
    ): ServerTask?

    fun delete(
        taskId: String,
        expectedVersion: Long,
        deletedAtMillis: Long,
    ): ServerTask?
}
