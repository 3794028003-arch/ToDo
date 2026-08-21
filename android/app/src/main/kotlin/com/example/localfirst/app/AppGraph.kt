package com.example.localfirst.app

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.localfirst.data.TaskRepository
import com.example.localfirst.database.RoomSyncStore
import com.example.localfirst.database.RoomTaskRepository
import com.example.localfirst.database.TaskDatabase
import com.example.localfirst.database.MIGRATION_1_2
import com.example.localfirst.network.RetrofitSyncApi
import com.example.localfirst.sync.RetryPolicy
import com.example.localfirst.sync.SyncClock
import com.example.localfirst.sync.SyncEngine
import com.example.localfirst.work.SyncRunOutcome
import com.example.localfirst.work.SyncWorkRunner
import com.example.localfirst.work.SyncWorkScheduler
import com.example.localfirst.work.SyncWorkerFactory

class AppGraph(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    private val database: TaskDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()
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
        )
    }

    fun scheduleSync() {
        scheduler.enqueue()
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
