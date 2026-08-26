package com.example.localfirst.app

import com.example.localfirst.sync.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderViewModelTest {
    @Test
    fun `simultaneous reminders are queued in arrival order`() {
        val store = ReminderQueueStore()

        store.enqueue(ReminderAlert("todo", "TODO task"))
        store.enqueue(ReminderAlert("doing", "DOING task"))
        store.enqueue(ReminderAlert("done", "DONE task"))

        assertEquals("todo", store.state.value.current?.taskId)
        assertEquals(3, store.state.value.pendingCount)
    }

    @Test
    fun `duplicate task updates its title without creating another queue item`() {
        val store = ReminderQueueStore()

        store.enqueue(ReminderAlert("same", "Old title"))
        store.enqueue(ReminderAlert("same", "New title"))

        assertEquals("New title", store.state.value.current?.title)
        assertEquals(1, store.state.value.pendingCount)
    }

    @Test
    fun `handling current reminder advances to the next one`() {
        val actions = mutableListOf<Pair<String, TaskStatus>>()
        val store = ReminderQueueStore().apply {
            enqueue(ReminderAlert("todo", "TODO task"))
            enqueue(ReminderAlert("doing", "DOING task"))
        }
        val viewModel = ReminderViewModel(store) { taskId, status -> actions += taskId to status }

        viewModel.move("todo", TaskStatus.DONE)

        assertEquals(listOf("todo" to TaskStatus.DONE), actions)
        assertEquals("doing", viewModel.state.value.current?.taskId)
        assertEquals(1, viewModel.state.value.pendingCount)
    }

    @Test
    fun `rapid repeated action for old reminder cannot affect next reminder`() {
        val actions = mutableListOf<Pair<String, TaskStatus>>()
        val store = ReminderQueueStore().apply {
            enqueue(ReminderAlert("first", "First"))
            enqueue(ReminderAlert("second", "Second"))
        }
        val viewModel = ReminderViewModel(store) { taskId, status -> actions += taskId to status }

        repeat(20) { viewModel.move("first", TaskStatus.DONE) }

        assertEquals(listOf("first" to TaskStatus.DONE), actions)
        assertEquals("second", viewModel.state.value.current?.taskId)
    }

    @Test
    fun `dismiss requires the currently displayed task id`() {
        val store = ReminderQueueStore().apply {
            enqueue(ReminderAlert("first", "First"))
            enqueue(ReminderAlert("second", "Second"))
        }
        val viewModel = ReminderViewModel(store) { _, _ -> }

        repeat(20) { viewModel.dismiss("first") }
        assertEquals("second", viewModel.state.value.current?.taskId)

        viewModel.dismiss("second")
        assertNull(viewModel.state.value.current)
        assertEquals(0, viewModel.state.value.pendingCount)
    }

    @Test
    fun `system notification action can remove a matching queued reminder`() {
        val store = ReminderQueueStore().apply {
            enqueue(ReminderAlert("todo", "TODO task"))
            enqueue(ReminderAlert("doing", "DOING task"))
            enqueue(ReminderAlert("done", "DONE task"))
        }

        store.remove("doing")

        assertEquals(listOf("todo", "done"), store.state.value.reminders.map(ReminderAlert::taskId))
    }
}
