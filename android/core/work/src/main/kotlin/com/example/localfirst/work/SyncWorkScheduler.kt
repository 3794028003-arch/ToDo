package com.example.localfirst.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncWorkScheduler(
    private val workManager: WorkManager,
) {
    fun enqueue() = workManager.enqueueUniqueWork(
        UNIQUE_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        createRequest(),
    )

    companion object {
        const val UNIQUE_WORK_NAME = "task-outbox-sync"

        internal fun createRequest(): OneTimeWorkRequest = OneTimeWorkRequest.Builder(
            SyncWorker::class.java,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS,
            )
            .build()
    }
}
