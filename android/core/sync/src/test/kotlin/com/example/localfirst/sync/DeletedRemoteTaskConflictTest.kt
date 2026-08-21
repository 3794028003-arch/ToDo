package com.example.localfirst.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeletedRemoteTaskConflictTest {
    @Test
    fun `server deletion wins and does not block another task`() = runTest {
        val backing = DurableTestBacking()
        val store = ReopenableFakeSyncStore(backing)
        store.seedTask(LocalTask("task-x", TaskStatus.DONE, serverVersion = 7))
        store.seedTask(LocalTask("task-y", TaskStatus.DOING, serverVersion = 2))
        store.seedOperation(
            pendingOperation(
                operationId = "x-status",
                taskId = "task-x",
                queueSequence = 1,
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = 7,
            ),
        )
        store.seedOperation(
            pendingOperation(
                operationId = "x-later-update",
                taskId = "task-x",
                queueSequence = 2,
                type = OperationType.CHANGE_STATUS,
                predecessorOperationId = "x-status",
                desiredStatus = TaskStatus.TODO,
                baseServerVersion = 7,
            ),
        )
        store.seedOperation(
            pendingOperation(
                operationId = "y-status",
                taskId = "task-y",
                queueSequence = 3,
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = 2,
            ),
        )
        val api = RecordingSyncApi { operation, _ ->
            when (operation.operationId) {
                "x-status" -> PushResult.ServerDeleted(
                    operationId = operation.operationId,
                    deletedAtMillis = 5_000,
                    tombstoneVersion = 8,
                )
                else -> PushResult.Applied(operation.operationId, serverVersion = 3)
            }
        }
        val engine = SyncEngine(
            store = store,
            api = api,
            clock = MutableSyncClock(1_000),
            retryPolicy = RetryPolicy { 10_000 },
        )

        engine.drain()

        assertEquals(OperationState.RESOLVED_CONFLICT, store.operation("x-status")?.state)
        assertEquals(OperationState.SUPERSEDED, store.operation("x-later-update")?.state)
        assertEquals(OperationState.SYNCED, store.operation("y-status")?.state)
        assertNotNull(store.task("task-x")?.deletedAtMillis)
        assertEquals(8L, store.task("task-x")?.serverVersion)
        assertEquals(0, api.attemptsFor("x-later-update"))
        assertEquals(1, api.attemptsFor("x-status"))
    }

    @Test
    fun `version conflict becomes terminal and does not block another task`() = runTest {
        val backing = DurableTestBacking()
        val store = ReopenableFakeSyncStore(backing)
        store.seedTask(LocalTask("task-stale", TaskStatus.DONE, serverVersion = 5))
        store.seedTask(LocalTask("task-next", TaskStatus.TODO, serverVersion = 2))
        store.seedOperation(
            pendingOperation(
                operationId = "stale-status",
                taskId = "task-stale",
                queueSequence = 1,
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = 5,
            ),
        )
        store.seedOperation(
            pendingOperation(
                operationId = "next-status",
                taskId = "task-next",
                queueSequence = 2,
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = 2,
            ),
        )
        val api = RecordingSyncApi { operation, _ ->
            when (operation.operationId) {
                "stale-status" -> PushResult.VersionConflict(
                    operationId = operation.operationId,
                    serverVersion = 6,
                )

                else -> PushResult.Applied(operation.operationId, serverVersion = 3)
            }
        }
        val engine = SyncEngine(
            store = store,
            api = api,
            clock = MutableSyncClock(1_000),
            retryPolicy = RetryPolicy { 10_000 },
        )

        engine.drain()

        val conflict = store.operation("stale-status")
        assertEquals(OperationState.RESOLVED_CONFLICT, conflict?.state)
        assertEquals("VERSION_CONFLICT", conflict?.lastErrorCode)
        assertEquals(6L, conflict?.acknowledgedServerVersion)
        assertEquals(6L, store.task("task-stale")?.serverVersion)
        assertEquals(OperationState.SYNCED, store.operation("next-status")?.state)
        assertEquals(1, api.attemptsFor("stale-status"))
        assertEquals(1, api.attemptsFor("next-status"))
    }
}
