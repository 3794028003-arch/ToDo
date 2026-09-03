package com.example.localfirst.backend.web

import com.example.localfirst.backend.auth.AuthService
import com.example.localfirst.backend.share.TaskShareService
import com.example.localfirst.backend.sync.ServerTask
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ShareCodeResponse(val shareCode: String)
data class ShareDownloadResponse(val tasks: List<ServerTask>)

@RestController
@RequestMapping("/api/v1/shares")
class TaskShareController(
    private val auth: AuthService,
    private val shares: TaskShareService,
) {
    @PostMapping
    fun upload(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ShareCodeResponse {
        val user = auth.authenticate(authorization)
        return ShareCodeResponse(shares.upload(user.id))
    }

    @PostMapping("/{code}/download")
    fun download(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable code: String,
    ): ShareDownloadResponse {
        val user = auth.authenticate(authorization)
        return ShareDownloadResponse(shares.download(user.id, code))
    }
}
