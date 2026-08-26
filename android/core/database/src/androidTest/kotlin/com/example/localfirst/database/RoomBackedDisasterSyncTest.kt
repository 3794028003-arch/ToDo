package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.PushResult
import com.example.localfirst.sync.RetryPolicy
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackedDisasterSyncTest {
    private lateinit var fixture: RoomDatabaseTestFixture

    @Before
    fun setUp() {
        fixture = RoomDatabaseTestFixture()
        fixture.deleteDatabase()
    }

    @After
    fun tearDown() {
        fixture.deleteDatabase()
    }

    @Test
    fun thirtyDurableOperationsDrainInBoundedBatchesWithBackoff() = runTest {
        var database = fixture.open()
        var mutations = RoomTaskMutationStore(database)

        repeat(20) { index ->
            mutations.createTaskAndEnqueue(
                taskId = "new-task-$index",
                title = "New task $index",
                operationId = "create-$index",
                queueSequence = index.toLong(),
            )
        }
        repeat(10) { index ->
            val taskId = "existing-task-$index"
            database.taskDao().upsert(taskEntity(taskId, serverVersion = 4))
            mutations.changeStatusAndEnqueue(
                taskId = taskId,
                status = TaskStatus.DONE,
                operationId = "status-$index",
                queueSequence = (20 + index).toLong(),
            )
        }
        database.close()

        // Reopening represents process death before network recovery.
        database = fixture.open()
        mutations = RoomTaskMutationStore(database)
        assertEquals(30, mutations.pendingOperationCount())

        val clock = TestSyncClock(currentMillis = 1_000)
        val api = RecordingRoomSyncApi { operation, attempt ->
            if (operation.operationId == "create-7" && attempt == 1) {
                PushResult.Retryable(operation.operationId, "HTTP_503")
            } else {
                PushResult.Applied(operation.operationId, serverVersion = 5)
            }
        }
        val executor = RoomSyncExecutor(
            database = database,
            api = api,
            clock = clock,
            retryPolicy = RetryPolicy { 10_000 },
            batchSize = 10,
            leaseMillis = 60_000,
        )

        executor.drain()

        val store = RoomSyncStore(database)
        assertEquals(29, store.allOperations().count { it.state == OperationState.SYNCED })
        assertEquals(OperationState.RETRY_WAIT, store.operation("create-7")?.state)
        assertEquals(1, api.maxConcurrentRequests)
        assertTrue(api.batches.all { it.size <= 10 })
        assertEquals(1, api.attemptsFor("create-7"))

        executor.drain()
        assertEquals("retry must not spin before due time", 1, api.attemptsFor("create-7"))

        clock.advanceBy(10_000)
        executor.drain()

        assertEquals(30, store.allOperations().count { it.state == OperationState.SYNCED })
        assertEquals(2, api.attemptsFor("create-7"))
        database.close()
    }

    @Test
    fun serverDeletionWinsWithoutBlockingAnotherRoomOperation() = runTest {
        var database = fixture.open()
        database.taskDao().upsert(taskEntity("task-x", serverVersion = 7))
        database.taskDao().upsert(taskEntity("task-y", TaskStatus.DOING, serverVersion = 2))
        var mutations = RoomTaskMutationStore(database)
        mutations.changeStatusAndEnqueue(
            taskId = "task-x",
            status = TaskStatus.DONE,
            operationId = "x-status",
            queueSequence = 1,
        )
        mutations.changeStatusAndEnqueue(
            taskId = "task-x",
            status = TaskStatus.TODO,
            operationId = "x-later-update",
            queueSequence = 2,
        )
        mutations.changeStatusAndEnqueue(
            taskId = "task-y",
            status = TaskStatus.DONE,
            operationId = "y-status",
            queueSequence = 3,
        )
        database.close()

        database = fixture.open()
        mutations = RoomTaskMutationStore(database)
        assertEquals(3, mutations.pendingOperationCount())

        val api = RecordingRoomSyncApi { operation, _ ->
            when (operation.operationId) {
                "x-status" -> PushResult.ServerDeleted(
                    operationId = operation.operationId,
                    deletedAtMillis = 5_000,
                    tombstoneVersion = 8,
                )

                else -> PushResult.Applied(operation.operationId, serverVersion = 3)
            }
        }
        val executor = RoomSyncExecutor(
            database = database,
            api = api,
            clock = TestSyncClock(currentMillis = 1_000),
            retryPolicy = RetryPolicy { 10_000 },
        )

        executor.drain()

        val store = RoomSyncStore(database)
        assertEquals(OperationState.RESOLVED_CONFLICT, store.operation("x-status")?.state)
        assertEquals(OperationState.SUPERSEDED, store.operation("x-later-update")?.state)
        assertEquals(OperationState.SYNCED, store.operation("y-status")?.state)
        assertNotNull(store.task("task-x")?.deletedAtMillis)
        assertEquals(8L, store.task("task-x")?.serverVersion)
        assertEquals(1L, database.taskDao().findById("task-x")?.serverDeletionNoticeSequence)
        assertEquals(1, api.attemptsFor("x-status"))
        assertEquals(0, api.attemptsFor("x-later-update"))

        val repository = RoomTaskRepository(database, scheduleSync = {})
        val notice = repository.serverDeletionNotices.first().single()
        assertEquals("task-x", notice.taskId)
        assertEquals("Task task-x", notice.title)

        repository.dismissServerDeletionNotice(notice.taskId)
        assertTrue(repository.serverDeletionNotices.first().isEmpty())
        database.close()
    }

    @Test
    fun serverDeletionNoticesFollowQueueSequenceInsteadOfTaskId() = runTest {
        val database = fixture.open()
        val mutations = RoomTaskMutationStore(database)
        val taskIdsInOperationOrder = listOf("task-c", "task-a", "task-b")

        taskIdsInOperationOrder.forEachIndexed { index, taskId ->
            database.taskDao().upsert(taskEntity(taskId, serverVersion = 4))
            mutations.changeStatusAndEnqueue(
                taskId = taskId,
                status = TaskStatus.DONE,
                operationId = "status-$taskId",
                queueSequence = (index + 10).toLong(),
            )
        }

        val api = RecordingRoomSyncApi { operation, _ ->
            PushResult.ServerDeleted(
                operationId = operation.operationId,
                deletedAtMillis = 5_000,
                tombstoneVersion = 5,
            )
        }
        val executor = RoomSyncExecutor(
            database = database,
            api = api,
            clock = TestSyncClock(currentMillis = 1_000),
            retryPolicy = RetryPolicy { 10_000 },
        )

        executor.drain()

        val repository = RoomTaskRepository(database, scheduleSync = {})
        assertEquals(
            taskIdsInOperationOrder,
            repository.serverDeletionNotices.first().map { notice -> notice.taskId },
        )

        repository.dismissServerDeletionNotice("task-c")
        assertEquals(
            listOf("task-a", "task-b"),
            repository.serverDeletionNotices.first().map { notice -> notice.taskId },
        )
        assertEquals(
            null,
            database.taskDao().findById("task-c")?.serverDeletionNoticeSequence,
        )
        database.close()
    }
}
