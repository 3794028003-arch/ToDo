package com.example.localfirst.backend.persistence

import com.example.localfirst.backend.sync.ServerTask
import com.example.localfirst.backend.sync.ServerTaskStatus
import com.example.localfirst.backend.sync.ServerTaskStore
import java.sql.ResultSet
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcServerTaskStore(
    private val jdbcClient: JdbcClient,
) : ServerTaskStore {
    override fun find(taskId: String): ServerTask? = jdbcClient.sql(
        """
        SELECT id, title, status, version, deleted_at_millis
        FROM tasks
        WHERE id = :taskId
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    override fun create(taskId: String, title: String): ServerTask = jdbcClient.sql(
        """
        INSERT INTO tasks (id, title, status, version, deleted_at_millis)
        VALUES (:taskId, :title, 'TODO', 1, NULL)
        RETURNING id, title, status, version, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("title", title)
        .query(TASK_ROW_MAPPER)
        .single()

    override fun changeStatus(
        taskId: String,
        status: ServerTaskStatus,
        expectedVersion: Long,
    ): ServerTask? = jdbcClient.sql(
        """
        UPDATE tasks
        SET status = :status, version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("status", status.name)
        .param("expectedVersion", expectedVersion)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    override fun updateTitle(
        taskId: String,
        title: String,
        expectedVersion: Long,
    ): ServerTask? = jdbcClient.sql(
        """
        UPDATE tasks
        SET title = :title, version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("title", title)
        .param("expectedVersion", expectedVersion)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    override fun delete(
        taskId: String,
        expectedVersion: Long,
        deletedAtMillis: Long,
    ): ServerTask? = jdbcClient.sql(
        """
        UPDATE tasks
        SET deleted_at_millis = :deletedAtMillis, version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("expectedVersion", expectedVersion)
        .param("deletedAtMillis", deletedAtMillis)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    private companion object {
        val TASK_ROW_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            ServerTask(
                id = resultSet.getString("id"),
                title = resultSet.getString("title"),
                status = ServerTaskStatus.valueOf(resultSet.getString("status")),
                version = resultSet.getLong("version"),
                deletedAtMillis = resultSet.getObject(
                    "deleted_at_millis",
                    java.lang.Long::class.java,
                )?.toLong(),
            )
        }
    }
}
