package com.example.localfirst.backend.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedSyncTest {
    private val tasks = InMemoryServerTaskStore()
    private val processor = SyncBatchProcessor(InMemoryIdempotencyExecutor(), tasks)

    @Test
    fun `new task is not pinned unless requested`() {
        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "create-pin-1",
                    requestHash = "hash-create-pin-1",
                    taskId = "task-pin-1",
                    type = ServerOperationType.CREATE,
                    title = "ordinary task",
                ),
            ),
        )

        assertFalse(tasks.task("task-pin-1")!!.isPinned)
    }

    @Test
    fun `update can pin a task and status changes preserve pin`() {
        tasks.seed(
            ServerTask(
                id = "task-pin-2",
                title = "important task",
                status = ServerTaskStatus.TODO,
                version = 1,
            ),
        )

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "pin-2",
                    requestHash = "hash-pin-2",
                    taskId = "task-pin-2",
                    type = ServerOperationType.UPDATE,
                    title = "important task",
                    isPinned = true,
                    baseServerVersion = 1,
                ),
                IncomingSyncOperation(
                    operationId = "move-pin-2",
                    requestHash = "hash-move-pin-2",
                    taskId = "task-pin-2",
                    type = ServerOperationType.CHANGE_STATUS,
                    desiredStatus = ServerTaskStatus.DOING,
                    baseServerVersion = 2,
                ),
            ),
        )

        assertTrue(tasks.task("task-pin-2")!!.isPinned)
    }

    @Test
    fun `legacy update without pin field preserves current pin`() {
        tasks.seed(
            ServerTask(
                id = "task-pin-3",
                title = "old title",
                status = ServerTaskStatus.TODO,
                version = 1,
                isPinned = true,
            ),
        )

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "legacy-update-pin-3",
                    requestHash = "hash-legacy-update-pin-3",
                    taskId = "task-pin-3",
                    type = ServerOperationType.UPDATE,
                    title = "new title",
                    baseServerVersion = 1,
                ),
            ),
        )

        assertTrue(tasks.task("task-pin-3")!!.isPinned)
    }
}
