package com.example.localfirst.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localfirst.data.Task
import com.example.localfirst.data.ReminderRepeat
import com.example.localfirst.data.TaskReminderScheduler
import com.example.localfirst.sync.OperationState
import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTaskRepositoryTest {
    private lateinit var fixture: RoomDatabaseTestFixture
    private lateinit var database: TaskDatabase

    @Before
    fun setUp() {
        fixture = RoomDatabaseTestFixture()
        fixture.deleteDatabase()
        database = fixture.open()
    }

    @After
    fun tearDown() {
        database.close()
        fixture.deleteDatabase()
    }

    @Test
    fun titleUpdateIsImmediatelyObservableAndAtomicallyEnqueuesUpdate() = runTest {
        database.taskDao().upsert(
            taskEntity("task-update", TaskStatus.TODO, serverVersion = 4),
        )
        var scheduleCalls = 0
        val repository = repository(
            operationIds = ArrayDeque(listOf("update-operation")),
            scheduleSync = { scheduleCalls += 1 },
        )

        repository.updateTask("task-update", "Edited offline", null)

        val visibleTask = repository.tasks.first().single()
        val storedTask = database.taskDao().findById("task-update")
        val operation = database.syncOperationDao().all().single()
        assertEquals("Edited offline", visibleTask.title)
        assertEquals("Edited offline", storedTask?.title)
        assertEquals(2L, storedTask?.localRevision)
        assertEquals(OperationType.UPDATE, operation.type)
        assertEquals("Edited offline", operation.title)
        assertEquals(4L, operation.baseServerVersion)
        assertEquals(2L, operation.taskRevision)
        assertEquals(OperationState.PENDING, operation.state)
        assertEquals(1, scheduleCalls)
    }

    @Test
    fun deleteImmediatelyHidesTaskAndEnqueuesVersionChainedTombstone() = runTest {
        database.taskDao().upsert(
            taskEntity("task-delete", TaskStatus.DOING, serverVersion = 4).copy(
                title = "Edited offline",
                localRevision = 2,
            ),
        )
        database.syncOperationDao().insert(
            operationEntity(
                operationId = "update-operation",
                taskId = "task-delete",
                queueSequence = 10,
                type = OperationType.UPDATE,
            ).copy(
                taskRevision = 2,
                title = "Edited offline",
                desiredStatus = null,
                baseServerVersion = 4,
            ),
        )
        var scheduleCalls = 0
        val repository = repository(
            operationIds = ArrayDeque(listOf("delete-operation")),
            scheduleSync = { scheduleCalls += 1 },
        )

        repository.deleteTask("task-delete")

        assertEquals(emptyList<Task>(), repository.tasks.first())
        val storedTask = database.taskDao().findById("task-delete")
        val operation = database.syncOperationDao().findById("delete-operation")
        assertEquals(5_000L, storedTask?.deletedAtMillis)
        assertEquals(3L, storedTask?.localRevision)
        assertEquals(OperationType.DELETE, operation?.type)
        assertEquals(5L, operation?.baseServerVersion)
        assertEquals(11L, operation?.queueSequence)
        assertEquals(3L, operation?.taskRevision)
        assertEquals("update-operation", operation?.predecessorOperationId)
        assertNull(operation?.title)
        assertNull(operation?.desiredStatus)
        assertEquals(1, scheduleCalls)
    }

    @Test
    fun permanentDeleteHidesTrashImmediatelyAndPurgesAfterDeleteSyncs() = runTest {
        database.taskDao().upsert(
            taskEntity("task-purge", TaskStatus.TODO, serverVersion = 4),
        )
        val repository = repository(
            operationIds = ArrayDeque(listOf("delete-operation")),
            scheduleSync = {},
        )

        repository.deleteTask("task-purge")
        assertEquals(listOf("task-purge"), repository.deletedTasks.first().map(Task::id))

        repository.permanentlyDeleteTask("task-purge")

        assertEquals(emptyList<Task>(), repository.deletedTasks.first())
        assertEquals(true, database.taskDao().findById("task-purge")?.permanentDeletionRequested)
        assertEquals(1, database.syncOperationDao().all().size)

        RoomSyncStore(database).markSynced("delete-operation", 5)

        assertNull(database.taskDao().findById("task-purge"))
        assertEquals(emptyList<SyncOperationEntity>(), database.syncOperationDao().all())
    }

    @Test
    fun schedulerFailureDoesNotUndoOrFailAnAlreadyCommittedLocalUpdate() = runTest {
        database.taskDao().upsert(
            taskEntity("task-safe", TaskStatus.TODO, serverVersion = 2),
        )
        val repository = repository(
            operationIds = ArrayDeque(listOf("safe-operation")),
            scheduleSync = { error("WorkManager unavailable") },
        )

        repository.updateTask("task-safe", "Still succeeds locally", null)

        assertEquals(
            "Still succeeds locally",
            database.taskDao().findById("task-safe")?.title,
        )
        assertEquals(
            OperationState.PENDING,
            database.syncOperationDao().findById("safe-operation")?.state,
        )
    }

    @Test
    fun reminderIsScheduledUpdatedAndCancelledWhenTaskCompletes() = runTest {
        val reminders = RecordingReminderScheduler()
        val repository = repository(
            operationIds = ArrayDeque(listOf("create", "update", "done")),
            scheduleSync = {},
            reminderScheduler = reminders,
        )

        repository.createTask("Reminder task", 10_000L)
        repository.updateTask("generated-task", "Updated reminder", 20_000L)
        repository.changeStatus("generated-task", TaskStatus.DONE)

        assertEquals(
            listOf(
                "schedule:generated-task:10000",
                "schedule:generated-task:20000",
                "cancel:generated-task",
            ),
            reminders.calls,
        )
        assertNull(database.taskDao().findById("generated-task")?.reminderAtMillis)
    }

    @Test
    fun completingRecurringTaskKeepsHistoryAndAtomicallyCreatesOnlyOneNextTodo() = runTest {
        database.taskDao().upsert(
            taskEntity("recurring", TaskStatus.DOING, serverVersion = 3).copy(
                title = "每日复盘",
                reminderAtMillis = 10_000L,
                reminderRepeat = ReminderRepeat.DAILY,
                isPinned = true,
            ),
        )
        val reminders = RecordingReminderScheduler()
        val repository = RoomTaskRepository(
            database = database,
            taskIdFactory = { "next-recurring" },
            operationIdFactory = ArrayDeque(listOf("complete", "create-next"))::removeFirst,
            nowMillis = { 5_000L },
            scheduleSync = {},
            reminderScheduler = reminders,
        )

        repeat(20) { repository.changeStatus("recurring", TaskStatus.DONE) }

        val visible = repository.tasks.first()
        val completed = visible.single { it.id == "recurring" }
        val next = visible.single { it.id == "next-recurring" }
        assertEquals(TaskStatus.DONE, completed.status)
        assertNull(completed.reminderAtMillis)
        assertEquals(TaskStatus.TODO, next.status)
        assertEquals("每日复盘", next.title)
        assertEquals(ReminderRepeat.DAILY, next.reminderRepeat)
        assertEquals(86_410_000L, next.reminderAtMillis)
        assertEquals(true, next.isPinned)
        assertEquals(2, database.syncOperationDao().all().size)
        assertEquals(
            listOf("cancel:recurring", "schedule:next-recurring:86410000"),
            reminders.calls,
        )
    }

    @Test
    fun tasksStartInCreationOrderThenMostRecentlyEditedTaskComesFirst() = runTest {
        val mutations = RoomTaskMutationStore(database)
        val repository = repository(
            operationIds = ArrayDeque(),
            scheduleSync = {},
        )

        mutations.createTaskAndEnqueue("task-3", "First", "create-3", queueSequence = 1)
        mutations.createTaskAndEnqueue("task-1", "Second", "create-1", queueSequence = 2)
        mutations.createTaskAndEnqueue("task-2", "Third", "create-2", queueSequence = 3)

        assertEquals(
            listOf("task-3", "task-1", "task-2"),
            repository.tasks.first().map(Task::id),
        )

        mutations.updateTaskAndEnqueue(
            taskId = "task-1",
            title = "Second edited",
            reminderAtMillis = null,
            operationId = "update-1",
            queueSequence = 4,
        )

        assertEquals(
            listOf("task-1", "task-3", "task-2"),
            repository.tasks.first().map(Task::id),
        )

        mutations.changeStatusAndEnqueue(
            taskId = "task-2",
            status = TaskStatus.DONE,
            operationId = "status-2",
            queueSequence = 5,
        )

        assertEquals(
            listOf("task-2", "task-1", "task-3"),
            repository.tasks.first().map(Task::id),
        )

        RoomSyncStore(database).markSynced("status-2", serverVersion = 8)

        assertEquals(
            listOf("task-2", "task-1", "task-3"),
            repository.tasks.first().map(Task::id),
        )
    }

    @Test
    fun pinnedTaskSortsFirstAndUpdateOperationCarriesPinState() = runTest {
        database.taskDao().upsert(taskEntity("first", TaskStatus.TODO, serverVersion = 1))
        database.taskDao().upsert(taskEntity("second", TaskStatus.TODO, serverVersion = 1))
        val repository = repository(
            operationIds = ArrayDeque(listOf("pin-second")),
            scheduleSync = {},
        )

        repository.setPinned("second", true)

        assertEquals(listOf("second", "first"), repository.tasks.first().map(Task::id))
        assertEquals(true, database.taskDao().findById("second")?.isPinned)
        val operation = database.syncOperationDao().findById("pin-second")
        assertEquals(OperationType.UPDATE, operation?.type)
        assertEquals(true, operation?.isPinned)
        assertEquals("Task second", operation?.title)
    }

    private fun repository(
        operationIds: ArrayDeque<String>,
        scheduleSync: () -> Unit,
        reminderScheduler: TaskReminderScheduler? = null,
    ): RoomTaskRepository = RoomTaskRepository(
        database = database,
        taskIdFactory = { "generated-task" },
        operationIdFactory = operationIds::removeFirst,
        nowMillis = { 5_000L },
        scheduleSync = scheduleSync,
        reminderScheduler = reminderScheduler,
    )
}

private class RecordingReminderScheduler : TaskReminderScheduler {
    val calls = mutableListOf<String>()

    override fun schedule(
        taskId: String,
        title: String,
        reminderAtMillis: Long,
        reminderRepeat: com.example.localfirst.data.ReminderRepeat,
    ) {
        calls += "schedule:$taskId:$reminderAtMillis"
    }

    override fun cancel(taskId: String) {
        calls += "cancel:$taskId"
    }
}
