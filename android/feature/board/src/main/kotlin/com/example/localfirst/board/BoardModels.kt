package com.example.localfirst.board

import com.example.localfirst.data.Task
import com.example.localfirst.sync.TaskStatus

data class BoardUiState(
    val todo: List<Task> = emptyList(),
    val doing: List<Task> = emptyList(),
    val done: List<Task> = emptyList(),
)

sealed interface BoardAction {
    data class CreateTask(val title: String) : BoardAction

    data class RenameTask(
        val taskId: String,
        val title: String,
    ) : BoardAction

    data class MoveTask(
        val taskId: String,
        val status: TaskStatus,
    ) : BoardAction

    data class DeleteTask(val taskId: String) : BoardAction
}
