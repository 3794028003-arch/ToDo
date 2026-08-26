package com.example.localfirst.app

import androidx.lifecycle.ViewModel
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ReminderAlert(val taskId: String, val title: String)

data class ReminderQueueState(
    val reminders: List<ReminderAlert> = emptyList(),
) {
    val current: ReminderAlert? get() = reminders.firstOrNull()
    val pendingCount: Int get() = reminders.size
}

class ReminderQueueStore {
    private val mutableState = MutableStateFlow(ReminderQueueState())
    val state: StateFlow<ReminderQueueState> = mutableState

    @Synchronized
    fun enqueue(reminder: ReminderAlert) {
        val reminders = mutableState.value.reminders
        val existingIndex = reminders.indexOfFirst { it.taskId == reminder.taskId }
        val updated = if (existingIndex >= 0) {
            reminders.toMutableList().apply { this[existingIndex] = reminder }
        } else {
            reminders + reminder
        }
        mutableState.value = ReminderQueueState(updated)
    }

    @Synchronized
    fun removeCurrent(taskId: String): ReminderAlert? {
        val reminders = mutableState.value.reminders
        val current = reminders.firstOrNull()?.takeIf { it.taskId == taskId } ?: return null
        mutableState.value = ReminderQueueState(reminders.drop(1))
        return current
    }

    @Synchronized
    fun remove(taskId: String) {
        val reminders = mutableState.value.reminders
        val updated = reminders.filterNot { it.taskId == taskId }
        if (updated.size != reminders.size) mutableState.value = ReminderQueueState(updated)
    }

    @Synchronized
    fun clear() {
        mutableState.value = ReminderQueueState()
    }
}

class ReminderViewModel(
    private val store: ReminderQueueStore,
    private val onMove: (taskId: String, status: TaskStatus) -> Unit,
) : ViewModel() {
    val state: StateFlow<ReminderQueueState> = store.state

    fun dismiss(taskId: String) {
        store.removeCurrent(taskId)
    }

    fun move(taskId: String, status: TaskStatus) {
        val reminder = store.removeCurrent(taskId) ?: return
        onMove(reminder.taskId, status)
    }
}
