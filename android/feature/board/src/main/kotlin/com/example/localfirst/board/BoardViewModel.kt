package com.example.localfirst.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.ReminderScheduleCalculator
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.data.Task
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BoardViewModel(
    private val repository: TaskRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val presentation = MutableStateFlow(BoardPresentationState())
    private val mutableEffects = MutableSharedFlow<BoardEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()
    val state: StateFlow<BoardUiState> = combine(
        repository.tasks,
        repository.deletedTasks,
        repository.serverDeletionNotices,
        presentation,
    ) { tasks, deletedTasks, notices, ui ->
            tasks.toBoardUiState(deletedTasks, notices.firstOrNull(), ui)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BoardUiState())

    fun onAction(action: BoardAction) {
        when (action) {
            is BoardAction.SelectStatus -> update { copy(selectedStatus = action.status, expandedTaskId = null, selectedTaskIds = emptySet()) }
            BoardAction.StartSearch -> update { copy(isSearching = true, isMainMenuOpen = false, expandedTaskId = null) }
            is BoardAction.UpdateSearch -> update { copy(searchQuery = action.query) }
            BoardAction.ExitSearch -> update { copy(isSearching = false, searchQuery = "") }
            is BoardAction.SelectSearchResult -> update {
                val next = highlightGeneration + 1
                copy(isSearching = false, searchQuery = "", selectedStatus = action.task.status,
                    highlightGeneration = next, highlightRequest = HighlightRequest(action.task.id, action.task.status, next),
                    expandedTaskId = null)
            }
            BoardAction.ToggleMainMenu -> update { copy(isMainMenuOpen = !isMainMenuOpen) }
            BoardAction.CloseMainMenu -> update { copy(isMainMenuOpen = false) }
            BoardAction.ToggleTheme -> update { copy(isDarkMode = !isDarkMode, isMainMenuOpen = false) }
            BoardAction.OpenRecycleBin -> update {
                if (isRecycleBinOpen) this else copy(isRecycleBinOpen = true, isMainMenuOpen = false, selectedDeletedTaskIds = emptySet())
            }
            BoardAction.CloseRecycleBin -> update {
                if (!isRecycleBinOpen || isRecycleBinOperationRunning) this
                else copy(isRecycleBinOpen = false, isRecycleBinBatchEditing = false, selectedDeletedTaskIds = emptySet())
            }
            BoardAction.StartRecycleBinBatchEdit -> update {
                if (!isRecycleBinOpen || isRecycleBinOperationRunning) this
                else copy(isRecycleBinBatchEditing = true, selectedDeletedTaskIds = emptySet())
            }
            BoardAction.CancelRecycleBinBatchEdit -> update {
                if (isRecycleBinOperationRunning) this
                else copy(isRecycleBinBatchEditing = false, selectedDeletedTaskIds = emptySet())
            }
            is BoardAction.ToggleDeletedTaskSelection -> update {
                if (!isRecycleBinBatchEditing || isRecycleBinOperationRunning) this
                else copy(selectedDeletedTaskIds = selectedDeletedTaskIds.toggle(action.taskId))
            }
            BoardAction.ToggleSelectAllDeletedTasks -> {
                val ids = state.value.deletedTasks.mapTo(mutableSetOf(), Task::id)
                update {
                    if (!isRecycleBinBatchEditing || isRecycleBinOperationRunning) this
                    else copy(selectedDeletedTaskIds = if (selectedDeletedTaskIds.containsAll(ids)) emptySet() else ids)
                }
            }
            BoardAction.RequestPermanentDeleteSelected -> {
                val ids = presentation.value.selectedDeletedTaskIds
                if (ids.isNotEmpty() && !presentation.value.isRecycleBinOperationRunning) {
                    update { copy(operationConfirmation = OperationConfirmation.PermanentDeleteMany(ids)) }
                }
            }
            BoardAction.StartBatchEdit -> update { copy(isBatchEditing = true, isMainMenuOpen = false, expandedTaskId = null, selectedTaskIds = emptySet()) }
            BoardAction.CancelBatchEdit -> update { if (isBatchOperationRunning) this else copy(isBatchEditing = false, selectedTaskIds = emptySet()) }
            is BoardAction.ToggleBatchSelection -> update {
                if (!isBatchEditing || isBatchOperationRunning) this else copy(selectedTaskIds = selectedTaskIds.toggle(action.taskId))
            }
            BoardAction.ToggleSelectAll -> {
                val ids = state.value.tasksFor(state.value.selectedStatus).mapTo(mutableSetOf(), Task::id)
                update { if (!isBatchEditing || isBatchOperationRunning) this else copy(selectedTaskIds = if (selectedTaskIds.containsAll(ids)) emptySet() else ids) }
            }
            BoardAction.RequestBatchDelete -> presentation.value.selectedTaskIds.takeIf { it.isNotEmpty() }?.let { ids ->
                if (!presentation.value.isBatchOperationRunning) update { copy(operationConfirmation = OperationConfirmation.BatchDelete(ids)) }
            }
            is BoardAction.BatchMove -> runBatchMove(action.status)
            is BoardAction.ToggleTaskActions -> update { if (isBatchEditing) this else copy(expandedTaskId = action.taskId.takeUnless { it == expandedTaskId }) }
            BoardAction.CloseTaskActions -> update { copy(expandedTaskId = null) }
            is BoardAction.SetPinned -> runTaskMutation(action.task.id) { repository.setPinned(action.task.id, action.isPinned) }
            BoardAction.OpenCreate -> update { copy(editor = TaskEditorState(), showDiscardConfirmation = false, expandedTaskId = null, datePickerTarget = null) }
            is BoardAction.OpenEdit -> if (!presentation.value.isBatchEditing) update { copy(editor = action.task.toEditorState(zoneId), showDiscardConfirmation = false, expandedTaskId = null, datePickerTarget = null) }
            is BoardAction.UpdateEditorContent -> updateEditor { copy(content = action.content, validationMessage = null) }
            BoardAction.ToggleDateSection -> updateEditor { copy(isDateSectionExpanded = !isDateSectionExpanded) }
            BoardAction.OpenStartDatePicker -> update { copy(datePickerTarget = DatePickerTarget.START) }
            BoardAction.OpenDueDatePicker -> update { copy(datePickerTarget = DatePickerTarget.DUE) }
            BoardAction.CloseDatePicker -> update { copy(datePickerTarget = null) }
            is BoardAction.SetStartDate -> updateEditor {
                val invalid = action.dateMillis != null && dueDateMillis != null && action.dateMillis > dueDateMillis
                if (invalid) copy(validationMessage = "起始日期不能晚于截止日期")
                else copy(startDateMillis = action.dateMillis, validationMessage = null)
            }
            is BoardAction.SetDueDate -> updateEditor {
                val invalid = action.dateMillis != null && startDateMillis != null && action.dateMillis < startDateMillis
                if (invalid) copy(validationMessage = "截止日期不能早于起始日期")
                else copy(dueDateMillis = action.dateMillis, validationMessage = null)
            }
            BoardAction.OpenTimePicker -> update { copy(showTimePicker = true) }
            BoardAction.CloseTimePicker -> update { copy(showTimePicker = false) }
            is BoardAction.SetReminderTime -> updateEditor { copy(reminderHour = action.hour, reminderMinute = action.minute, validationMessage = null) }
            BoardAction.ClearReminderTime -> updateEditor { copy(reminderHour = null, reminderMinute = null, reminderRepeat = ReminderRepeat.NONE) }
            BoardAction.OpenRepeatPicker -> update { if (editor?.hasReminder == true) copy(showRepeatPicker = true) else this }
            BoardAction.CloseRepeatPicker -> update { copy(showRepeatPicker = false) }
            is BoardAction.SetReminderRepeat -> { updateEditor { copy(reminderRepeat = action.repeat) }; update { copy(showRepeatPicker = false) } }
            BoardAction.SaveEditor -> saveEditor()
            BoardAction.RequestCloseEditor -> requestCloseEditor()
            BoardAction.KeepEditing -> update { copy(showDiscardConfirmation = false) }
            BoardAction.ConfirmDiscardEditor -> update { copy(editor = null, showTimePicker = false, showRepeatPicker = false, datePickerTarget = null, showDiscardConfirmation = false) }
            is BoardAction.RequestMove -> update { copy(operationConfirmation = OperationConfirmation.Move(action.task, action.status), expandedTaskId = null) }
            is BoardAction.MoveImmediately -> runTaskMutation(action.task.id) { repository.changeStatus(action.task.id, action.status) }
            is BoardAction.QuickComplete -> quickComplete(action.task)
            BoardAction.RefreshBoard -> update { copy(transientCompletedTasks = emptyMap(), expandedTaskId = null) }
            is BoardAction.RequestDelete -> update { copy(operationConfirmation = OperationConfirmation.Delete(action.task), expandedTaskId = null) }
            is BoardAction.RequestPermanentDelete -> update {
                if (action.task.id in busyTaskIds) this
                else copy(operationConfirmation = OperationConfirmation.PermanentDelete(action.task))
            }
            BoardAction.ConfirmOperation -> confirmOperation()
            BoardAction.CancelOperation -> update { copy(operationConfirmation = null) }
            BoardAction.HighlightConsumed -> update { copy(highlightRequest = null) }
            is BoardAction.DismissServerDeletionNotice -> viewModelScope.launch { repository.dismissServerDeletionNotice(action.taskId) }
        }
    }

    private fun saveEditor() {
        val ui = presentation.value
        val editor = ui.editor ?: return
        if (ui.isEditorSaving) return
        val content = editor.content.trim()
        if (content.isEmpty()) { updateEditor { copy(validationMessage = "请输入任务内容") }; return }
        val trigger = if (editor.hasReminder) ReminderScheduleCalculator.firstTriggerAt(
            nowMillis(), checkNotNull(editor.reminderHour), checkNotNull(editor.reminderMinute), editor.reminderRepeat, zoneId,
        ) else null
        val effectiveRepeat = if (trigger == null) ReminderRepeat.NONE else editor.reminderRepeat
        if (trigger != null) mutableEffects.tryEmit(BoardEffect.RequestReminderPermissions)
        update { copy(editor = null, showTimePicker = false, showRepeatPicker = false, datePickerTarget = null, isEditorSaving = true) }
        viewModelScope.launch {
            try {
                if (editor.taskId == null) {
                    repository.createTask(
                        content,
                        trigger,
                        effectiveRepeat,
                        editor.startDateMillis,
                        editor.dueDateMillis,
                    )
                    update { copy(selectedStatus = TaskStatus.TODO, todoScrollToEndGeneration = todoScrollToEndGeneration + 1) }
                } else repository.updateTask(
                    editor.taskId,
                    content,
                    trigger,
                    effectiveRepeat,
                    editor.startDateMillis,
                    editor.dueDateMillis,
                )
            } finally { update { copy(isEditorSaving = false) } }
        }
    }

    private fun requestCloseEditor() {
        val editor = presentation.value.editor ?: return
        if (editor.isDirty()) update { copy(showDiscardConfirmation = true) }
        else update { copy(editor = null, showTimePicker = false, showRepeatPicker = false, datePickerTarget = null) }
    }

    private fun confirmOperation() {
        val value = presentation.value.operationConfirmation ?: return
        update { copy(operationConfirmation = null) }
        when (value) {
            is OperationConfirmation.Delete -> runTaskMutation(value.task.id) { repository.deleteTask(value.task.id) }
            is OperationConfirmation.PermanentDelete -> runTaskMutation(value.task.id) {
                repository.permanentlyDeleteTask(value.task.id)
            }
            is OperationConfirmation.PermanentDeleteMany -> runRecycleBinBatchPermanentDelete(value.taskIds)
            is OperationConfirmation.Move -> runTaskMutation(value.task.id) { repository.changeStatus(value.task.id, value.destination) }
            is OperationConfirmation.BatchDelete -> runBatch(value.taskIds) { repository.deleteTask(it) }
        }
    }

    private fun runBatchMove(status: TaskStatus) {
        val ids = presentation.value.selectedTaskIds
        if (ids.isNotEmpty()) runBatch(ids) { repository.changeStatus(it, status) }
    }

    private fun runRecycleBinBatchPermanentDelete(ids: Set<String>) {
        if (presentation.value.isRecycleBinOperationRunning || ids.isEmpty()) return
        update { copy(isRecycleBinOperationRunning = true, selectedDeletedTaskIds = emptySet()) }
        viewModelScope.launch {
            try { ids.forEach { repository.permanentlyDeleteTask(it) } }
            finally {
                update {
                    copy(
                        isRecycleBinOperationRunning = false,
                        isRecycleBinBatchEditing = false,
                        selectedDeletedTaskIds = emptySet(),
                    )
                }
            }
        }
    }

    private fun runBatch(ids: Set<String>, operation: suspend (String) -> Unit) {
        if (presentation.value.isBatchOperationRunning || ids.isEmpty()) return
        update { copy(isBatchOperationRunning = true, selectedTaskIds = emptySet()) }
        viewModelScope.launch {
            try { ids.forEach { operation(it) } }
            finally { update { copy(isBatchOperationRunning = false, isBatchEditing = false) } }
        }
    }

    private fun runTaskMutation(id: String, operation: suspend () -> Unit) {
        if (id in presentation.value.busyTaskIds) return
        update { copy(busyTaskIds = busyTaskIds + id, expandedTaskId = null) }
        viewModelScope.launch { try { operation() } finally { update { copy(busyTaskIds = busyTaskIds - id) } } }
    }

    private fun quickComplete(task: Task) {
        val ui = presentation.value
        if (task.status == TaskStatus.DONE || task.id in ui.busyTaskIds || task.id in ui.transientCompletedTasks) return
        update {
            copy(
                transientCompletedTasks = transientCompletedTasks + (task.id to task),
                busyTaskIds = busyTaskIds + task.id,
                expandedTaskId = null,
            )
        }
        viewModelScope.launch {
            try {
                repository.changeStatus(task.id, TaskStatus.DONE)
            } catch (_: Exception) {
                update { copy(transientCompletedTasks = transientCompletedTasks - task.id) }
            } finally {
                update { copy(busyTaskIds = busyTaskIds - task.id) }
            }
        }
    }
    private fun updateEditor(transform: TaskEditorState.() -> TaskEditorState) = update { copy(editor = editor?.transform()) }
    private fun update(transform: BoardPresentationState.() -> BoardPresentationState) = presentation.update(transform)
}

private data class BoardPresentationState(
    val selectedStatus: TaskStatus = TaskStatus.TODO, val isSearching: Boolean = false, val searchQuery: String = "",
    val isDarkMode: Boolean = false, val isMainMenuOpen: Boolean = false, val expandedTaskId: String? = null,
    val busyTaskIds: Set<String> = emptySet(), val editor: TaskEditorState? = null,
    val showTimePicker: Boolean = false, val showRepeatPicker: Boolean = false, val showDiscardConfirmation: Boolean = false,
    val operationConfirmation: OperationConfirmation? = null, val highlightRequest: HighlightRequest? = null,
    val highlightGeneration: Long = 0, val todoScrollToEndGeneration: Long = 0, val isEditorSaving: Boolean = false,
    val isBatchEditing: Boolean = false, val selectedTaskIds: Set<String> = emptySet(), val isBatchOperationRunning: Boolean = false,
    val isRecycleBinOpen: Boolean = false,
    val isRecycleBinBatchEditing: Boolean = false,
    val selectedDeletedTaskIds: Set<String> = emptySet(),
    val isRecycleBinOperationRunning: Boolean = false,
    val datePickerTarget: DatePickerTarget? = null,
    val transientCompletedTasks: Map<String, Task> = emptyMap(),
)

private enum class DatePickerTarget { START, DUE }

private fun List<Task>.toBoardUiState(deletedTasks: List<Task>, notice: ServerDeletionNotice?, ui: BoardPresentationState): BoardUiState {
    val todo = tasksFor(TaskStatus.TODO).withTransientCompleted(ui.transientCompletedTasks, TaskStatus.TODO)
    val doing = tasksFor(TaskStatus.DOING).withTransientCompleted(ui.transientCompletedTasks, TaskStatus.DOING)
    val done = tasksFor(TaskStatus.DONE)
    val query = ui.searchQuery.trim()
    val current = when (ui.selectedStatus) { TaskStatus.TODO -> todo; TaskStatus.DOING -> doing; TaskStatus.DONE -> done }.mapTo(mutableSetOf(), Task::id)
    return BoardUiState(
        todo = todo,
        doing = doing,
        done = done,
        deletedTasks = deletedTasks,
        serverDeletionNotice = notice,
        selectedStatus = ui.selectedStatus,
        isSearching = ui.isSearching,
        searchQuery = ui.searchQuery,
        searchResults = if (query.isEmpty()) emptyList() else filter { it.title.contains(query, true) },
        isDarkMode = ui.isDarkMode,
        isMainMenuOpen = ui.isMainMenuOpen,
        expandedTaskId = ui.expandedTaskId,
        busyTaskIds = ui.busyTaskIds,
        editor = ui.editor,
        showTimePicker = ui.showTimePicker,
        showRepeatPicker = ui.showRepeatPicker,
        showDiscardConfirmation = ui.showDiscardConfirmation,
        operationConfirmation = ui.operationConfirmation,
        highlightRequest = ui.highlightRequest,
        todoScrollToEndGeneration = ui.todoScrollToEndGeneration,
        isBatchEditing = ui.isBatchEditing,
        selectedTaskIds = ui.selectedTaskIds.intersect(current),
        isBatchOperationRunning = ui.isBatchOperationRunning,
        isRecycleBinOpen = ui.isRecycleBinOpen,
        isRecycleBinBatchEditing = ui.isRecycleBinBatchEditing,
        selectedDeletedTaskIds = ui.selectedDeletedTaskIds.intersect(deletedTasks.mapTo(mutableSetOf(), Task::id)),
        isRecycleBinOperationRunning = ui.isRecycleBinOperationRunning,
        transientCompletedTaskIds = ui.transientCompletedTasks.keys,
        showStartDatePicker = ui.datePickerTarget == DatePickerTarget.START,
        showDueDatePicker = ui.datePickerTarget == DatePickerTarget.DUE,
    )
}
private fun List<Task>.tasksFor(status: TaskStatus) = filter { it.status == status }.sortedByDescending(Task::isPinned)
private fun List<Task>.withTransientCompleted(snapshots: Map<String, Task>, status: TaskStatus): List<Task> {
    val completedSnapshots = snapshots.values.filter { it.status == status }
    val completedIds = completedSnapshots.mapTo(mutableSetOf(), Task::id)
    return completedSnapshots + filterNot { it.id in completedIds }
}
private fun BoardUiState.tasksFor(status: TaskStatus) = when (status) { TaskStatus.TODO -> todo; TaskStatus.DOING -> doing; TaskStatus.DONE -> done }
private fun Task.toEditorState(zone: ZoneId): TaskEditorState {
    val time = reminderAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    return TaskEditorState(
        taskId = id,
        content = title,
        reminderHour = time?.hour,
        reminderMinute = time?.minute,
        reminderRepeat = reminderRepeat,
        startDateMillis = startDateMillis,
        dueDateMillis = dueDateMillis,
        isDateSectionExpanded = startDateMillis != null || dueDateMillis != null,
        initialContent = title,
        initialReminderHour = time?.hour,
        initialReminderMinute = time?.minute,
        initialReminderRepeat = reminderRepeat,
        initialStartDateMillis = startDateMillis,
        initialDueDateMillis = dueDateMillis,
    )
}
private fun TaskEditorState.isDirty() = content != initialContent || reminderHour != initialReminderHour ||
    reminderMinute != initialReminderMinute || reminderRepeat != initialReminderRepeat ||
    startDateMillis != initialStartDateMillis || dueDateMillis != initialDueDateMillis
private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
