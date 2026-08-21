package com.example.localfirst.backend.web

import com.example.localfirst.backend.sync.ServerTask
import com.example.localfirst.backend.sync.ServerTaskStore
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tasks")
class TaskController(
    private val tasks: ServerTaskStore,
) {
    @GetMapping("/{taskId}")
    fun task(@PathVariable taskId: String): ServerTask =
        tasks.find(taskId) ?: throw TaskNotFoundException()
}

@ResponseStatus(HttpStatus.NOT_FOUND)
private class TaskNotFoundException : RuntimeException()
