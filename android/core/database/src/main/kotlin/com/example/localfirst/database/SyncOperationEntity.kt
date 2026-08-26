package com.example.localfirst.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus

@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["taskId", "queueSequence"]),
        Index(value = ["state", "nextAttemptAtMillis", "queueSequence"]),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey val operationId: String,
    val taskId: String,
    val queueSequence: Long,
    val taskRevision: Long,
    val predecessorOperationId: String?,
    val type: OperationType,
    val title: String?,
    val reminderAtMillis: Long? = null,
    val reminderRepeat: String? = null,
    val isPinned: Boolean? = null,
    val startDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val desiredStatus: TaskStatus?,
    val baseServerVersion: Long?,
    val state: OperationState,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long?,
    val leaseUntilMillis: Long?,
    val acknowledgedServerVersion: Long?,
    val lastErrorCode: String?,
)
