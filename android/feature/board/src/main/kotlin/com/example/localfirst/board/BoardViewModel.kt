package com.example.localfirst.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localfirst.data.ServerDeletionNotice
import com.example.localfirst.data.Task
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardViewModel(
    private val repository: TaskRepository,
) : ViewModel() {
    val state: StateFlow<BoardUiState> = repository.tasks
        .combine(repository.serverDeletionNotices) { tasks, notices ->
            tasks.toBoardUiState(notices.firstOrNull())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BoardUiState(),
        )

    fun onAction(action: BoardAction) {
        viewModelScope.launch {
            when (action) {
                is BoardAction.CreateTask -> repository.createTask(action.title)
                is BoardAction.RenameTask -> repository.updateTitle(action.taskId, action.title)
                is BoardAction.MoveTask -> repository.changeStatus(action.taskId, action.status)
                is BoardAction.DeleteTask -> repository.deleteTask(action.taskId)
                is BoardAction.DismissServerDeletionNotice ->
                    repository.dismissServerDeletionNotice(action.taskId)
            }
        }
    }
}

private fun List<Task>.toBoardUiState(
    serverDeletionNotice: ServerDeletionNotice?,
) = BoardUiState(
    todo = filter { it.status == TaskStatus.TODO },
    doing = filter { it.status == TaskStatus.DOING },
    done = filter { it.status == TaskStatus.DONE },
    serverDeletionNotice = serverDeletionNotice,
)
