package com.example.localfirst.network

import com.example.localfirst.sync.PushResult
import com.example.localfirst.sync.SyncApi
import com.example.localfirst.sync.SyncOperation
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class RetrofitSyncApi private constructor(
    private val service: SyncBatchService,
) : SyncApi {
    override suspend fun push(operations: List<SyncOperation>): List<PushResult> =
        service.pushBatch(
            SyncBatchRequest(operations.map(SyncOperation::toRequest)),
        ).results.map(SyncOperationResponse::toDomain)

    companion object {
        fun create(baseUrl: String): RetrofitSyncApi {
            val service = Retrofit.Builder()
                .baseUrl(baseUrl.ensureTrailingSlash())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SyncBatchService::class.java)
            return RetrofitSyncApi(service)
        }
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

private interface SyncBatchService {
    @POST("api/v1/sync/batch")
    suspend fun pushBatch(@Body request: SyncBatchRequest): SyncBatchResponse
}
