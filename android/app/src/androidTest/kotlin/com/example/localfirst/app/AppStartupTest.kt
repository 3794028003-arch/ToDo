package com.example.localfirst.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupTest {
    @Test
    fun applicationProvidesStableLocalFirstDependencies() = runTest {
        val application = ApplicationProvider.getApplicationContext<LocalFirstApplication>()

        assertSame(application.graph.repository, application.graph.repository)
        application.graph.repository.tasks.first()
        assertNotNull(application.workManagerConfiguration.workerFactory)
    }

    @Test
    fun launcherActivityStartsWithoutBackendAvailability() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertSame(
                    ApplicationProvider.getApplicationContext<LocalFirstApplication>(),
                    activity.application,
                )
            }
        }
    }
}
