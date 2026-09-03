package com.example.localfirst.app

import android.content.Context
import android.app.NotificationManager
import androidx.room.Room
import androidx.work.WorkManager
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.data.ScopedTaskRepository
import com.example.localfirst.data.accountTaskSpace
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
import com.example.localfirst.database.MIGRATION_9_10
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AppGraph(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reminderScheduler by lazy { AlarmTaskReminderScheduler(applicationContext) }
    val accountSessionStore = AccountSessionStore(applicationContext)
    val accountRepository: AccountRepository by lazy { RetrofitAccountRepository(BuildConfig.SYNC_BASE_URL, accountSessionStore) }
    val appearancePreferences: AppearancePreferences = SharedAppearancePreferences(applicationContext)
    val reminderQueueStore = ReminderQueueStore()
    @Volatile var isForeground: Boolean = false
        private set

    private val databases = ConcurrentHashMap<String, TaskDatabase>()
    private val repositories = ConcurrentHashMap<String, TaskRepository>()
    private val accountSyncMutex = Mutex()
    private val clock = SyncClock(System::currentTimeMillis)
    private val retryPolicy = RetryPolicy(::retryDelayMillis)
    private val scheduler by lazy {
        SyncWorkScheduler(WorkManager.getInstance(applicationContext))
    }

    val workerFactory = SyncWorkerFactory(
        SyncWorkRunner {
            val session = accountSessionStore.current() ?: return@SyncWorkRunner SyncRunOutcome.COMPLETE
            if (synchronizeSession(session)) {
                SyncRunOutcome.RETRY
            } else {
                SyncRunOutcome.COMPLETE
            }
        },
    )

    val repository: TaskRepository by lazy {
        ScopedTaskRepository(accountSessionStore.taskSpace, ::repositoryForSpace)
    }

    init {
        applicationScope.launch {
            var previousSpace = accountSessionStore.taskSpace.value
            accountSessionStore.taskSpace.collect { currentSpace ->
                if (currentSpace != previousSpace) {
                    repositoryForSpace(previousSpace).tasks.first().forEach { task ->
                        if (task.reminderAtMillis != null) reminderScheduler.cancel(task.id)
                    }
                    repositoryForSpace(currentSpace).tasks.first().forEach { task ->
                        task.reminderAtMillis?.let { reminderAtMillis ->
                            reminderScheduler.schedule(task.id, task.title, reminderAtMillis, task.reminderRepeat)
                        }
                    }
                    previousSpace = currentSpace
                }
            }
        }
    }

    fun scheduleSync() {
        if (accountSessionStore.current() != null) scheduler.enqueue()
    }

    suspend fun synchronizeAccount() {
        val session = accountSessionStore.current() ?: return
        synchronizeSession(session)
        scheduleSync()
    }

    suspend fun mergeDownloadedTasks(owner: AccountSession, tasks: List<com.example.localfirst.data.RemoteTask>) {
        val session = accountSessionStore.current()
        if (session?.userId != owner.userId || session.token != owner.token) error("账号已切换，请重新下载")
        repositoryForSpace(accountTaskSpace(owner.userId)).mergeRemoteTasks(tasks)
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

    private fun databaseForSpace(space: String): TaskDatabase = databases[space] ?: synchronized(databases) {
        databases[space] ?: Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            databaseNameFor(space),
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
        ).build().also { databases[space] = it }
    }

    private fun repositoryForSpace(space: String): TaskRepository = repositories[space] ?: synchronized(repositories) {
        repositories[space] ?: RoomTaskRepository(
            database = databaseForSpace(space),
            scheduleSync = ::scheduleSync,
            reminderScheduler = reminderScheduler,
        ).also { repositories[space] = it }
    }

    private suspend fun synchronizeSession(session: AccountSession): Boolean = accountSyncMutex.withLock {
        val current = accountSessionStore.current()
        if (current?.userId != session.userId || current.token != session.token) return@withLock false
        val space = accountTaskSpace(session.userId)
        val store = RoomSyncStore(databaseForSpace(space))
        val engine = SyncEngine(
            store = store,
            api = RetrofitSyncApi.create(BuildConfig.SYNC_BASE_URL) { session.token },
            clock = clock,
            retryPolicy = retryPolicy,
        )
        engine.drain()
        val remoteTasks = accountRepository.snapshot(session)
        val stillCurrent = accountSessionStore.current()
        if (stillCurrent?.userId != session.userId || stillCurrent.token != session.token) return@withLock false
        repositoryForSpace(space).mergeRemoteTasks(remoteTasks)
        store.allOperations().any { operation -> !operation.state.isTerminal }
    }

    private fun databaseNameFor(space: String): String {
        if (space == com.example.localfirst.data.LOCAL_TASK_SPACE) return DATABASE_NAME
        val digest = MessageDigest.getInstance("SHA-256").digest(space.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .take(20)
        return "local-first-tasks-account-$digest.db"
    }
}
