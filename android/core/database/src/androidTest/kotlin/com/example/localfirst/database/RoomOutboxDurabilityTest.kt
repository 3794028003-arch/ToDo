package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOutboxDurabilityTest {
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
    fun thirtyPendingOperationsSurviveDatabaseCloseAndReopen() = runTest {
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
            database.taskDao().upsert(
                taskEntity(
                    id = taskId,
                    status = TaskStatus.TODO,
                    serverVersion = 4,
                ),
            )
            mutations.changeStatusAndEnqueue(
                taskId = taskId,
                status = TaskStatus.DONE,
                operationId = "status-$index",
                queueSequence = (20 + index).toLong(),
            )
        }

        assertEquals(30, database.syncOperationDao().all().size)
        database.close()

        database = fixture.open()
        mutations = RoomTaskMutationStore(database)
        val reopenedOperations = database.syncOperationDao().all()

        assertEquals(30, reopenedOperations.size)
        assertEquals(30, reopenedOperations.count { it.state == OperationState.PENDING })
        assertEquals(
            (0 until 20).map { "create-$it" }.toSet() +
                (0 until 10).map { "status-$it" }.toSet(),
            reopenedOperations.map { it.operationId }.toSet(),
        )
        // Keep the reopened store live long enough to prove it can read the same database.
        assertEquals(30, mutations.pendingOperationCount())
        database.close()
    }
}
