package com.example.localfirst.backend.auth

private const val SEPARATOR = "::"
fun taskPrefix(userId: String): String = "$userId$SEPARATOR"
fun scopedTaskId(userId: String, localTaskId: String): String = taskPrefix(userId) + localTaskId
fun unscopedTaskId(userId: String, scopedTaskId: String): String = scopedTaskId.removePrefix(taskPrefix(userId))
fun scopedOperationId(userId: String, operationId: String): String = "$userId$SEPARATOR$operationId"
