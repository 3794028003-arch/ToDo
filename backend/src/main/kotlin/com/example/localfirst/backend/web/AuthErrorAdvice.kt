package com.example.localfirst.backend.web

import com.example.localfirst.backend.auth.AuthFailure
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthErrorAdvice {
    @ExceptionHandler(AuthFailure.Unauthorized::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun unauthorized(error: AuthFailure.Unauthorized) = ErrorResponse(error.message ?: "请先登录")

    @ExceptionHandler(AuthFailure::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun authFailure(error: AuthFailure) = ErrorResponse(error.message ?: "账号操作失败")
}
