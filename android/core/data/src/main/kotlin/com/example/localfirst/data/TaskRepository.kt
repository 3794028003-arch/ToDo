package com.example.localfirst.data

import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.Flow

data class Task(
    val id: String,
    val title: String,
    val status: TaskStatus,
)

data class ServerDeletionNotice(
    val taskId: String,
    val title: String,
)

interface TaskRepository {
    val tasks: Flow<List<Task>>
    val serverDeletionNotices: Flow<List<ServerDeletionNotice>>

    suspend fun createTask(title: String): String

    suspend fun updateTitle(taskId: String, title: String)

    suspend fun changeStatus(taskId: String, status: TaskStatus)

    suspend fun deleteTask(taskId: String)

    suspend fun dismissServerDeletionNotice(taskId: String)
}
