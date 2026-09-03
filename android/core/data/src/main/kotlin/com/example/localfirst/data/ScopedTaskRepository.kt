package com.example.localfirst.data

import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

const val LOCAL_TASK_SPACE = "local"
fun accountTaskSpace(userId: String): String = "account:${userId.trim()}"

@OptIn(ExperimentalCoroutinesApi::class)
class ScopedTaskRepository(
    private val activeSpace: StateFlow<String>,
    private val repositoryFor: (String) -> TaskRepository,
) : TaskRepository {
    override val tasks: Flow<List<Task>> = activeSpace.flatMapLatest { repositoryFor(it).tasks }
    override val deletedTasks: Flow<List<Task>> = activeSpace.flatMapLatest { repositoryFor(it).deletedTasks }
    override val serverDeletionNotices: Flow<List<ServerDeletionNotice>> =
        activeSpace.flatMapLatest { repositoryFor(it).serverDeletionNotices }

    override suspend fun createTask(title: String, reminderAtMillis: Long?, reminderRepeat: ReminderRepeat, startDateMillis: Long?, dueDateMillis: Long?) =
        current().createTask(title, reminderAtMillis, reminderRepeat, startDateMillis, dueDateMillis)
    override suspend fun updateTask(taskId: String, title: String, reminderAtMillis: Long?, reminderRepeat: ReminderRepeat, startDateMillis: Long?, dueDateMillis: Long?) =
        current().updateTask(taskId, title, reminderAtMillis, reminderRepeat, startDateMillis, dueDateMillis)
    override suspend fun changeStatus(taskId: String, status: TaskStatus) = current().changeStatus(taskId, status)
    override suspend fun setPinned(taskId: String, isPinned: Boolean) = current().setPinned(taskId, isPinned)
    override suspend fun reorderTasks(taskIdsInDisplayOrder: List<String>) = current().reorderTasks(taskIdsInDisplayOrder)
    override suspend fun deleteTask(taskId: String) = current().deleteTask(taskId)
    override suspend fun permanentlyDeleteTask(taskId: String) = current().permanentlyDeleteTask(taskId)
    override suspend fun dismissServerDeletionNotices(taskIds: Set<String>) =
        current().dismissServerDeletionNotices(taskIds)
    override suspend fun mergeRemoteTasks(tasks: List<RemoteTask>) = current().mergeRemoteTasks(tasks)

    private fun current(): TaskRepository = repositoryFor(activeSpace.value)
}
