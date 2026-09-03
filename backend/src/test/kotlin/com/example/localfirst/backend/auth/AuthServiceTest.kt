package com.example.localfirst.backend.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthServiceTest {
    private val store = FakeAuthStore()
    private var now = 1_700_000_000_000L
    private val service = AuthService(
        store = store,
        passwords = PlainTestPasswords,
        clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
        idFactory = { "id-${store.codes.size + store.users.size + 1}" },
        tokenFactory = { "session-token" },
        codeFactory = { "0042" },
    )

    @Test
    fun `server creates a four digit development code`() {
        val result = service.requestCode("13800138000", VerificationPurpose.REGISTER)

        assertEquals("0042", result.developmentCode)
        assertTrue(result.developmentCode.matches(Regex("\\d{4}")))
        assertNotEquals("0042", store.codes.single().codeHash)
    }

    @Test
    fun `code request is throttled for sixty seconds`() {
        service.requestCode("13800138000", VerificationPurpose.REGISTER)

        assertThrows(AuthFailure.TooManyRequests::class.java) {
            service.requestCode("13800138000", VerificationPurpose.REGISTER)
        }
    }

    @Test
    fun `registration consumes code and login returns an authenticated session`() {
        service.requestCode("13800138000", VerificationPurpose.REGISTER)
        val registered = service.register("13800138000", "password8", "0042")
        val loggedIn = service.login("13800138000", "password8")

        assertEquals("13800138000", registered.user.contact)
        assertEquals(now, registered.user.createdAtMillis)
        assertEquals(registered.user.id, loggedIn.user.id)
        assertEquals(loggedIn.user, service.authenticate(loggedIn.token))
        assertThrows(AuthFailure.InvalidCode::class.java) {
            service.register("another@example.com", "password8", "0042")
        }
    }
}

private object PlainTestPasswords : PasswordHasher {
    override fun hash(value: String): String = "hashed:$value"
    override fun matches(value: String, hash: String): Boolean = hash == "hashed:$value"
}

private class FakeAuthStore : AuthStore {
    val users = mutableListOf<AuthUser>()
    val codes = mutableListOf<StoredVerificationCode>()
    private val passwordHashes = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, AuthUser>()

    override fun latestCode(contact: String, purpose: VerificationPurpose) =
        codes.filter { it.contact == contact && it.purpose == purpose }.maxByOrNull { it.createdAtMillis }
    override fun saveCode(code: StoredVerificationCode) { codes += code }
    override fun markCodeFailure(id: String) = Unit
    override fun consumeCode(id: String, usedAtMillis: Long) {
        val index = codes.indexOfFirst { it.id == id }
        codes[index] = codes[index].copy(usedAtMillis = usedAtMillis)
    }
    override fun findUserByContact(contact: String) = users.firstOrNull { it.contact == contact }
        ?.let { StoredUser(it, passwordHashes.getValue(it.id)) }
    override fun createUser(user: AuthUser, passwordHash: String) {
        users += user
        passwordHashes[user.id] = passwordHash
    }
    override fun updatePassword(userId: String, passwordHash: String) { passwordHashes[userId] = passwordHash }
    override fun saveSession(tokenHash: String, userId: String, createdAtMillis: Long, expiresAtMillis: Long) {
        sessions[tokenHash] = users.single { it.id == userId }
    }
    override fun findSession(tokenHash: String, nowMillis: Long) = sessions[tokenHash]
}
