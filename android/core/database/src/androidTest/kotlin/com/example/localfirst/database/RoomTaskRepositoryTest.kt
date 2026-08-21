package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.data.Task
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private lateinit var fixture: RoomDatabaseTestFixture
    private lateinit var database: TaskDatabase

    @Before
    fun setUp() {
        fixture = RoomDatabaseTestFixture()
        fixture.deleteDatabase()
        database = fixture.open()
    }

    @After
    fun tearDown() {
        database.close()
        fixture.deleteDatabase()
    }

    @Test
    fun titleUpdateIsImmediatelyObservableAndAtomicallyEnqueuesUpdate() = runTest {
        database.taskDao().upsert(
            taskEntity("task-update", TaskStatus.TODO, serverVersion = 4),
        )
        var scheduleCalls = 0
        val repository = repository(
            operationIds = ArrayDeque(listOf("update-operation")),
            scheduleSync = { scheduleCalls += 1 },
        )

        repository.updateTitle("task-update", "Edited offline")

        val visibleTask = repository.tasks.first().single()
        val storedTask = database.taskDao().findById("task-update")
        val operation = database.syncOperationDao().all().single()
        assertEquals("Edited offline", visibleTask.title)
        assertEquals("Edited offline", storedTask?.title)
        assertEquals(2L, storedTask?.localRevision)
        assertEquals(OperationType.UPDATE, operation.type)
        assertEquals("Edited offline", operation.title)
        assertEquals(4L, operation.baseServerVersion)
        assertEquals(2L, operation.taskRevision)
        assertEquals(OperationState.PENDING, operation.state)
        assertEquals(1, scheduleCalls)
    }

    @Test
    fun deleteImmediatelyHidesTaskAndEnqueuesVersionChainedTombstone() = runTest {
        database.taskDao().upsert(
            taskEntity("task-delete", TaskStatus.DOING, serverVersion = 4).copy(
                title = "Edited offline",
                localRevision = 2,
            ),
        )
        database.syncOperationDao().insert(
            operationEntity(
                operationId = "update-operation",
                taskId = "task-delete",
                queueSequence = 10,
                type = OperationType.UPDATE,
            ).copy(
                taskRevision = 2,
                title = "Edited offline",
                desiredStatus = null,
                baseServerVersion = 4,
            ),
        )
        var scheduleCalls = 0
        val repository = repository(
            operationIds = ArrayDeque(listOf("delete-operation")),
            scheduleSync = { scheduleCalls += 1 },
        )

        repository.deleteTask("task-delete")

        assertEquals(emptyList<Task>(), repository.tasks.first())
        val storedTask = database.taskDao().findById("task-delete")
        val operation = database.syncOperationDao().findById("delete-operation")
        assertEquals(5_000L, storedTask?.deletedAtMillis)
        assertEquals(3L, storedTask?.localRevision)
        assertEquals(OperationType.DELETE, operation?.type)
        assertEquals(5L, operation?.baseServerVersion)
        assertEquals(11L, operation?.queueSequence)
        assertEquals(3L, operation?.taskRevision)
        assertEquals("update-operation", operation?.predecessorOperationId)
        assertNull(operation?.title)
        assertNull(operation?.desiredStatus)
        assertEquals(1, scheduleCalls)
    }

    @Test
    fun schedulerFailureDoesNotUndoOrFailAnAlreadyCommittedLocalUpdate() = runTest {
        database.taskDao().upsert(
            taskEntity("task-safe", TaskStatus.TODO, serverVersion = 2),
        )
        val repository = repository(
            operationIds = ArrayDeque(listOf("safe-operation")),
            scheduleSync = { error("WorkManager unavailable") },
        )

        repository.updateTitle("task-safe", "Still succeeds locally")

        assertEquals(
            "Still succeeds locally",
            database.taskDao().findById("task-safe")?.title,
        )
        assertEquals(
            OperationState.PENDING,
            database.syncOperationDao().findById("safe-operation")?.state,
        )
    }

    private fun repository(
        operationIds: ArrayDeque<String>,
        scheduleSync: () -> Unit,
    ): RoomTaskRepository = RoomTaskRepository(
        database = database,
        taskIdFactory = { "generated-task" },
        operationIdFactory = operationIds::removeFirst,
        nowMillis = { 5_000L },
        scheduleSync = scheduleSync,
    )
}
