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

    override fun create(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String,
        isPinned: Boolean,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ): ServerTask {
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return ServerTask(
            id = taskId,
            title = title,
            status = ServerTaskStatus.TODO,
            version = 1,
            reminderAtMillis = reminderAtMillis,
            reminderRepeat = reminderRepeat,
            isPinned = isPinned,
            startDateMillis = startDateMillis,
            dueDateMillis = dueDateMillis,
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
            reminderAtMillis = if (status == ServerTaskStatus.DONE) null else current.reminderAtMillis,
            reminderRepeat = if (status == ServerTaskStatus.DONE) "NONE" else current.reminderRepeat,
            version = current.version + 1,
        ).also { tasks[taskId] = it }
    }

    override fun updateDetails(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String?,
        isPinned: Boolean?,
        startDateMillis: Long?,
        dueDateMillis: Long?,
        expectedVersion: Long,
    ): ServerTask? {
        val current = requireNotNull(tasks[taskId])
        if (current.version != expectedVersion || current.deletedAtMillis != null) return null
        mutationCounts[taskId] = mutationCount(taskId) + 1
        return current.copy(
            title = title,
            reminderAtMillis = reminderAtMillis,
            reminderRepeat = reminderRepeat ?: current.reminderRepeat,
            isPinned = isPinned ?: current.isPinned,
            startDateMillis = startDateMillis,
            dueDateMillis = dueDateMillis,
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
            reminderAtMillis = null,
            reminderRepeat = "NONE",
            deletedAtMillis = deletedAtMillis,
        ).also { tasks[taskId] = it }
    }

    fun task(taskId: String): ServerTask? = tasks[taskId]

    fun mutationCount(taskId: String): Int = mutationCounts[taskId] ?: 0
}
