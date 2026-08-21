package com.example.localfirst.backend.web

import com.example.localfirst.backend.sync.SyncBatchProcessor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val processor: SyncBatchProcessor,
) {
    @PostMapping("/batch")
    fun pushBatch(@RequestBody request: SyncBatchRequest): SyncBatchResponse = SyncBatchResponse(
        results = processor.process(request.operations.map(SyncOperationRequest::toDomain))
            .map { it.toResponse() },
    )
}
