package com.example.localfirst.backend.auth

data class AuthUser(val id: String, val contact: String, val createdAtMillis: Long = 0L)
data class StoredUser(val user: AuthUser, val passwordHash: String)
enum class VerificationPurpose { REGISTER, RESET_PASSWORD }
data class StoredVerificationCode(
    val id: String, val contact: String, val purpose: VerificationPurpose, val codeHash: String,
    val createdAtMillis: Long, val expiresAtMillis: Long, val usedAtMillis: Long? = null,
    val failedAttempts: Int = 0,
)
data class VerificationCodeResult(val developmentCode: String, val expiresAtMillis: Long)
data class AuthSession(val token: String, val user: AuthUser)

sealed class AuthFailure(message: String) : RuntimeException(message) {
    class InvalidContact : AuthFailure("请输入有效的邮箱或手机号")
    class WeakPassword : AuthFailure("密码至少需要8位")
    class ContactExists : AuthFailure("账号已经存在")
    class InvalidCredentials : AuthFailure("账号或密码错误")
    class InvalidCode : AuthFailure("验证码错误或已失效")
    class TooManyRequests : AuthFailure("请稍后再获取验证码")
    class Unauthorized : AuthFailure("请先登录")
}

interface PasswordHasher { fun hash(value: String): String; fun matches(value: String, hash: String): Boolean }
interface AuthStore {
    fun latestCode(contact: String, purpose: VerificationPurpose): StoredVerificationCode?
    fun saveCode(code: StoredVerificationCode)
    fun markCodeFailure(id: String)
    fun consumeCode(id: String, usedAtMillis: Long)
    fun findUserByContact(contact: String): StoredUser?
    fun createUser(user: AuthUser, passwordHash: String)
    fun updatePassword(userId: String, passwordHash: String)
    fun saveSession(tokenHash: String, userId: String, createdAtMillis: Long, expiresAtMillis: Long)
    fun findSession(tokenHash: String, nowMillis: Long): AuthUser?
}
