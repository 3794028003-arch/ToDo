package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTaskOutboxAtomicityTest {
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
    fun taskMutationRollsBackWhenOutboxInsertFails() = runTest {
        var database = fixture.open()
        database.taskDao().upsert(taskEntity("task-x", TaskStatus.TODO, serverVersion = 3))
        database.syncOperationDao().insert(
            operationEntity(
                operationId = "duplicate-operation",
                taskId = "task-x",
                queueSequence = 1,
            ),
        )
        val mutations = RoomTaskMutationStore(database)

        try {
            mutations.changeStatusAndEnqueue(
                taskId = "task-x",
                status = TaskStatus.DONE,
                operationId = "duplicate-operation",
                queueSequence = 2,
            )
            fail("Expected duplicate outbox operation to abort the transaction")
        } catch (_: Exception) {
            // Expected: the duplicate operationId violates the outbox primary key.
        }
        database.close()

        database = fixture.open()
        assertEquals(TaskStatus.TODO, database.taskDao().findById("task-x")?.status)
        assertEquals(1, database.syncOperationDao().all().size)
        database.close()
    }
}
