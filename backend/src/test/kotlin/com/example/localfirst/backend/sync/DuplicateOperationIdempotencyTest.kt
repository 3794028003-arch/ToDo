package com.example.localfirst.backend.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateOperationIdempotencyTest {
    @Test
    fun `concurrent duplicate operation is applied exactly once`() = runBlocking {
        val tasks = InMemoryServerTaskStore()
        val processor = SyncBatchProcessor(
            idempotency = InMemoryIdempotencyExecutor(),
            tasks = tasks,
        )
        val operation = IncomingSyncOperation(
            operationId = "operation-1",
            requestHash = "hash-1",
            taskId = "task-1",
            type = ServerOperationType.CREATE,
            title = "Offline task",
        )

        val results = listOf(
            async(Dispatchers.Default) { processor.process(listOf(operation)).single() },
            async(Dispatchers.Default) { processor.process(listOf(operation)).single() },
        ).awaitAll()

        assertEquals(results[0], results[1])
        assertTrue(results.all { it is BackendOperationResult.Applied })
        assertEquals(1L, tasks.task("task-1")?.version)
        assertEquals(1, tasks.mutationCount("task-1"))
    }
}
