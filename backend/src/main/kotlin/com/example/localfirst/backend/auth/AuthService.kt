package com.example.localfirst.backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val store: AuthStore,
    private val passwords: PasswordHasher,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val tokenFactory: () -> String = ::secureToken,
    private val codeFactory: () -> String = {
        "%04d".format(SecureRandom().nextInt(VERIFICATION_CODE_BOUND))
    },
) {
    fun requestCode(rawContact: String, purpose: VerificationPurpose): VerificationCodeResult {
        val contact = normalizeContact(rawContact)
        val now = clock.millis()
        val latest = store.latestCode(contact, purpose)
        if (latest != null && now - latest.createdAtMillis < CODE_COOLDOWN_MILLIS) {
            throw AuthFailure.TooManyRequests()
        }
        if (purpose == VerificationPurpose.REGISTER && store.findUserByContact(contact) != null) {
            throw AuthFailure.ContactExists()
        }
        val code = codeFactory()
            .padStart(VERIFICATION_CODE_LENGTH, '0')
            .takeLast(VERIFICATION_CODE_LENGTH)
        val expires = now + CODE_VALIDITY_MILLIS
        store.saveCode(StoredVerificationCode(idFactory(), contact, purpose, sha256(code), now, expires))
        return VerificationCodeResult(code, expires)
    }

    fun register(rawContact: String, password: String, code: String): AuthSession {
        val contact = normalizeContact(rawContact)
        validatePassword(password)
        if (store.findUserByContact(contact) != null) {
            throw AuthFailure.ContactExists()
        }
        consumeValidCode(contact, VerificationPurpose.REGISTER, code)
        val user = AuthUser(idFactory(), contact, clock.millis())
        store.createUser(user, passwords.hash(password))
        return createSession(user)
    }

    fun login(rawContact: String, password: String): AuthSession {
        val stored = store.findUserByContact(normalizeContact(rawContact))
            ?: throw AuthFailure.InvalidCredentials()
        if (!passwords.matches(password, stored.passwordHash)) {
            throw AuthFailure.InvalidCredentials()
        }
        return createSession(stored.user)
    }

    fun resetPassword(rawContact: String, newPassword: String, code: String) {
        val contact = normalizeContact(rawContact)
        validatePassword(newPassword)
        val user = store.findUserByContact(contact) ?: throw AuthFailure.InvalidCredentials()
        consumeValidCode(contact, VerificationPurpose.RESET_PASSWORD, code)
        store.updatePassword(user.user.id, passwords.hash(newPassword))
    }

    fun authenticate(authorization: String?): AuthUser {
        val token = authorization?.removePrefix("Bearer ")?.trim().orEmpty()
        if (token.isBlank()) {
            throw AuthFailure.Unauthorized()
        }
        return store.findSession(sha256(token), clock.millis()) ?: throw AuthFailure.Unauthorized()
    }

    private fun consumeValidCode(contact: String, purpose: VerificationPurpose, rawCode: String) {
        val now = clock.millis()
        val stored = store.latestCode(contact, purpose)
        val isMissingOrUsed = stored == null || stored.usedAtMillis != null
        val isExpired = stored != null && stored.expiresAtMillis < now
        val hasTooManyFailures = stored != null && stored.failedAttempts >= MAX_CODE_ATTEMPTS
        val hasWrongCode = stored != null && stored.codeHash != sha256(rawCode.trim())
        val isUnusable = isMissingOrUsed || isExpired
        val isInvalidAttempt = hasTooManyFailures || hasWrongCode
        if (isUnusable || isInvalidAttempt) {
            stored?.let { store.markCodeFailure(it.id) }
            throw AuthFailure.InvalidCode()
        }
        store.consumeCode(checkNotNull(stored).id, now)
    }

    private fun createSession(user: AuthUser): AuthSession {
        val token = tokenFactory()
        val now = clock.millis()
        store.saveSession(sha256(token), user.id, now, now + SESSION_VALIDITY_MILLIS)
        return AuthSession(token, user)
    }

    private fun normalizeContact(value: String): String {
        val contact = value.trim().lowercase()
        val isPhoneNumber = contact.matches(Regex("^1\\d{10}$"))
        val isEmailAddress = contact.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
        if (!isPhoneNumber && !isEmailAddress) {
            throw AuthFailure.InvalidContact()
        }
        return contact
    }

    private fun validatePassword(value: String) {
        if (value.length < MIN_PASSWORD_LENGTH) {
            throw AuthFailure.WeakPassword()
        }
    }

    companion object {
        const val CODE_COOLDOWN_MILLIS = 60_000L
        const val CODE_VALIDITY_MILLIS = 300_000L
        const val SESSION_VALIDITY_MILLIS = 2_592_000_000L
        const val MAX_CODE_ATTEMPTS = 5
        private const val VERIFICATION_CODE_LENGTH = 4
        private const val VERIFICATION_CODE_BOUND = 10_000
        private const val MIN_PASSWORD_LENGTH = 8
    }
}

private const val BYTE_MASK = 0xff
private const val TOKEN_LENGTH = 64
private const val TOKEN_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

private fun secureToken(): String = buildString(TOKEN_LENGTH) {
    val random = SecureRandom()
    repeat(TOKEN_LENGTH) {
        append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)])
    }
}
