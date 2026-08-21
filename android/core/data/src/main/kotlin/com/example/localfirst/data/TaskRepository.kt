package com.example.localfirst.data

import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.Flow

data class Task(
    val id: String,
    val title: String,
    val status: TaskStatus,
)

interface TaskRepository {
    val tasks: Flow<List<Task>>

    suspend fun createTask(title: String): String

    suspend fun updateTitle(taskId: String, title: String)

    suspend fun changeStatus(taskId: String, status: TaskStatus)

    suspend fun deleteTask(taskId: String)
}
