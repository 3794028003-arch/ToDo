package com.example.localfirst.board

import com.example.localfirst.data.Task
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repositoryTasksAreGroupedIntoThreeBoardColumns() = runTest {
        val repository = FakeTaskRepository(
            listOf(
                Task("todo-1", "Todo", TaskStatus.TODO),
                Task("doing-1", "Doing", TaskStatus.DOING),
                Task("done-1", "Done", TaskStatus.DONE),
            ),
        )
        val viewModel = BoardViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf("todo-1"), viewModel.state.value.todo.map(Task::id))
        assertEquals(listOf("doing-1"), viewModel.state.value.doing.map(Task::id))
        assertEquals(listOf("done-1"), viewModel.state.value.done.map(Task::id))
    }

    @Test
    fun userActionsOnlyMutateThroughRepositoryAndStateUpdatesFromRepositoryFlow() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(repository)

        viewModel.onAction(BoardAction.CreateTask("Created offline"))
        advanceUntilIdle()
        assertEquals("Created offline", viewModel.state.value.todo.single().title)

        viewModel.onAction(BoardAction.RenameTask("generated-task", "Renamed offline"))
        advanceUntilIdle()
        assertEquals("Renamed offline", viewModel.state.value.todo.single().title)

        viewModel.onAction(BoardAction.MoveTask("generated-task", TaskStatus.DONE))
        advanceUntilIdle()
        assertEquals("generated-task", viewModel.state.value.done.single().id)

        viewModel.onAction(BoardAction.DeleteTask("generated-task"))
        advanceUntilIdle()
        assertEquals(BoardUiState(), viewModel.state.value)
        assertEquals(
            listOf("create", "rename", "move", "delete"),
            repository.calls,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeTaskRepository(
    initialTasks: List<Task>,
) : TaskRepository {
    private val mutableTasks = MutableStateFlow(initialTasks)
    override val tasks: Flow<List<Task>> = mutableTasks
    val calls = mutableListOf<String>()

    override suspend fun createTask(title: String): String {
        calls += "create"
        mutableTasks.value += Task("generated-task", title, TaskStatus.TODO)
        return "generated-task"
    }

    override suspend fun updateTitle(taskId: String, title: String) {
        calls += "rename"
        mutableTasks.value = mutableTasks.value.map { task ->
            if (task.id == taskId) task.copy(title = title) else task
        }
    }

    override suspend fun changeStatus(taskId: String, status: TaskStatus) {
        calls += "move"
        mutableTasks.value = mutableTasks.value.map { task ->
            if (task.id == taskId) task.copy(status = status) else task
        }
    }

    override suspend fun deleteTask(taskId: String) {
        calls += "delete"
        mutableTasks.value = mutableTasks.value.filterNot { task -> task.id == taskId }
    }
}
