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

    fun create(taskId: String, title: String): ServerTask

    fun changeStatus(
        taskId: String,
        status: ServerTaskStatus,
        expectedVersion: Long,
    ): ServerTask?

    fun updateTitle(
        taskId: String,
        title: String,
        expectedVersion: Long,
    ): ServerTask?

    fun delete(
        taskId: String,
        expectedVersion: Long,
        deletedAtMillis: Long,
    ): ServerTask?
}
