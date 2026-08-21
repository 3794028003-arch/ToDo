package com.example.localfirst.backend.sync

internal class InMemoryIdempotencyExecutor : IdempotencyExecutor {
    private val lock = Any()
    private val receipts = mutableMapOf<String, BackendOperationResult>()

    override fun executeOnce(
        operationId: String,
        requestHash: String,
        action: () -> BackendOperationResult,
    ): BackendOperationResult = synchronized(lock) {
        receipts[operationId] ?: action().also { result -> receipts[operationId] = result }
    }
}

internal class InMemoryServerTaskStore : ServerTaskStore {
    private val tasks = linkedMapOf<String, ServerTask>()
    private val mutationCounts = mutableMapOf<String, Int>()

    fun seed(task: ServerTask) {
        tasks[task.id] = task
    }

    override fun find(taskId: String): ServerTask? = tasks[taskId]

    override fun create(taskId: String, title: String): ServerTask {
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return ServerTask(
            id = taskId,
            title = title,
            status = ServerTaskStatus.TODO,
            version = 1,
        ).also { tasks[taskId] = it }
    }

    override fun changeStatus(
        taskId: String,
        status: ServerTaskStatus,
        expectedVersion: Long,
    ): ServerTask? {
        val current = requireNotNull(tasks[taskId])
        if (current.version != expectedVersion || current.deletedAtMillis != null) return null
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return current.copy(
            status = status,
            version = current.version + 1,
        ).also { tasks[taskId] = it }
    }

    override fun updateTitle(
        taskId: String,
        title: String,
        expectedVersion: Long,
    ): ServerTask? {
        val current = requireNotNull(tasks[taskId])
        if (current.version != expectedVersion || current.deletedAtMillis != null) return null
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return current.copy(
            title = title,
            version = current.version + 1,
        ).also { tasks[taskId] = it }
    }

    override fun delete(
        taskId: String,
        expectedVersion: Long,
        deletedAtMillis: Long,
    ): ServerTask? {
        val current = requireNotNull(tasks[taskId])
        if (current.version != expectedVersion || current.deletedAtMillis != null) return null
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return current.copy(
            version = current.version + 1,
            deletedAtMillis = deletedAtMillis,
        ).also { tasks[taskId] = it }
    }

    fun task(taskId: String): ServerTask? = tasks[taskId]

    fun mutationCount(taskId: String): Int = mutationCounts[taskId] ?: 0
}
