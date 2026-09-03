package com.example.localfirst.backend.web

import com.example.localfirst.backend.auth.AuthService
import com.example.localfirst.backend.auth.scopedTaskId
import com.example.localfirst.backend.auth.taskPrefix
import com.example.localfirst.backend.auth.unscopedTaskId
import com.example.localfirst.backend.sync.ServerTask
import com.example.localfirst.backend.sync.ServerTaskStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val tasks: ServerTaskStore,
    private val auth: AuthService,
    @param:Value("\${doti.allow-unauthenticated-sync:false}") private val allowLegacySync: Boolean,
) {
    @GetMapping("/{taskId}")
    fun task(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable taskId: String,
    ): ServerTask {
        if (authorization.isNullOrBlank() && allowLegacySync) {
            return tasks.find(taskId) ?: throw TaskNotFoundException()
        }
        val user = auth.authenticate(authorization)
        return tasks.find(scopedTaskId(user.id, taskId))?.copy(id = taskId)
            ?: throw TaskNotFoundException()
    }

    @GetMapping
    fun tasks(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): List<ServerTask> {
        if (authorization.isNullOrBlank() && allowLegacySync) {
            return tasks.listByPrefix("")
        }
        val user = auth.authenticate(authorization)
        return tasks.listByPrefix(taskPrefix(user.id)).map {
            it.copy(id = unscopedTaskId(user.id, it.id))
        }
    }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
private class TaskNotFoundException : RuntimeException()
