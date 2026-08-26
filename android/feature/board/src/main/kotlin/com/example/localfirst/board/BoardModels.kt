package com.example.localfirst.board

import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus

data class BoardUiState(
    val todo: List<Task> = emptyList(), val doing: List<Task> = emptyList(), val done: List<Task> = emptyList(),
    val deletedTasks: List<Task> = emptyList(),
    val serverDeletionNotice: ServerDeletionNotice? = null, val selectedStatus: TaskStatus = TaskStatus.TODO,
    val isSearching: Boolean = false, val searchQuery: String = "", val searchResults: List<Task> = emptyList(),
    val isDarkMode: Boolean = false, val isMainMenuOpen: Boolean = false,
    val expandedTaskId: String? = null, val busyTaskIds: Set<String> = emptySet(),
    val editor: TaskEditorState? = null, val showTimePicker: Boolean = false, val showRepeatPicker: Boolean = false,
    val showDiscardConfirmation: Boolean = false, val operationConfirmation: OperationConfirmation? = null,
    val highlightRequest: HighlightRequest? = null, val todoScrollToEndGeneration: Long = 0,
    val isBatchEditing: Boolean = false, val selectedTaskIds: Set<String> = emptySet(),
    val isBatchOperationRunning: Boolean = false,
    val isRecycleBinOpen: Boolean = false,
    val isRecycleBinBatchEditing: Boolean = false,
    val selectedDeletedTaskIds: Set<String> = emptySet(),
    val isRecycleBinOperationRunning: Boolean = false,
    val transientCompletedTaskIds: Set<String> = emptySet(),
    val showStartDatePicker: Boolean = false,
    val showDueDatePicker: Boolean = false,
)

data class TaskEditorState(
    val taskId: String? = null, val content: String = "",
    val reminderHour: Int? = null, val reminderMinute: Int? = null,
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val startDateMillis: Long? = null, val dueDateMillis: Long? = null,
    val isDateSectionExpanded: Boolean = false,
    val initialContent: String = "", val initialReminderHour: Int? = null,
    val initialReminderMinute: Int? = null, val initialReminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val initialStartDateMillis: Long? = null, val initialDueDateMillis: Long? = null,
    val validationMessage: String? = null,
) {
    val isEditing: Boolean get() = taskId != null
    val hasReminder: Boolean get() = reminderHour != null && reminderMinute != null
}

data class HighlightRequest(val taskId: String, val status: TaskStatus, val generation: Long)

sealed interface OperationConfirmation {
    data class Delete(val task: Task) : OperationConfirmation
    data class PermanentDelete(val task: Task) : OperationConfirmation
    data class PermanentDeleteMany(val taskIds: Set<String>) : OperationConfirmation
    data class Move(val task: Task, val destination: TaskStatus) : OperationConfirmation
    data class BatchDelete(val taskIds: Set<String>) : OperationConfirmation
}

sealed interface BoardEffect { data object RequestReminderPermissions : BoardEffect }

sealed interface BoardAction {
    data class SelectStatus(val status: TaskStatus) : BoardAction
    data object StartSearch : BoardAction
    data class UpdateSearch(val query: String) : BoardAction
    data object ExitSearch : BoardAction
    data class SelectSearchResult(val task: Task) : BoardAction
    data object ToggleMainMenu : BoardAction
    data object CloseMainMenu : BoardAction
    data object ToggleTheme : BoardAction
    data object OpenRecycleBin : BoardAction
    data object CloseRecycleBin : BoardAction
    data object StartRecycleBinBatchEdit : BoardAction
    data object CancelRecycleBinBatchEdit : BoardAction
    data class ToggleDeletedTaskSelection(val taskId: String) : BoardAction
    data object ToggleSelectAllDeletedTasks : BoardAction
    data object RequestPermanentDeleteSelected : BoardAction
    data object StartBatchEdit : BoardAction
    data object CancelBatchEdit : BoardAction
    data class ToggleBatchSelection(val taskId: String) : BoardAction
    data object ToggleSelectAll : BoardAction
    data object RequestBatchDelete : BoardAction
    data class BatchMove(val status: TaskStatus) : BoardAction
    data class ToggleTaskActions(val taskId: String) : BoardAction
    data object CloseTaskActions : BoardAction
    data class SetPinned(val task: Task, val isPinned: Boolean) : BoardAction
    data object OpenCreate : BoardAction
    data class OpenEdit(val task: Task) : BoardAction
    data class UpdateEditorContent(val content: String) : BoardAction
    data object ToggleDateSection : BoardAction
    data object OpenStartDatePicker : BoardAction
    data object OpenDueDatePicker : BoardAction
    data object CloseDatePicker : BoardAction
    data class SetStartDate(val dateMillis: Long?) : BoardAction
    data class SetDueDate(val dateMillis: Long?) : BoardAction
    data object OpenTimePicker : BoardAction
    data object CloseTimePicker : BoardAction
    data class SetReminderTime(val hour: Int, val minute: Int) : BoardAction
    data object ClearReminderTime : BoardAction
    data object OpenRepeatPicker : BoardAction
    data object CloseRepeatPicker : BoardAction
    data class SetReminderRepeat(val repeat: ReminderRepeat) : BoardAction
    data object SaveEditor : BoardAction
    data object RequestCloseEditor : BoardAction
    data object KeepEditing : BoardAction
    data object ConfirmDiscardEditor : BoardAction
    data class RequestMove(val task: Task, val status: TaskStatus) : BoardAction
    data class MoveImmediately(val task: Task, val status: TaskStatus) : BoardAction
    data class QuickComplete(val task: Task) : BoardAction
    data object RefreshBoard : BoardAction
    data class RequestDelete(val task: Task) : BoardAction
    data class RequestPermanentDelete(val task: Task) : BoardAction
    data object ConfirmOperation : BoardAction
    data object CancelOperation : BoardAction
    data object HighlightConsumed : BoardAction
    data class DismissServerDeletionNotice(val taskId: String) : BoardAction
}
