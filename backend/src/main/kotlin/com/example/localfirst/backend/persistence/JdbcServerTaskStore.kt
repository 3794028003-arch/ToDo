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
    override fun listByPrefix(taskIdPrefix: String): List<ServerTask> = jdbcClient.sql(
        """
        SELECT id, title, status, version, reminder_at_millis, reminder_repeat,
               is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        FROM tasks
        WHERE id LIKE :prefix
        ORDER BY id
        """.trimIndent(),
    ).param("prefix", "$taskIdPrefix%").query(TASK_ROW_MAPPER).list()

    override fun find(taskId: String): ServerTask? = jdbcClient.sql(
        """
        SELECT id, title, status, version, reminder_at_millis, reminder_repeat,
               is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        FROM tasks
        WHERE id = :taskId
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    override fun create(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String,
        isPinned: Boolean,
        startDateMillis: Long?,
        dueDateMillis: Long?,
    ): ServerTask = jdbcClient.sql(
        """
        INSERT INTO tasks (
            id, title, status, version, reminder_at_millis, reminder_repeat,
            is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        )
        VALUES (
            :taskId, :title, 'TODO', 1, :reminderAtMillis, :reminderRepeat,
            :isPinned, :startDateMillis, :dueDateMillis, NULL
        )
        RETURNING id, title, status, version, reminder_at_millis, reminder_repeat,
                  is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("title", title)
        .param("reminderAtMillis", reminderAtMillis)
        .param("reminderRepeat", reminderRepeat)
        .param("isPinned", isPinned)
        .param("startDateMillis", startDateMillis)
        .param("dueDateMillis", dueDateMillis)
        .query(TASK_ROW_MAPPER)
        .single()

    override fun changeStatus(
        taskId: String,
        status: ServerTaskStatus,
        expectedVersion: Long,
    ): ServerTask? = jdbcClient.sql(
        """
        UPDATE tasks
        SET status = :status,
            reminder_at_millis = CASE WHEN :status = 'DONE' THEN NULL ELSE reminder_at_millis END,
            reminder_repeat = CASE WHEN :status = 'DONE' THEN 'NONE' ELSE reminder_repeat END,
            version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, reminder_at_millis, reminder_repeat,
                  is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("status", status.name)
        .param("expectedVersion", expectedVersion)
        .query(TASK_ROW_MAPPER)
        .optional()
        .orElse(null)

    override fun updateDetails(
        taskId: String,
        title: String,
        reminderAtMillis: Long?,
        reminderRepeat: String?,
        isPinned: Boolean?,
        startDateMillis: Long?,
        dueDateMillis: Long?,
        expectedVersion: Long,
    ): ServerTask? = jdbcClient.sql(
        """
        UPDATE tasks
        SET title = :title,
            reminder_at_millis = :reminderAtMillis,
            reminder_repeat = COALESCE(:reminderRepeat, reminder_repeat),
            is_pinned = COALESCE(:isPinned, is_pinned),
            start_date_millis = :startDateMillis,
            due_date_millis = :dueDateMillis,
            version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, reminder_at_millis, reminder_repeat,
                  is_pinned, start_date_millis, due_date_millis, deleted_at_millis
        """.trimIndent(),
    )
        .param("taskId", taskId)
        .param("title", title)
        .param("reminderAtMillis", reminderAtMillis)
        .param("reminderRepeat", reminderRepeat)
        .param("isPinned", isPinned)
        .param("startDateMillis", startDateMillis)
        .param("dueDateMillis", dueDateMillis)
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
        SET deleted_at_millis = :deletedAtMillis,
            reminder_at_millis = NULL,
            reminder_repeat = 'NONE',
            version = version + 1
        WHERE id = :taskId
          AND version = :expectedVersion
          AND deleted_at_millis IS NULL
        RETURNING id, title, status, version, reminder_at_millis, reminder_repeat,
                  is_pinned, start_date_millis, due_date_millis, deleted_at_millis
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
                reminderAtMillis = resultSet.getObject(
                    "reminder_at_millis",
                    java.lang.Long::class.java,
                )?.toLong(),
                reminderRepeat = resultSet.getString("reminder_repeat"),
                isPinned = resultSet.getBoolean("is_pinned"),
                startDateMillis = resultSet.getObject(
                    "start_date_millis",
                    java.lang.Long::class.java,
                )?.toLong(),
                dueDateMillis = resultSet.getObject(
                    "due_date_millis",
                    java.lang.Long::class.java,
                )?.toLong(),
                deletedAtMillis = resultSet.getObject(
                    "deleted_at_millis",
                    java.lang.Long::class.java,
                )?.toLong(),
            )
        }
    }
}
