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

    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAtMillis IS NULL
        ORDER BY
            CASE WHEN manualOrder IS NULL THEN 1 ELSE 0 END,
            manualOrder,
            isPinned DESC,
            CASE WHEN lastModifiedSequence IS NULL THEN 1 ELSE 0 END,
            lastModifiedSequence DESC,
            createdSequence,
            id
        """,
    )
    fun observeActive(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAtMillis IS NOT NULL
          AND permanentDeletionRequested = 0
        ORDER BY deletedAtMillis DESC, id
        """,
    )
    fun observeDeleted(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE serverDeletionNoticePending = 1
        ORDER BY serverDeletionNoticeSequence DESC, id
        """,
    )
    fun observePendingServerDeletionNotices(): Flow<List<TaskEntity>>

    @Query(
        """
        UPDATE tasks
        SET serverDeletionNoticePending = 0,
            serverDeletionNoticeSequence = NULL
        WHERE id IN (:taskIds)
        """,
    )
    suspend fun dismissServerDeletionNotices(taskIds: Set<String>)

    @Query("UPDATE tasks SET permanentDeletionRequested = 1 WHERE id = :taskId AND deletedAtMillis IS NOT NULL")
    suspend fun requestPermanentDeletion(taskId: String): Int

    @Query("UPDATE tasks SET manualOrder = :order WHERE id = :taskId AND deletedAtMillis IS NULL")
    suspend fun setManualOrder(taskId: String, order: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)
}
