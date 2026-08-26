package com.example.localfirst.backend.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeSyncTest {
    private val tasks = InMemoryServerTaskStore()
    private val processor = SyncBatchProcessor(InMemoryIdempotencyExecutor(), tasks)

    @Test
    fun `create and update persist optional start and due dates`() {
        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "create-dates",
                    requestHash = "create-dates-hash",
                    taskId = "dated-task",
                    type = ServerOperationType.CREATE,
                    title = "Prepare launch",
                    startDateMillis = 1_800_000_000_000,
                    dueDateMillis = 1_800_086_400_000,
                ),
            ),
        )
        assertEquals(1_800_000_000_000, tasks.task("dated-task")?.startDateMillis)
        assertEquals(1_800_086_400_000, tasks.task("dated-task")?.dueDateMillis)

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "update-dates",
                    requestHash = "update-dates-hash",
                    taskId = "dated-task",
                    type = ServerOperationType.UPDATE,
                    title = "Prepare launch",
                    startDateMillis = null,
                    dueDateMillis = 1_800_172_800_000,
                    baseServerVersion = 1,
                ),
            ),
        )
        assertEquals(null, tasks.task("dated-task")?.startDateMillis)
        assertEquals(1_800_172_800_000, tasks.task("dated-task")?.dueDateMillis)
    }
}
