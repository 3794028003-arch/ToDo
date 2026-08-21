package com.example.localfirst.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localfirst.data.Task
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BoardViewModel(
    private val repository: TaskRepository,
) : ViewModel() {
    val state: StateFlow<BoardUiState> = repository.tasks
        .map { tasks -> tasks.toBoardUiState() }
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
            }
        }
    }
}

private fun List<Task>.toBoardUiState() = BoardUiState(
    todo = filter { it.status == TaskStatus.TODO },
    doing = filter { it.status == TaskStatus.DOING },
    done = filter { it.status == TaskStatus.DONE },
)
