package com.example.localfirst.backend.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatSyncTest {
    private val tasks = InMemoryServerTaskStore()
    private val processor = SyncBatchProcessor(InMemoryIdempotencyExecutor(), tasks)

    @Test
    fun `create and update persist reminder repeat type`() {
        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "create-repeat",
                    requestHash = "create-repeat-hash",
                    taskId = "repeat-task",
                    type = ServerOperationType.CREATE,
                    title = "Stand up",
                    reminderAtMillis = 1_900_000_000_000,
                    reminderRepeat = "DAILY",
                ),
            ),
        )
        assertEquals("DAILY", tasks.task("repeat-task")?.reminderRepeat)

        processor.process(
            listOf(
                IncomingSyncOperation(
                    operationId = "update-repeat",
                    requestHash = "update-repeat-hash",
                    taskId = "repeat-task",
                    type = ServerOperationType.UPDATE,
                    title = "Stand up on weekdays",
                    reminderAtMillis = 1_900_100_000_000,
                    reminderRepeat = "WEEKDAYS",
                    baseServerVersion = 1,
                ),
            ),
        )
        assertEquals("WEEKDAYS", tasks.task("repeat-task")?.reminderRepeat)
    }
}
