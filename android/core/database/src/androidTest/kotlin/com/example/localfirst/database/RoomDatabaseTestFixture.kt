package com.example.localfirst.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus
import java.util.UUID

internal class RoomDatabaseTestFixture {
    val context: Context = ApplicationProvider.getApplicationContext()
    val databaseName: String = "local-first-${UUID.randomUUID()}.db"

    fun open(): TaskDatabase = Room.databaseBuilder(
        context,
        TaskDatabase::class.java,
        databaseName,
    ).allowMainThreadQueries().build()

    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }
}

internal fun taskEntity(
    id: String,
    status: TaskStatus = TaskStatus.TODO,
    serverVersion: Long? = null,
): TaskEntity = TaskEntity(
    id = id,
    title = "Task $id",
    status = status,
    localRevision = 1,
    serverVersion = serverVersion,
    deletedAtMillis = null,
)

internal fun operationEntity(
    operationId: String,
    taskId: String,
    queueSequence: Long,
    type: OperationType = OperationType.CHANGE_STATUS,
    state: OperationState = OperationState.PENDING,
    leaseUntilMillis: Long? = null,
): SyncOperationEntity = SyncOperationEntity(
    operationId = operationId,
    taskId = taskId,
    queueSequence = queueSequence,
    taskRevision = 1,
    predecessorOperationId = null,
    type = type,
    title = null,
    desiredStatus = TaskStatus.DONE,
    baseServerVersion = 1,
    state = state,
    attemptCount = 0,
    nextAttemptAtMillis = null,
    leaseUntilMillis = leaseUntilMillis,
    acknowledgedServerVersion = null,
    lastErrorCode = null,
)
