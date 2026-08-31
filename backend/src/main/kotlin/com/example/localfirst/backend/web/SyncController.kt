package com.example.localfirst.backend.web

import com.example.localfirst.backend.auth.AuthService
import com.example.localfirst.backend.auth.scopedOperationId
import com.example.localfirst.backend.auth.scopedTaskId
import com.example.localfirst.backend.sync.SyncBatchProcessor
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Value

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val processor: SyncBatchProcessor,
    private val auth: AuthService,
    @Value("\${doti.allow-unauthenticated-sync:false}") private val allowLegacySync:Boolean,
) {
    @PostMapping("/batch")
    fun pushBatch(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: SyncBatchRequest,
    ): SyncBatchResponse {
        if (authorization.isNullOrBlank() && allowLegacySync) {
            return SyncBatchResponse(processor.process(request.operations.map(SyncOperationRequest::toDomain)).map { it.toResponse() })
        }
        val user = auth.authenticate(authorization)
        val scoped = request.operations.map { operation ->
            operation.toDomain().copy(
                operationId = scopedOperationId(user.id, operation.operationId),
                taskId = scopedTaskId(user.id, operation.taskId),
            )
        }
        return SyncBatchResponse(
            results = processor.process(scoped).mapIndexed { index, result ->
                result.toResponse().copy(operationId = request.operations[index].operationId)
            },
        )
    }
}
