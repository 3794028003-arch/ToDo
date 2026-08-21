package com.example.localfirst.network

import com.example.localfirst.sync.OperationType
import com.example.localfirst.sync.PushResult
import com.example.localfirst.sync.SyncOperation
import com.example.localfirst.sync.TaskStatus
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RetrofitSyncApiTest {
    @get:Rule
    val server = MockWebServer()

    @Test
    fun `batch request matches backend contract and terminal responses map to domain`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "results": [
                        {
                          "operationId": "create-1",
                          "status": "APPLIED",
                          "serverVersion": 1
                        },
                        {
                          "operationId": "deleted-1",
                          "status": "TASK_DELETED",
                          "tombstoneVersion": 9,
                          "deletedAtMillis": 5000
                        },
                        {
                          "operationId": "conflict-1",
                          "status": "VERSION_CONFLICT",
                          "serverVersion": 6
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val api = RetrofitSyncApi.create(server.url("/").toString())
        val operations = listOf(
            operation(
                operationId = "create-1",
                taskId = "task-create",
                type = OperationType.CREATE,
                title = "Created offline",
            ),
            operation(
                operationId = "deleted-1",
                taskId = "task-deleted",
                type = OperationType.CHANGE_STATUS,
                desiredStatus = TaskStatus.DONE,
                baseServerVersion = 8,
            ),
            operation(
                operationId = "conflict-1",
                taskId = "task-conflict",
                type = OperationType.UPDATE,
                title = "Edited offline",
                baseServerVersion = 5,
            ),
        )

        val results = api.push(operations)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/sync/batch", request.path)
        val requestOperations = JsonParser.parseString(request.body.readUtf8())
            .asJsonObject["operations"]
            .asJsonArray
        assertEquals(3, requestOperations.size())
        assertEquals("CREATE", requestOperations[0].asJsonObject["type"].asString)
        assertEquals("Created offline", requestOperations[0].asJsonObject["title"].asString)
        assertEquals("DONE", requestOperations[1].asJsonObject["desiredStatus"].asString)
        assertEquals(8L, requestOperations[1].asJsonObject["baseServerVersion"].asLong)
        val requestHash = requestOperations[0].asJsonObject["requestHash"].asString
        assertFalse(requestHash.isBlank())
        assertEquals(64, requestHash.length)
        assertTrue(requestHash.all { character -> character in '0'..'9' || character in 'a'..'f' })

        assertEquals(
            listOf(
                PushResult.Applied("create-1", serverVersion = 1),
                PushResult.ServerDeleted(
                    operationId = "deleted-1",
                    deletedAtMillis = 5_000,
                    tombstoneVersion = 9,
                ),
                PushResult.VersionConflict("conflict-1", serverVersion = 6),
            ),
            results,
        )
    }

    private fun operation(
        operationId: String,
        taskId: String,
        type: OperationType,
        title: String? = null,
        desiredStatus: TaskStatus? = null,
        baseServerVersion: Long? = null,
    ): SyncOperation = SyncOperation(
        operationId = operationId,
        taskId = taskId,
        queueSequence = 1,
        taskRevision = 1,
        type = type,
        title = title,
        desiredStatus = desiredStatus,
        baseServerVersion = baseServerVersion,
    )
}
