package com.example.localfirst.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentRecoverySyncTest {
    @Test
    fun `network recovery drains 30 durable operations with bounded requests and backoff`() = runTest {
        val backing = DurableTestBacking()
        val initialStore = ReopenableFakeSyncStore(backing)

        (1..20).forEach { index ->
            val taskId = "new-task-$index"
            initialStore.seedTask(LocalTask(taskId, TaskStatus.TODO))
            initialStore.seedOperation(
                pendingOperation(
                    operationId = "create-$index",
                    taskId = taskId,
                    queueSequence = index.toLong(),
                    type = OperationType.CREATE,
                ),
            )
        }
        (1..10).forEach { index ->
            val taskId = "existing-task-$index"
            initialStore.seedTask(LocalTask(taskId, TaskStatus.DONE, serverVersion = 4))
            initialStore.seedOperation(
                pendingOperation(
                    operationId = "status-$index",
                    taskId = taskId,
                    queueSequence = (20 + index).toLong(),
                    type = OperationType.CHANGE_STATUS,
                    desiredStatus = TaskStatus.DONE,
                    baseServerVersion = 4,
                ),
            )
        }

        // A new store instance represents reopening the durable queue after process death.
        val reopenedStore = ReopenableFakeSyncStore(backing)
        assertEquals(30, reopenedStore.allOperations().size)

        val clock = MutableSyncClock(currentMillis = 1_000)
        val api = RecordingSyncApi { operation, attempt ->
            if (operation.operationId == "create-7" && attempt == 1) {
                PushResult.Retryable(operation.operationId, "HTTP_503")
            } else {
                PushResult.Applied(operation.operationId, serverVersion = 5)
            }
        }
        val engine = SyncEngine(
            store = reopenedStore,
            api = api,
            clock = clock,
            retryPolicy = RetryPolicy { 10_000 },
            batchSize = 10,
            leaseMillis = 60_000,
        )

        engine.drain()

        assertEquals(29, reopenedStore.allOperations().count { it.state == OperationState.SYNCED })
        assertEquals(OperationState.RETRY_WAIT, reopenedStore.operation("create-7")?.state)
        assertEquals(1, api.maxConcurrentRequests)
        assertTrue(api.batches.all { it.size <= 10 })
        assertEquals(1, api.attemptsFor("create-7"))

        engine.drain()
        assertEquals("retry must not spin before its due time", 1, api.attemptsFor("create-7"))

        clock.advanceBy(10_000)
        engine.drain()

        assertEquals(30, reopenedStore.allOperations().count { it.state == OperationState.SYNCED })
        assertEquals(2, api.attemptsFor("create-7"))
    }

    @Test
    fun `operations for one task preserve causal order`() = runTest {
        val backing = DurableTestBacking()
        val store = ReopenableFakeSyncStore(backing)
        store.seedTask(LocalTask("task-a", TaskStatus.DONE))
        store.seedOperation(pendingOperation("a-create", "task-a", 1, OperationType.CREATE))
        store.seedOperation(
            pendingOperation(
                "a-status",
                "task-a",
                2,
                OperationType.CHANGE_STATUS,
                predecessorOperationId = "a-create",
                desiredStatus = TaskStatus.DONE,
            ),
        )
        store.seedOperation(
            pendingOperation(
                "a-delete",
                "task-a",
                3,
                OperationType.DELETE,
                predecessorOperationId = "a-status",
            ),
        )
        val api = RecordingSyncApi { operation, _ ->
            PushResult.Applied(operation.operationId, operation.queueSequence)
        }
        val engine = SyncEngine(
            store = store,
            api = api,
            clock = MutableSyncClock(0),
            retryPolicy = RetryPolicy { 10_000 },
        )

        engine.drain()

        assertEquals(listOf("a-create", "a-status", "a-delete"), api.sendOrder)
        assertTrue(api.batches.all { it.size == 1 })
    }
}
