package com.example.localfirst.backend.sync

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SyncConfiguration {
    @Bean
    fun syncBatchProcessor(
        idempotencyExecutor: IdempotencyExecutor,
        serverTaskStore: ServerTaskStore,
    ): SyncBatchProcessor = SyncBatchProcessor(
        idempotency = idempotencyExecutor,
        tasks = serverTaskStore,
    )
}
