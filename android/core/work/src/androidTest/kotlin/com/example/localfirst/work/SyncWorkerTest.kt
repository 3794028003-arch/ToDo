package com.example.localfirst.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    @Test
    fun completedDrainReturnsSuccess() = runTest {
        val runner = RecordingSyncWorkRunner(SyncRunOutcome.COMPLETE)
        val worker = worker(runner)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, runner.calls)
    }

    @Test
    fun pendingRetryReturnsWorkManagerRetry() = runTest {
        val runner = RecordingSyncWorkRunner(SyncRunOutcome.RETRY)
        val worker = worker(runner)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, runner.calls)
    }

    private fun worker(runner: SyncWorkRunner): SyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder.from(context, SyncWorker::class.java)
            .setWorkerFactory(SyncWorkerFactory(runner))
            .build()
    }
}

private class RecordingSyncWorkRunner(
    private val outcome: SyncRunOutcome,
) : SyncWorkRunner {
    var calls: Int = 0
        private set

    override suspend fun run(): SyncRunOutcome {
        calls += 1
        return outcome
    }
}
