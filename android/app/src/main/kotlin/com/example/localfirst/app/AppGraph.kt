package com.example.localfirst.app

import android.content.Context
import android.app.NotificationManager
import androidx.room.Room
import androidx.work.WorkManager
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.database.RoomSyncStore
import com.example.localfirst.database.RoomTaskRepository
import com.example.localfirst.database.TaskDatabase
import com.example.localfirst.database.MIGRATION_1_2
import com.example.localfirst.database.MIGRATION_2_3
import com.example.localfirst.database.MIGRATION_3_4
import com.example.localfirst.database.MIGRATION_4_5
import com.example.localfirst.database.MIGRATION_5_6
import com.example.localfirst.database.MIGRATION_6_7
import com.example.localfirst.database.MIGRATION_7_8
import com.example.localfirst.database.MIGRATION_8_9
import com.example.localfirst.network.RetrofitSyncApi
import com.example.localfirst.sync.RetryPolicy
import com.example.localfirst.sync.SyncClock
import com.example.localfirst.sync.SyncEngine
import com.example.localfirst.sync.TaskStatus
import com.example.localfirst.work.SyncRunOutcome
import com.example.localfirst.work.SyncWorkRunner
import com.example.localfirst.work.SyncWorkScheduler
import com.example.localfirst.work.SyncWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppGraph(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reminderScheduler by lazy { AlarmTaskReminderScheduler(applicationContext) }
    val reminderQueueStore = ReminderQueueStore()
    @Volatile var isForeground: Boolean = false
        private set

    private val database: TaskDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        ).build()
    }

    private val syncStore by lazy { RoomSyncStore(database) }
    private val clock = SyncClock(System::currentTimeMillis)
    private val retryPolicy = RetryPolicy(::retryDelayMillis)
    private val syncEngine by lazy {
        SyncEngine(
            store = syncStore,
            api = RetrofitSyncApi.create(BuildConfig.SYNC_BASE_URL),
            clock = clock,
            retryPolicy = retryPolicy,
        )
    }
    private val scheduler by lazy {
        SyncWorkScheduler(WorkManager.getInstance(applicationContext))
    }

    val workerFactory = SyncWorkerFactory(
        SyncWorkRunner {
            syncEngine.drain()
            if (syncStore.allOperations().any { operation -> !operation.state.isTerminal }) {
                SyncRunOutcome.RETRY
            } else {
                SyncRunOutcome.COMPLETE
            }
        },
    )

    val repository: TaskRepository by lazy {
        RoomTaskRepository(
            database = database,
            scheduleSync = ::scheduleSync,
            reminderScheduler = reminderScheduler,
        )
    }

    fun scheduleSync() {
        scheduler.enqueue()
    }

    fun rescheduleReminders() {
        applicationScope.launch {
            repository.tasks.first().forEach { task ->
                task.reminderAtMillis?.let { reminderAtMillis ->
                    reminderScheduler.schedule(
                        task.id,
                        task.title,
                        reminderAtMillis,
                        task.reminderRepeat,
                    )
                }
            }
        }
    }

    fun setForeground(value: Boolean) {
        isForeground = value
    }

    fun showReminder(taskId: String, title: String) {
        if (isForeground) reminderQueueStore.enqueue(ReminderAlert(taskId, title))
    }

    fun handleReminderAction(taskId: String, status: TaskStatus) {
        reminderQueueStore.remove(taskId)
        applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(taskId.hashCode() and Int.MAX_VALUE)
        applicationScope.launch {
            repository.changeStatus(taskId, status)
        }
    }

    private companion object {
        const val DATABASE_NAME = "local-first-tasks.db"
        const val BASE_RETRY_MILLIS = 10_000L
        const val MAX_RETRY_MILLIS = 6 * 60 * 60 * 1_000L

        fun retryDelayMillis(attemptCount: Int): Long {
            val exponent = (attemptCount - 1).coerceIn(0, 11)
            return minOf(BASE_RETRY_MILLIS * (1L shl exponent), MAX_RETRY_MILLIS)
        }
    }
}
