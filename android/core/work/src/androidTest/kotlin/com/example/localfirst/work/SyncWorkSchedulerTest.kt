package com.example.localfirst.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkSchedulerTest {
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun connectedExponentialWorkIsUniqueAcrossRepeatedScheduling() {
        val request = SyncWorkScheduler.createRequest()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(10_000L, request.workSpec.backoffDelayDuration)

        val scheduler = SyncWorkScheduler(workManager)
        scheduler.enqueue()
        scheduler.enqueue()

        val work = workManager.getWorkInfosForUniqueWork(SyncWorkScheduler.UNIQUE_WORK_NAME)
            .get(5, TimeUnit.SECONDS)
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }
}
