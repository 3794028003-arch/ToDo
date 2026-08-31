package com.example.localfirst.backend.web

import com.example.localfirst.backend.auth.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class CodeRequest(val contact:String,val purpose:VerificationPurpose)
data class CodeResponse(val developmentCode:String,val expiresAtMillis:Long)
data class RegisterRequest(val contact:String,val password:String,val code:String)
data class LoginRequest(val contact:String,val password:String)
data class ResetPasswordRequest(val contact:String,val newPassword:String,val code:String)
data class SessionResponse(val token:String,val userId:String,val contact:String,val createdAtMillis:Long)
data class UserResponse(val userId:String,val contact:String,val createdAtMillis:Long)
data class ErrorResponse(val message:String)

@RestController @RequestMapping("/api/v1/auth")
class AuthController(private val auth:AuthService){
    @PostMapping("/codes") fun requestCode(@RequestBody request:CodeRequest)=auth.requestCode(request.contact,request.purpose).let{CodeResponse(it.developmentCode,it.expiresAtMillis)}
    @PostMapping("/register") fun register(@RequestBody request:RegisterRequest)=auth.register(request.contact,request.password,request.code).response()
    @PostMapping("/login") fun login(@RequestBody request:LoginRequest)=auth.login(request.contact,request.password).response()
    @PostMapping("/reset-password") fun reset(@RequestBody request:ResetPasswordRequest)=auth.resetPassword(request.contact,request.newPassword,request.code)
    @GetMapping("/me") fun me(@RequestHeader("Authorization",required=false) authorization:String?)=auth.authenticate(authorization).let{UserResponse(it.id,it.contact,it.createdAtMillis)}
    @ExceptionHandler(AuthFailure::class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun authFailure(error:AuthFailure)=ErrorResponse(error.message?:"账号操作失败")
}
private fun AuthSession.response()=SessionResponse(token,user.id,user.contact,user.createdAtMillis)
