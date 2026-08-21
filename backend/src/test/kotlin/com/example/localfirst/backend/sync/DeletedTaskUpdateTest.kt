package com.example.localfirst.backend.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeletedTaskUpdateTest {
    @Test
    fun `update to tombstone is terminal idempotent and next task continues`() {
        val tasks = InMemoryServerTaskStore().apply {
            seed(
                ServerTask(
                    id = "task-x",
                    title = "Deleted task",
                    status = ServerTaskStatus.TODO,
                    version = 8,
                    deletedAtMillis = 5_000,
                ),
            )
            seed(
                ServerTask(
                    id = "task-y",
                    title = "Active task",
                    status = ServerTaskStatus.DOING,
                    version = 2,
                ),
            )
        }
        val processor = SyncBatchProcessor(
            idempotency = InMemoryIdempotencyExecutor(),
            tasks = tasks,
        )
        val deletedTaskUpdate = IncomingSyncOperation(
            operationId = "x-status",
            requestHash = "hash-x",
            taskId = "task-x",
            type = ServerOperationType.CHANGE_STATUS,
            desiredStatus = ServerTaskStatus.DONE,
            baseServerVersion = 7,
        )
        val nextTaskUpdate = IncomingSyncOperation(
            operationId = "y-status",
            requestHash = "hash-y",
            taskId = "task-y",
            type = ServerOperationType.CHANGE_STATUS,
            desiredStatus = ServerTaskStatus.DONE,
            baseServerVersion = 2,
        )

        val results = processor.process(listOf(deletedTaskUpdate, nextTaskUpdate))
        val replay = processor.process(listOf(deletedTaskUpdate)).single()

        assertEquals(BackendOperationResult.TaskDeleted("x-status", 8, 5_000), results[0])
        assertEquals(results[0], replay)
        assertTrueApplied(results[1], expectedVersion = 3)
        assertNotNull(tasks.task("task-x")?.deletedAtMillis)
        assertEquals(ServerTaskStatus.TODO, tasks.task("task-x")?.status)
        assertEquals(0, tasks.mutationCount("task-x"))
        assertEquals(ServerTaskStatus.DONE, tasks.task("task-y")?.status)
        assertEquals(1, tasks.mutationCount("task-y"))
    }

    private fun assertTrueApplied(result: BackendOperationResult, expectedVersion: Long) {
        require(result is BackendOperationResult.Applied)
        assertEquals(expectedVersion, result.serverVersion)
    }
}
