package com.example.localfirst.board

import com.example.localfirst.data.Task
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.ZoneId
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
    fun repositoryOrderIsPreservedInsideEachBoardColumn() = runTest {
        val repository = FakeTaskRepository(
            listOf(
                Task("task-3", "First", TaskStatus.TODO),
                Task("task-1", "Second", TaskStatus.TODO),
                Task("task-2", "Third", TaskStatus.TODO),
            ),
        )
        val viewModel = BoardViewModel(repository)

        advanceUntilIdle()

        assertEquals(
            listOf("task-3", "task-1", "task-2"),
            viewModel.state.value.todo.map(Task::id),
        )
    }

    @Test
    fun onlyOneTaskActionBarIsExpandedAndPinningUsesRepository() = runTest {
        val repository = FakeTaskRepository(
            listOf(
                Task("ordinary", "Ordinary", TaskStatus.TODO),
                Task("important", "Important", TaskStatus.TODO),
            ),
        )
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(BoardAction.ToggleTaskActions("ordinary"))
        advanceUntilIdle()
        assertEquals("ordinary", viewModel.state.value.expandedTaskId)
        viewModel.onAction(BoardAction.ToggleTaskActions("important"))
        advanceUntilIdle()
        assertEquals("important", viewModel.state.value.expandedTaskId)

        val important = viewModel.state.value.todo.first { it.id == "important" }
        viewModel.onAction(BoardAction.SetPinned(important, true))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.expandedTaskId)
        assertEquals("important", viewModel.state.value.todo.first().id)
        assertEquals(true, viewModel.state.value.todo.first().isPinned)
        assertEquals("pin:true", repository.calls.last())
    }

    @Test
    fun userActionsOnlyMutateThroughRepositoryAndStateUpdatesFromRepositoryFlow() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(repository)

        viewModel.onAction(BoardAction.OpenCreate)
        viewModel.onAction(BoardAction.UpdateEditorContent("Created offline"))
        viewModel.onAction(BoardAction.SaveEditor)
        advanceUntilIdle()
        assertEquals("Created offline", viewModel.state.value.todo.single().title)

        viewModel.onAction(BoardAction.OpenEdit(viewModel.state.value.todo.single()))
        viewModel.onAction(BoardAction.UpdateEditorContent("Renamed offline"))
        viewModel.onAction(BoardAction.SaveEditor)
        advanceUntilIdle()
        assertEquals("Renamed offline", viewModel.state.value.todo.single().title)

        val renamed = viewModel.state.value.todo.single()
        viewModel.onAction(BoardAction.RequestMove(renamed, TaskStatus.DONE))
        viewModel.onAction(BoardAction.ConfirmOperation)
        advanceUntilIdle()
        assertEquals("generated-task", viewModel.state.value.done.single().id)

        viewModel.onAction(BoardAction.RequestDelete(viewModel.state.value.done.single()))
        viewModel.onAction(BoardAction.ConfirmOperation)
        advanceUntilIdle()
        assertEquals(emptyList<Task>(), viewModel.state.value.todo)
        assertEquals(emptyList<Task>(), viewModel.state.value.doing)
        assertEquals(emptyList<Task>(), viewModel.state.value.done)
        assertEquals(
            listOf("create", "update", "move", "delete"),
            repository.calls,
        )
    }

    @Test
    fun minutePrecisionReminderAndRepeatArePassedToRepository() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(
            repository = repository,
            nowMillis = { 1_700_000_000_000 },
            zoneId = ZoneId.of("UTC"),
        )

        viewModel.onAction(BoardAction.OpenCreate)
        viewModel.onAction(BoardAction.UpdateEditorContent("Future task"))
        viewModel.onAction(BoardAction.SetReminderTime(3, 4))
        viewModel.onAction(BoardAction.SetReminderRepeat(ReminderRepeat.DAILY))
        viewModel.onAction(BoardAction.SaveEditor)
        advanceUntilIdle()

        assertEquals(1_700_017_440_000, repository.lastReminderAtMillis)
        assertEquals(ReminderRepeat.DAILY, repository.lastReminderRepeat)
    }

    @Test
    fun rapidRepeatedMoveIsHandledOnlyOnce() = runTest {
        val task = Task("task", "Stable", TaskStatus.TODO)
        val repository = FakeTaskRepository(listOf(task))
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        repeat(20) { viewModel.onAction(BoardAction.MoveImmediately(task, TaskStatus.DOING)) }
        advanceUntilIdle()

        assertEquals(1, repository.calls.count { it == "move" })
        assertEquals(TaskStatus.DOING, viewModel.state.value.doing.single().status)
    }

    @Test
    fun batchEditSelectsCurrentPageAndRunsOneOperationPerTask() = runTest {
        val repository = FakeTaskRepository(listOf(
            Task("todo-1", "One", TaskStatus.TODO),
            Task("todo-2", "Two", TaskStatus.TODO),
            Task("doing", "Other page", TaskStatus.DOING),
        ))
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()
        viewModel.onAction(BoardAction.StartBatchEdit)
        viewModel.onAction(BoardAction.ToggleSelectAll)
        viewModel.onAction(BoardAction.BatchMove(TaskStatus.DONE))
        repeat(10) { viewModel.onAction(BoardAction.BatchMove(TaskStatus.DONE)) }
        advanceUntilIdle()

        assertEquals(2, repository.calls.count { it == "move" })
        assertEquals(setOf("todo-1", "todo-2"), viewModel.state.value.done.mapTo(mutableSetOf(), Task::id))
    }

    @Test
    fun changedEditorRequiresDiscardConfirmation() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(repository)

        viewModel.onAction(BoardAction.OpenCreate)
        viewModel.onAction(BoardAction.UpdateEditorContent("Do not lose this"))
        viewModel.onAction(BoardAction.RequestCloseEditor)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.showDiscardConfirmation)
        assertEquals("Do not lose this", viewModel.state.value.editor?.content)

        viewModel.onAction(BoardAction.ConfirmDiscardEditor)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.editor)
    }

    @Test
    fun quickCompleteIsIdempotentAndGraySnapshotIsClearedByRefresh() = runTest {
        val task = Task("quick", "Quick complete", TaskStatus.TODO)
        val repository = FakeTaskRepository(listOf(task))
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        repeat(20) { viewModel.onAction(BoardAction.QuickComplete(task)) }
        runCurrent()

        assertEquals(1, viewModel.state.value.todo.count { it.id == task.id })
        advanceUntilIdle()

        assertEquals(1, repository.calls.count { it == "move" })
        assertEquals(setOf("quick"), viewModel.state.value.transientCompletedTaskIds)
        assertEquals(listOf("quick"), viewModel.state.value.todo.map(Task::id))
        assertEquals(listOf("quick"), viewModel.state.value.done.map(Task::id))

        viewModel.onAction(BoardAction.RefreshBoard)
        advanceUntilIdle()
        assertEquals(emptySet<String>(), viewModel.state.value.transientCompletedTaskIds)
        assertEquals(emptyList<Task>(), viewModel.state.value.todo)
        assertEquals(listOf("quick"), viewModel.state.value.done.map(Task::id))
    }

    @Test
    fun recycleBinBackIgnoresRapidRepeatedClicks() = runTest {
        val repository = FakeTaskRepository(emptyList())
        repository.publishDeleted(Task("deleted", "Deleted", TaskStatus.TODO, deletedAtMillis = 123L))
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(BoardAction.OpenRecycleBin)
        repeat(20) { viewModel.onAction(BoardAction.CloseRecycleBin) }
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isRecycleBinOpen)
        assertEquals(listOf("deleted"), viewModel.state.value.deletedTasks.map(Task::id))
    }

    @Test
    fun permanentDeleteConfirmationIsIdempotentAndRemovesTrashItem() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val deleted = Task("deleted", "Deleted", TaskStatus.TODO, deletedAtMillis = 123L)
        repository.publishDeleted(deleted)
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        repeat(20) { viewModel.onAction(BoardAction.RequestPermanentDelete(deleted)) }
        repeat(20) { viewModel.onAction(BoardAction.ConfirmOperation) }
        advanceUntilIdle()

        assertEquals(1, repository.calls.count { it == "permanent-delete" })
        assertEquals(emptyList<Task>(), viewModel.state.value.deletedTasks)
    }

    @Test
    fun recycleBinBatchDeleteSelectedOnlyDeletesCheckedTasksOnce() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val first = Task("deleted-1", "First", TaskStatus.TODO, deletedAtMillis = 1L)
        val second = Task("deleted-2", "Second", TaskStatus.DOING, deletedAtMillis = 2L)
        val third = Task("deleted-3", "Third", TaskStatus.DONE, deletedAtMillis = 3L)
        listOf(first, second, third).forEach(repository::publishDeleted)
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(BoardAction.OpenRecycleBin)
        viewModel.onAction(BoardAction.StartRecycleBinBatchEdit)
        viewModel.onAction(BoardAction.ToggleDeletedTaskSelection(first.id))
        viewModel.onAction(BoardAction.ToggleDeletedTaskSelection(second.id))
        repeat(20) { viewModel.onAction(BoardAction.RequestPermanentDeleteSelected) }
        repeat(20) { viewModel.onAction(BoardAction.ConfirmOperation) }
        advanceUntilIdle()

        assertEquals(2, repository.calls.count { it == "permanent-delete" })
        assertEquals(listOf(third.id), viewModel.state.value.deletedTasks.map(Task::id))
        assertEquals(false, viewModel.state.value.isRecycleBinBatchEditing)
    }

    @Test
    fun recycleBinSelectAllThenDeletePermanentlyDeletesEveryTrashTaskOnce() = runTest {
        val repository = FakeTaskRepository(emptyList())
        repeat(3) { index ->
            repository.publishDeleted(Task("deleted-$index", "Task $index", TaskStatus.TODO, deletedAtMillis = index.toLong()))
        }
        val viewModel = BoardViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(BoardAction.OpenRecycleBin)
        viewModel.onAction(BoardAction.StartRecycleBinBatchEdit)
        viewModel.onAction(BoardAction.ToggleSelectAllDeletedTasks)
        repeat(20) { viewModel.onAction(BoardAction.RequestPermanentDeleteSelected) }
        repeat(20) { viewModel.onAction(BoardAction.ConfirmOperation) }
        advanceUntilIdle()

        assertEquals(3, repository.calls.count { it == "permanent-delete" })
        assertEquals(emptyList<Task>(), viewModel.state.value.deletedTasks)
        assertEquals(false, viewModel.state.value.isRecycleBinOperationRunning)
    }

    @Test
    fun optionalDateRangeIsPersistedByViewModel() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(repository)
        viewModel.onAction(BoardAction.OpenCreate)
        viewModel.onAction(BoardAction.UpdateEditorContent("Dated task"))
        viewModel.onAction(BoardAction.SetStartDate(1_800_000_000_000L))
        viewModel.onAction(BoardAction.SetDueDate(1_800_086_400_000L))
        viewModel.onAction(BoardAction.SaveEditor)
        advanceUntilIdle()

        assertEquals(1_800_000_000_000L, repository.lastStartDateMillis)
        assertEquals(1_800_086_400_000L, repository.lastDueDateMillis)
    }

    @Test
    fun serverDeletionNoticeIsExposedUntilTheUiDismissesIt() = runTest {
        val repository = FakeTaskRepository(emptyList())
        val viewModel = BoardViewModel(repository)
        val notice = ServerDeletionNotice("deleted-task", "Offline task")

        repository.publishServerDeletionNotice(notice)
        advanceUntilIdle()

        assertEquals(notice, viewModel.state.value.serverDeletionNotice)

        viewModel.onAction(BoardAction.DismissServerDeletionNotice(notice.taskId))
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.serverDeletionNotice)
        assertEquals("dismiss-notice", repository.calls.last())
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
    private val mutableDeletedTasks = MutableStateFlow<List<Task>>(emptyList())
    private val mutableServerDeletionNotices =
        MutableStateFlow<List<ServerDeletionNotice>>(emptyList())
    override val tasks: Flow<List<Task>> = mutableTasks
    override val deletedTasks: Flow<List<Task>> = mutableDeletedTasks
    override val serverDeletionNotices: Flow<List<ServerDeletionNotice>> =
        mutableServerDeletionNotices
    val calls = mutableListOf<String>()
    var lastReminderAtMillis: Long? = null
    var lastReminderRepeat: ReminderRepeat = ReminderRepeat.NONE
    var lastStartDateMillis: Long? = null
    var lastDueDateMillis: Long? = null

    override suspend fun createTask(
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ): String {
        calls += "create"
        lastReminderAtMillis = reminderAtMillis
        lastReminderRepeat = reminderRepeat
        lastStartDateMillis = startDateMillis
        lastDueDateMillis = dueDateMillis
        mutableTasks.value += Task(
            "generated-task", title, TaskStatus.TODO, reminderAtMillis,
            reminderRepeat = reminderRepeat, startDateMillis = startDateMillis, dueDateMillis = dueDateMillis,
        )
        return "generated-task"
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: ReminderRepeat,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ) {
        calls += "update"
        lastReminderAtMillis = reminderAtMillis
        lastReminderRepeat = reminderRepeat
        lastStartDateMillis = startDateMillis
        lastDueDateMillis = dueDateMillis
        mutableTasks.value = mutableTasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    title = title,
                    reminderAtMillis = reminderAtMillis,
                    reminderRepeat = reminderRepeat,
                    startDateMillis = startDateMillis,
                    dueDateMillis = dueDateMillis,
                )
            } else task
        }
    }

    override suspend fun changeStatus(taskId: String, status: TaskStatus) {
        calls += "move"
        mutableTasks.value = mutableTasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = status,
                    reminderAtMillis = if (status == TaskStatus.DONE) null else task.reminderAtMillis,
                )
            } else task
        }
    }

    override suspend fun setPinned(taskId: String, isPinned: Boolean) {
        calls += "pin:$isPinned"
        mutableTasks.value = mutableTasks.value.map { task ->
            if (task.id == taskId) task.copy(isPinned = isPinned) else task
        }
    }

    override suspend fun deleteTask(taskId: String) {
        calls += "delete"
        mutableTasks.value.firstOrNull { it.id == taskId }?.let {
            mutableDeletedTasks.value += it.copy(deletedAtMillis = 1L)
        }
        mutableTasks.value = mutableTasks.value.filterNot { task -> task.id == taskId }
    }

    override suspend fun permanentlyDeleteTask(taskId: String) {
        calls += "permanent-delete"
        mutableDeletedTasks.value = mutableDeletedTasks.value.filterNot { task -> task.id == taskId }
    }

    override suspend fun dismissServerDeletionNotice(taskId: String) {
        calls += "dismiss-notice"
        mutableServerDeletionNotices.value = mutableServerDeletionNotices.value.filterNot {
            notice -> notice.taskId == taskId
        }
    }

    fun publishServerDeletionNotice(notice: ServerDeletionNotice) {
        mutableServerDeletionNotices.value += notice
    }

    fun publishDeleted(task: Task) {
        mutableDeletedTasks.value += task
    }
}
