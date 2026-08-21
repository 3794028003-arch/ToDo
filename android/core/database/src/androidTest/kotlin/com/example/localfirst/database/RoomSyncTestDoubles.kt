package com.example.localfirst.database

import com.example.localfirst.sync.PushResult
import com.example.localfirst.sync.SyncApi
import com.example.localfirst.sync.SyncClock
import com.example.localfirst.sync.SyncOperation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.yield

internal class TestSyncClock(
    private var currentMillis: Long,
) : SyncClock {
    override fun nowMillis(): Long = currentMillis

    fun advanceBy(millis: Long) {
        currentMillis += millis
    }
}

internal class RecordingRoomSyncApi(
    private val responder: (SyncOperation, Int) -> PushResult,
) : SyncApi {
    private val activeRequests = AtomicInteger(0)
    private val maximumActiveRequests = AtomicInteger(0)
    private val attempts = ConcurrentHashMap<String, AtomicInteger>()
    private val recordedBatches = mutableListOf<List<String>>()

    val maxConcurrentRequests: Int
        get() = maximumActiveRequests.get()

    val batches: List<List<String>>
        get() = synchronized(recordedBatches) { recordedBatches.map { it.toList() } }

    fun attemptsFor(operationId: String): Int = attempts[operationId]?.get() ?: 0

    override suspend fun push(operations: List<SyncOperation>): List<PushResult> {
        val active = activeRequests.incrementAndGet()
        maximumActiveRequests.accumulateAndGet(active, ::maxOf)
        synchronized(recordedBatches) {
            recordedBatches += operations.map(SyncOperation::operationId)
        }

        return try {
            yield()
            operations.map { operation ->
                val attempt = attempts
                    .computeIfAbsent(operation.operationId) { AtomicInteger(0) }
                    .incrementAndGet()
                responder(operation, attempt)
            }
        } finally {
            activeRequests.decrementAndGet()
        }
    }
}
