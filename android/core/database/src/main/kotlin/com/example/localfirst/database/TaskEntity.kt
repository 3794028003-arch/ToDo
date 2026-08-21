package com.example.localfirst.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.localfirst.sync.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: TaskStatus,
    val localRevision: Long,
    val serverVersion: Long?,
    val deletedAtMillis: Long?,
    @ColumnInfo(defaultValue = "0")
    val serverDeletionNoticePending: Boolean = false,
)
