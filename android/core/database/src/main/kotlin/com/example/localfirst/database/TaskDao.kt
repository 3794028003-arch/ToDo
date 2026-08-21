package com.example.localfirst.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun findById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deletedAtMillis IS NULL ORDER BY id")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE serverDeletionNoticePending = 1 ORDER BY id")
    fun observePendingServerDeletionNotices(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET serverDeletionNoticePending = 0 WHERE id = :taskId")
    suspend fun dismissServerDeletionNotice(taskId: String)
}
