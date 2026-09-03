package com.example.localfirst.backend.persistence

import com.example.localfirst.backend.share.TaskShareStore
import com.example.localfirst.backend.sync.ServerTask
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcTaskShareStore(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
) : TaskShareStore {
    override fun save(
        code: String,
        ownerUserId: String,
        tasks: List<ServerTask>,
        createdAtMillis: Long,
        expiresAtMillis: Long,
    ) {
        jdbc.sql(
            """
            INSERT INTO task_share_packages(
                share_code, owner_user_id, payload_json, created_at_millis, expires_at_millis
            ) VALUES (:code, :owner, :payload, :created, :expires)
            """.trimIndent(),
        )
            .params(
                mapOf(
                    "code" to code,
                    "owner" to ownerUserId,
                    "payload" to json.writeValueAsString(tasks),
                    "created" to createdAtMillis,
                    "expires" to expiresAtMillis,
                ),
            )
            .update()
    }

    override fun load(code: String, nowMillis: Long): List<ServerTask>? = jdbc.sql(
        """
        SELECT payload_json
        FROM task_share_packages
        WHERE share_code = :code AND expires_at_millis > :now
        """.trimIndent(),
    )
        .param("code", code)
        .param("now", nowMillis)
        .query(String::class.java)
        .optional()
        .orElse(null)
        ?.let { json.readValue(it, object : TypeReference<List<ServerTask>>() {}) }
}
