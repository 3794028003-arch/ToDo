package com.example.localfirst.work

enum class SyncRunOutcome {
    COMPLETE,
    RETRY,
}

fun interface SyncWorkRunner {
    suspend fun run(): SyncRunOutcome
}
