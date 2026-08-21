package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.sync.OperationState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLeaseRecoveryTest {
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
    fun onlyExpiredInFlightLeaseReturnsToPending() = runTest {
        val database = fixture.open()
        database.taskDao().upsert(taskEntity("expired-task"))
        database.taskDao().upsert(taskEntity("active-task"))
        database.syncOperationDao().insert(
            operationEntity(
                operationId = "expired-operation",
                taskId = "expired-task",
                queueSequence = 1,
                state = OperationState.IN_FLIGHT,
                leaseUntilMillis = 999,
            ),
        )
        database.syncOperationDao().insert(
            operationEntity(
                operationId = "active-operation",
                taskId = "active-task",
                queueSequence = 2,
                state = OperationState.IN_FLIGHT,
                leaseUntilMillis = 10_000,
            ),
        )

        RoomSyncStore(database).recoverExpiredLeases(nowMillis = 1_000)

        assertEquals(
            OperationState.PENDING,
            database.syncOperationDao().findById("expired-operation")?.state,
        )
        assertEquals(
            OperationState.IN_FLIGHT,
            database.syncOperationDao().findById("active-operation")?.state,
        )
        database.close()
    }
}
