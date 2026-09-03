package com.example.localfirst.data

import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedTaskRepositoryTest {
    @Test fun `local and signed in account tasks never mix`() = runBlocking {
        val activeSpace = MutableStateFlow(LOCAL_TASK_SPACE)
        val local = FakeSpaceRepository()
        val account = FakeSpaceRepository()
        val scoped = ScopedTaskRepository(activeSpace) { space ->
            if (space == LOCAL_TASK_SPACE) local else account
        }

        scoped.createTask("仅本机任务")
        activeSpace.value = accountTaskSpace("account-1")
        scoped.createTask("账号任务")

        assertEquals(listOf("账号任务"), scoped.tasks.first().map(Task::title))
        activeSpace.value = LOCAL_TASK_SPACE
        assertEquals(listOf("仅本机任务"), scoped.tasks.first().map(Task::title))
        assertTrue(account.created.none { it == "仅本机任务" })
    }

    @Test fun `switching between accounts restores each independent task space`() = runBlocking {
        val activeSpace = MutableStateFlow(accountTaskSpace("account-a"))
        val spaces = mutableMapOf<String, FakeSpaceRepository>()
        val scoped = ScopedTaskRepository(activeSpace) { spaces.getOrPut(it, ::FakeSpaceRepository) }

        scoped.createTask("A的任务")
        activeSpace.value = accountTaskSpace("account-b")
        scoped.createTask("B的任务")
        activeSpace.value = accountTaskSpace("account-a")

        assertEquals(listOf("A的任务"), scoped.tasks.first().map(Task::title))
    }
}

private class FakeSpaceRepository : TaskRepository {
    private val active = MutableStateFlow<List<Task>>(emptyList())
    private val deleted = MutableStateFlow<List<Task>>(emptyList())
    private val notices = MutableStateFlow<List<ServerDeletionNotice>>(emptyList())
    val created = mutableListOf<String>()
    override val tasks = active
    override val deletedTasks = deleted
    override val serverDeletionNotices = notices

    override suspend fun createTask(
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ): String {
        created += title
        val id = "task-${created.size}"
        active.value = active.value + Task(id, title, TaskStatus.TODO)
        return id
    }

    override suspend fun updateTask(taskId: String, title: String, reminderAtMillis: Long?, reminderRepeat: ReminderRepeat, startDateMillis: Long?, dueDateMillis: Long?) = Unit
    override suspend fun changeStatus(taskId: String, status: TaskStatus) = Unit
    override suspend fun setPinned(taskId: String, isPinned: Boolean) = Unit
    override suspend fun deleteTask(taskId: String) = Unit
    override suspend fun permanentlyDeleteTask(taskId: String) = Unit
    override suspend fun dismissServerDeletionNotices(taskIds: Set<String>) = Unit
}
