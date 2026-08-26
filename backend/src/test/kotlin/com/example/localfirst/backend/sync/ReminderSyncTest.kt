package com.example.localfirst.backend.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSyncTest {
    private val tasks = InMemoryServerTaskStore()
    private val processor = SyncBatchProcessor(
        idempotency = InMemoryIdempotencyExecutor(),
        tasks = tasks,
    )

    @Test
    fun `create stores optional reminder time`() {
        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "create-with-reminder",
                    requestHash = "hash-create",
                    taskId = "task-1",
                    type = ServerOperationType.CREATE,
                    title = "Take medicine",
                    reminderAtMillis = 1_900_000_000_000,
                ),
            ),
        )

        assertEquals(1_900_000_000_000, tasks.task("task-1")?.reminderAtMillis)
    }

    @Test
    fun `update replaces and can clear reminder time`() {
        tasks.seed(
            ServerTask(
                id = "task-2",
                title = "Original",
                status = ServerTaskStatus.TODO,
                version = 1,
                reminderAtMillis = 1_900_000_000_000,
            ),
        )

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "clear-reminder",
                    requestHash = "hash-clear",
                    taskId = "task-2",
                    type = ServerOperationType.UPDATE,
                    title = "Updated",
                    reminderAtMillis = null,
                    baseServerVersion = 1,
                ),
            ),
        )

        val updated = tasks.task("task-2")
        assertEquals("Updated", updated?.title)
        assertNull(updated?.reminderAtMillis)
    }

    @Test
    fun `moving task to done clears reminder time`() {
        tasks.seed(
            ServerTask(
                id = "task-3",
                title = "Finish report",
                status = ServerTaskStatus.DOING,
                version = 4,
                reminderAtMillis = 1_900_000_000_000,
            ),
        )

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "complete-task",
                    requestHash = "hash-complete",
                    taskId = "task-3",
                    type = ServerOperationType.CHANGE_STATUS,
                    desiredStatus = ServerTaskStatus.DONE,
                    baseServerVersion = 4,
                ),
            ),
        )

        assertEquals(ServerTaskStatus.DONE, tasks.task("task-3")?.status)
        assertNull(tasks.task("task-3")?.reminderAtMillis)
    }
}
