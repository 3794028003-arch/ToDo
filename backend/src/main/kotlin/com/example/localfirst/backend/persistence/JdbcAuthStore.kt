package com.example.localfirst.backend.persistence

import com.example.localfirst.backend.auth.AuthStore
import com.example.localfirst.backend.auth.AuthUser
import com.example.localfirst.backend.auth.StoredUser
import com.example.localfirst.backend.auth.StoredVerificationCode
import com.example.localfirst.backend.auth.VerificationPurpose
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcAuthStore(private val jdbc: JdbcClient) : AuthStore {
    override fun latestCode(
        contact: String,
        purpose: VerificationPurpose,
    ): StoredVerificationCode? = jdbc.sql(
        """
        SELECT id, contact, purpose, code_hash, created_at_millis, expires_at_millis,
               used_at_millis, failed_attempts
        FROM verification_codes
        WHERE contact = :contact AND purpose = :purpose
        ORDER BY created_at_millis DESC
        LIMIT 1
        """.trimIndent(),
    )
        .param("contact", contact)
        .param("purpose", purpose.name)
        .query { resultSet, _ -> resultSet.toStoredVerificationCode() }
        .optional()
        .orElse(null)

    override fun saveCode(code: StoredVerificationCode) {
        jdbc.sql(
            """
            INSERT INTO verification_codes(
                id, contact, purpose, code_hash, created_at_millis, expires_at_millis,
                failed_attempts
            ) VALUES (:id, :contact, :purpose, :hash, :created, :expires, 0)
            """.trimIndent(),
        )
            .params(
                mapOf(
                    "id" to code.id,
                    "contact" to code.contact,
                    "purpose" to code.purpose.name,
                    "hash" to code.codeHash,
                    "created" to code.createdAtMillis,
                    "expires" to code.expiresAtMillis,
                ),
            )
            .update()
    }

    override fun markCodeFailure(id: String) {
        jdbc.sql(
            "UPDATE verification_codes SET failed_attempts = failed_attempts + 1 WHERE id = :id",
        )
            .param("id", id)
            .update()
    }

    override fun consumeCode(id: String, usedAtMillis: Long) {
        jdbc.sql(
            "UPDATE verification_codes SET used_at_millis = :used WHERE id = :id AND used_at_millis IS NULL",
        )
            .param("id", id)
            .param("used", usedAtMillis)
            .update()
    }

    override fun findUserByContact(contact: String): StoredUser? = jdbc.sql(
        "SELECT id, contact, password_hash, created_at_millis FROM app_users WHERE contact = :contact",
    )
        .param("contact", contact)
        .query { resultSet, _ ->
            StoredUser(
                AuthUser(
                    resultSet.getString("id"),
                    resultSet.getString("contact"),
                    resultSet.getLong("created_at_millis"),
                ),
                resultSet.getString("password_hash"),
            )
        }
        .optional()
        .orElse(null)

    override fun createUser(user: AuthUser, passwordHash: String) {
        jdbc.sql(
            "INSERT INTO app_users(id, contact, password_hash, created_at_millis) " +
                "VALUES (:id, :contact, :hash, :created)",
        )
            .params(
                mapOf(
                    "id" to user.id,
                    "contact" to user.contact,
                    "hash" to passwordHash,
                    "created" to user.createdAtMillis,
                ),
            )
            .update()
    }

    override fun updatePassword(userId: String, passwordHash: String) {
        jdbc.sql("UPDATE app_users SET password_hash = :hash WHERE id = :id")
            .param("id", userId)
            .param("hash", passwordHash)
            .update()
    }

    override fun saveSession(
        tokenHash: String,
        userId: String,
        createdAtMillis: Long,
        expiresAtMillis: Long,
    ) {
        jdbc.sql(
            """
            INSERT INTO auth_sessions(token_hash, user_id, created_at_millis, expires_at_millis)
            VALUES (:token, :user, :created, :expires)
            """.trimIndent(),
        )
            .params(
                mapOf(
                    "token" to tokenHash,
                    "user" to userId,
                    "created" to createdAtMillis,
                    "expires" to expiresAtMillis,
                ),
            )
            .update()
    }

    override fun findSession(tokenHash: String, nowMillis: Long): AuthUser? = jdbc.sql(
        """
        SELECT u.id, u.contact, u.created_at_millis
        FROM auth_sessions s
        JOIN app_users u ON u.id = s.user_id
        WHERE s.token_hash = :token AND s.expires_at_millis > :now
        """.trimIndent(),
    )
        .param("token", tokenHash)
        .param("now", nowMillis)
        .query { resultSet, _ ->
            AuthUser(
                resultSet.getString("id"),
                resultSet.getString("contact"),
                resultSet.getLong("created_at_millis"),
            )
        }
        .optional()
        .orElse(null)
}

private fun java.sql.ResultSet.toStoredVerificationCode() = StoredVerificationCode(
    getString("id"),
    getString("contact"),
    VerificationPurpose.valueOf(getString("purpose")),
    getString("code_hash"),
    getLong("created_at_millis"),
    getLong("expires_at_millis"),
    getObject("used_at_millis", java.lang.Long::class.java)?.toLong(),
    getInt("failed_attempts"),
)
