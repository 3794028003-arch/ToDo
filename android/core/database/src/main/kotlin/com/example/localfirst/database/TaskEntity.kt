package com.example.localfirst.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.sync.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: TaskStatus,
    val reminderAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "'NONE'")
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    val startDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val localRevision: Long,
    val serverVersion: Long?,
    val deletedAtMillis: Long?,
    @ColumnInfo(defaultValue = "0")
    val permanentDeletionRequested: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val serverDeletionNoticePending: Boolean = false,
    val serverDeletionNoticeSequence: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val createdSequence: Long = 0,
    val lastModifiedSequence: Long? = null,
    val manualOrder: Long? = null,
)
