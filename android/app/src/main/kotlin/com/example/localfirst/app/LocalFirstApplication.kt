package com.example.localfirst.app

import android.app.Application
import androidx.work.Configuration

class LocalFirstApplication : Application(), Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(graph.workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        graph.scheduleSync()
    }
}
