package com.example.localfirst.data

import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.Flow

data class Task(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val reminderAtMillis: Long? = null,
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val isPinned: Boolean = false,
    val startDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val deletedAtMillis: Long? = null,
)

enum class ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
    WEEKDAYS,
}

data class ServerDeletionNotice(
    val taskId: String,
    val title: String,
    val deletedAtMillis: Long,
)

data class RemoteTask(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val version: Long,
    val reminderAtMillis: Long? = null,
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val isPinned: Boolean = false,
    val startDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val deletedAtMillis: Long? = null,
)

interface TaskRepository {
    val tasks: Flow<List<Task>>
    val deletedTasks: Flow<List<Task>>
    val serverDeletionNotices: Flow<List<ServerDeletionNotice>>

    suspend fun createTask(
        title: String,
        reminderAtMillis: Long? = null,
        reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
    ): String

    suspend fun updateTask(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
        startDateMillis: Long? = null,
        dueDateMillis: Long? = null,
    )

    suspend fun changeStatus(taskId: String, status: TaskStatus)

    suspend fun setPinned(taskId: String, isPinned: Boolean)

    suspend fun reorderTasks(taskIdsInDisplayOrder: List<String>) = Unit

    suspend fun deleteTask(taskId: String)

    suspend fun permanentlyDeleteTask(taskId: String)

    suspend fun dismissServerDeletionNotices(taskIds: Set<String>)

    suspend fun mergeRemoteTasks(tasks: List<RemoteTask>) = Unit
}

interface TaskReminderScheduler {
    fun schedule(
        taskId: String,
        title: String,
        reminderAtMillis: Long,
        reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    )

    fun cancel(taskId: String)
}
