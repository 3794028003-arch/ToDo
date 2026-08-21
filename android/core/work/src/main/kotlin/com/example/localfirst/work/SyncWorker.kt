package com.example.localfirst.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

class SyncWorker internal constructor(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val runner: SyncWorkRunner,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = when (runner.run()) {
        SyncRunOutcome.COMPLETE -> Result.success()
        SyncRunOutcome.RETRY -> Result.retry()
    }
}

class SyncWorkerFactory(
    private val runner: SyncWorkRunner,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == SyncWorker::class.java.name) {
        SyncWorker(
            appContext = appContext,
            workerParameters = workerParameters,
            runner = runner,
        )
    } else {
        null
    }
}
