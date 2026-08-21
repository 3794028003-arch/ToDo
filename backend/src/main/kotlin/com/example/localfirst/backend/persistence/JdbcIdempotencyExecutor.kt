package com.example.localfirst.backend.persistence

import com.example.localfirst.backend.sync.BackendOperationResult
import com.example.localfirst.backend.sync.IdempotencyExecutor
import java.sql.ResultSet
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Repository
class JdbcIdempotencyExecutor(
    private val jdbcClient: JdbcClient,
    transactionManager: PlatformTransactionManager,
) : IdempotencyExecutor {
    private val transactions = TransactionTemplate(transactionManager)

    override fun executeOnce(
        operationId: String,
        requestHash: String,
        action: () -> BackendOperationResult,
    ): BackendOperationResult = checkNotNull(
        transactions.execute {
            reserveOperation(operationId, requestHash)
            val stored = lockOperation(operationId)
            check(stored.requestHash == requestHash) {
                "Operation $operationId was already used with a different request hash"
            }

            stored.toResultOrNull() ?: action().also(::saveResult)
        },
    )

    private fun reserveOperation(operationId: String, requestHash: String) {
        jdbcClient.sql(
            """
            INSERT INTO sync_operations (operation_id, request_hash)
            VALUES (:operationId, :requestHash)
            ON CONFLICT (operation_id) DO NOTHING
            """.trimIndent(),
        )
            .param("operationId", operationId)
            .param("requestHash", requestHash)
            .update()
    }

    private fun lockOperation(operationId: String): StoredOperation = jdbcClient.sql(
        """
        SELECT operation_id, request_hash, response_status,
               server_version, tombstone_version, deleted_at_millis
        FROM sync_operations
        WHERE operation_id = :operationId
        FOR UPDATE
        """.trimIndent(),
    )
        .param("operationId", operationId)
        .query(STORED_OPERATION_ROW_MAPPER)
        .single()

    private fun saveResult(result: BackendOperationResult) {
        when (result) {
            is BackendOperationResult.Applied -> jdbcClient.sql(
                """
                UPDATE sync_operations
                SET response_status = 'APPLIED',
                    server_version = :serverVersion,
                    completed_at = CURRENT_TIMESTAMP
                WHERE operation_id = :operationId
                """.trimIndent(),
            )
                .param("operationId", result.operationId)
                .param("serverVersion", result.serverVersion)
                .update()

            is BackendOperationResult.TaskDeleted -> jdbcClient.sql(
                """
                UPDATE sync_operations
                SET response_status = 'TASK_DELETED',
                    tombstone_version = :tombstoneVersion,
                    deleted_at_millis = :deletedAtMillis,
                    completed_at = CURRENT_TIMESTAMP
                WHERE operation_id = :operationId
                """.trimIndent(),
            )
                .param("operationId", result.operationId)
                .param("tombstoneVersion", result.tombstoneVersion)
                .param("deletedAtMillis", result.deletedAtMillis)
                .update()

            is BackendOperationResult.VersionConflict -> jdbcClient.sql(
                """
                UPDATE sync_operations
                SET response_status = 'VERSION_CONFLICT',
                    server_version = :serverVersion,
                    completed_at = CURRENT_TIMESTAMP
                WHERE operation_id = :operationId
                """.trimIndent(),
            )
                .param("operationId", result.operationId)
                .param("serverVersion", result.serverVersion)
                .update()
        }
    }

    private data class StoredOperation(
        val operationId: String,
        val requestHash: String,
        val responseStatus: String?,
        val serverVersion: Long?,
        val tombstoneVersion: Long?,
        val deletedAtMillis: Long?,
    ) {
        fun toResultOrNull(): BackendOperationResult? = when (responseStatus) {
            null -> null
            "APPLIED" -> BackendOperationResult.Applied(
                operationId = operationId,
                serverVersion = requireNotNull(serverVersion),
            )

            "TASK_DELETED" -> BackendOperationResult.TaskDeleted(
                operationId = operationId,
                tombstoneVersion = requireNotNull(tombstoneVersion),
                deletedAtMillis = requireNotNull(deletedAtMillis),
            )

            "VERSION_CONFLICT" -> BackendOperationResult.VersionConflict(
                operationId = operationId,
                serverVersion = requireNotNull(serverVersion),
            )

            else -> error("Unsupported stored response status: $responseStatus")
        }
    }

    private companion object {
        val STORED_OPERATION_ROW_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            StoredOperation(
                operationId = resultSet.getString("operation_id"),
                requestHash = resultSet.getString("request_hash"),
                responseStatus = resultSet.getString("response_status"),
                serverVersion = resultSet.nullableLong("server_version"),
                tombstoneVersion = resultSet.nullableLong("tombstone_version"),
                deletedAtMillis = resultSet.nullableLong("deleted_at_millis"),
            )
        }
    }
}

private fun ResultSet.nullableLong(columnName: String): Long? =
    getObject(columnName, java.lang.Long::class.java)?.toLong()
