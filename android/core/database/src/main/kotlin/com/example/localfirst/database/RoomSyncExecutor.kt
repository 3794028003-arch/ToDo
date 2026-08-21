package com.example.localfirst.database

import com.example.localfirst.sync.RetryPolicy
import com.example.localfirst.sync.SyncApi
import com.example.localfirst.sync.SyncClock
import com.example.localfirst.sync.SyncEngine

class RoomSyncExecutor(
    database: TaskDatabase,
    api: SyncApi,
    clock: SyncClock,
    retryPolicy: RetryPolicy,
    batchSize: Int = SyncEngine.DEFAULT_BATCH_SIZE,
    leaseMillis: Long = SyncEngine.DEFAULT_LEASE_MILLIS,
) {
    private val engine = SyncEngine(
        store = RoomSyncStore(database),
        api = api,
        clock = clock,
        retryPolicy = retryPolicy,
        batchSize = batchSize,
        leaseMillis = leaseMillis,
    )

    suspend fun drain() = engine.drain()
}
