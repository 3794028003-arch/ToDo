package com.example.localfirst.backend.sync

import com.example.localfirst.backend.LocalFirstTaskBackendApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest(classes = [LocalFirstTaskBackendApplication::class], properties = ["doti.allow-unauthenticated-sync=true"])
@AutoConfigureMockMvc
class PostgreSqlSyncApiDisasterTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun clearDatabase() {
        jdbcClient.sql("TRUNCATE TABLE sync_operations, tasks").update()
    }

    @Test
    fun concurrentDuplicateOperationIsAppliedExactlyOnceAndReturnsSameResult() = runBlocking {
        val request =
            """
            {
              "operations": [
                {
                  "operationId": "create-operation-1",
                  "requestHash": "sha256:create-operation-1",
                  "taskId": "task-a",
                  "type": "CREATE",
                  "title": "Task A"
                }
              ]
            }
            """.trimIndent()
        val start = CompletableDeferred<Unit>()
        val responses = (1..2).map {
            async(Dispatchers.IO) {
                start.await()
                mockMvc.post("/api/v1/sync/batch") {
                    contentType = MediaType.APPLICATION_JSON
                    content = request
                }.andReturn().response
            }
        }

        start.complete(Unit)
        responses.awaitAll().forEach { response ->
            assertEquals(200, response.status)
            assertTrue(response.contentAsString.contains("\"status\":\"APPLIED\""))
            assertTrue(response.contentAsString.contains("\"serverVersion\":1"))
        }

        val taskCount = jdbcClient.sql("SELECT COUNT(*) FROM tasks WHERE id = 'task-a'")
            .query(Long::class.java)
            .single()
        val operationCount = jdbcClient.sql(
            "SELECT COUNT(*) FROM sync_operations WHERE operation_id = 'create-operation-1'",
        ).query(Long::class.java).single()

        assertEquals(1L, taskCount)
        assertEquals(1L, operationCount)

        mockMvc.get("/api/v1/tasks/task-a")
            .andExpect {
                status { isOk() }
                jsonPath("$.version", equalTo(1))
            }
        Unit
    }

    @Test
    fun deletedTaskConflictIsTerminalAndDoesNotBlockTheRestOfTheBatch() {
        insertDeletedAndLiveTasks()

        mockMvc.post("/api/v1/sync/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = deletedConflictRequest
        }.andExpect {
            status { isOk() }
            jsonPath("$.results[0].operationId", equalTo("x-status"))
            jsonPath("$.results[0].status", equalTo("TASK_DELETED"))
            jsonPath("$.results[0].tombstoneVersion", equalTo(8))
            jsonPath("$.results[0].deletedAtMillis", equalTo(5_000))
            jsonPath("$.results[1].operationId", equalTo("y-status"))
            jsonPath("$.results[1].status", equalTo("APPLIED"))
            jsonPath("$.results[1].serverVersion", equalTo(3))
        }

        val taskXVersion = jdbcClient.sql("SELECT version FROM tasks WHERE id = 'task-x'")
            .query(Long::class.java)
            .single()
        val taskYStatus = jdbcClient.sql("SELECT status FROM tasks WHERE id = 'task-y'")
            .query(String::class.java)
            .single()
        val storedOperations = jdbcClient.sql(
            "SELECT COUNT(*) FROM sync_operations WHERE operation_id IN ('x-status', 'y-status')",
        ).query(Long::class.java).single()

        assertEquals(8L, taskXVersion)
        assertEquals("DONE", taskYStatus)
        assertEquals(2L, storedOperations)
    }

    private fun insertDeletedAndLiveTasks() {
        jdbcClient.sql(
            """
            INSERT INTO tasks (id, title, status, version, deleted_at_millis)
            VALUES
                ('task-x', 'Deleted Task', 'TODO', 8, 5000),
                ('task-y', 'Live Task', 'DOING', 2, NULL)
            """.trimIndent(),
        ).update()
    }

    @Test
    fun deleteCreatesOneTombstoneAndDuplicateDeliveryReturnsTheStoredResult() {
        jdbcClient.sql(
            """
            INSERT INTO tasks (id, title, status, version, deleted_at_millis)
            VALUES (:id, :title, :status, :version, NULL)
            """.trimIndent(),
        )
            .param("id", "task-delete")
            .param("title", "Delete me")
            .param("status", "DOING")
            .param("version", 3L)
            .update()

        val request =
            """
            {
              "operations": [
                {
                  "operationId": "delete-operation-1",
                  "requestHash": "sha256:delete-operation-1",
                  "taskId": "task-delete",
                  "type": "DELETE",
                  "baseServerVersion": 3
                }
              ]
            }
            """.trimIndent()

        repeat(2) {
            mockMvc.post("/api/v1/sync/batch") {
                contentType = MediaType.APPLICATION_JSON
                content = request
            }.andExpect {
                status { isOk() }
                jsonPath("$.results[0].operationId", equalTo("delete-operation-1"))
                jsonPath("$.results[0].status", equalTo("APPLIED"))
                jsonPath("$.results[0].serverVersion", equalTo(4))
            }
        }

        val task = jdbcClient.sql(
            """
            SELECT version, deleted_at_millis
            FROM tasks
            WHERE id = 'task-delete'
            """.trimIndent(),
        ).query { resultSet, _ ->
            resultSet.getLong("version") to
                resultSet.getObject("deleted_at_millis", java.lang.Long::class.java)?.toLong()
        }.single()
        val operationCount = jdbcClient.sql(
            "SELECT COUNT(*) FROM sync_operations WHERE operation_id = 'delete-operation-1'",
        ).query(Long::class.java).single()

        assertEquals(4L, task.first)
        assertTrue(task.second != null)
        assertEquals(1L, operationCount)
    }

    @Test
    fun staleVersionIsTerminalAndDoesNotBlockTheRestOfTheBatch() {
        jdbcClient.sql(
            """
            INSERT INTO tasks (id, title, status, version, deleted_at_millis)
            VALUES
                ('task-stale', 'Server changed this', 'DOING', 5, NULL),
                ('task-next', 'Must still sync', 'TODO', 2, NULL)
            """.trimIndent(),
        ).update()

        val request =
            """
            {
              "operations": [
                {
                  "operationId": "stale-status",
                  "requestHash": "sha256:stale-status",
                  "taskId": "task-stale",
                  "type": "CHANGE_STATUS",
                  "desiredStatus": "DONE",
                  "baseServerVersion": 4
                },
                {
                  "operationId": "next-status",
                  "requestHash": "sha256:next-status",
                  "taskId": "task-next",
                  "type": "CHANGE_STATUS",
                  "desiredStatus": "DONE",
                  "baseServerVersion": 2
                }
              ]
            }
            """.trimIndent()

        mockMvc.post("/api/v1/sync/batch") {
            contentType = MediaType.APPLICATION_JSON
            content = request
        }.andExpect {
            status { isOk() }
            jsonPath("$.results[0].operationId", equalTo("stale-status"))
            jsonPath("$.results[0].status", equalTo("VERSION_CONFLICT"))
            jsonPath("$.results[0].serverVersion", equalTo(5))
            jsonPath("$.results[1].operationId", equalTo("next-status"))
            jsonPath("$.results[1].status", equalTo("APPLIED"))
            jsonPath("$.results[1].serverVersion", equalTo(3))
        }

        val staleStatus = jdbcClient.sql("SELECT status FROM tasks WHERE id = 'task-stale'")
            .query(String::class.java)
            .single()
        val nextStatus = jdbcClient.sql("SELECT status FROM tasks WHERE id = 'task-next'")
            .query(String::class.java)
            .single()
        val conflictReceipt = jdbcClient.sql(
            "SELECT response_status FROM sync_operations WHERE operation_id = 'stale-status'",
        ).query(String::class.java).single()

        assertEquals("DOING", staleStatus)
        assertEquals("DONE", nextStatus)
        assertEquals("VERSION_CONFLICT", conflictReceipt)
    }

    @Test
    fun titleUpdateUsesExpectedVersionAndDuplicateDeliveryIsIdempotent() {
        jdbcClient.sql(
            """
            INSERT INTO tasks (id, title, status, version, deleted_at_millis)
            VALUES ('task-update', 'Old title', 'TODO', 1, NULL)
            """.trimIndent(),
        ).update()

        val request =
            """
            {
              "operations": [
                {
                  "operationId": "update-operation-1",
                  "requestHash": "sha256:update-operation-1",
                  "taskId": "task-update",
                  "type": "UPDATE",
                  "title": "New offline title",
                  "baseServerVersion": 1
                }
              ]
            }
            """.trimIndent()

        repeat(2) {
            mockMvc.post("/api/v1/sync/batch") {
                contentType = MediaType.APPLICATION_JSON
                content = request
            }.andExpect {
                status { isOk() }
                jsonPath("$.results[0].operationId", equalTo("update-operation-1"))
                jsonPath("$.results[0].status", equalTo("APPLIED"))
                jsonPath("$.results[0].serverVersion", equalTo(2))
            }
        }

        val task = jdbcClient.sql(
            "SELECT title, version FROM tasks WHERE id = 'task-update'",
        ).query { resultSet, _ ->
            resultSet.getString("title") to resultSet.getLong("version")
        }.single()
        val operationCount = jdbcClient.sql(
            "SELECT COUNT(*) FROM sync_operations WHERE operation_id = 'update-operation-1'",
        ).query(Long::class.java).single()

        assertEquals("New offline title", task.first)
        assertEquals(2L, task.second)
        assertEquals(1L, operationCount)
    }

    companion object {
        private val deletedConflictRequest =
            """
            {
              "operations": [
                {
                  "operationId": "x-status",
                  "requestHash": "sha256:x-status",
                  "taskId": "task-x",
                  "type": "CHANGE_STATUS",
                  "desiredStatus": "DONE",
                  "baseServerVersion": 7
                },
                {
                  "operationId": "y-status",
                  "requestHash": "sha256:y-status",
                  "taskId": "task-y",
                  "type": "CHANGE_STATUS",
                  "desiredStatus": "DONE",
                  "baseServerVersion": 2
                }
              ]
            }
            """.trimIndent()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
    }
}
