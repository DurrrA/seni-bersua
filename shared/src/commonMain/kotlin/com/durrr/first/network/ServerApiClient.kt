package com.durrr.first.network

import com.durrr.first.network.dto.DailyRecapResponse
import com.durrr.first.network.dto.ServerMenuItemDto
import com.durrr.first.network.dto.ServerOrderDto
import com.durrr.first.network.dto.ServerOrderStatus
import com.durrr.first.network.dto.ServerOrderStatusRequest
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransactionBatchResponse
import com.durrr.first.network.dto.UpsertMenuItemRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ServerApiClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }
    }

    suspend fun fetchMenu(baseUrl: String, outletId: String? = null): List<ServerMenuItemDto> {
        val url = normalizeBaseUrl(baseUrl)
        return client.get("$url/api/menu") {
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.body()
    }

    suspend fun upsertMenuItem(
        baseUrl: String,
        id: String?,
        name: String,
        price: Long,
        outletId: String? = null,
    ): ServerMenuItemDto {
        val url = normalizeBaseUrl(baseUrl)
        return client.post("$url/api/menu/upsert") {
            setBody(
                UpsertMenuItemRequest(
                    id = id,
                    name = name,
                    price = price,
                    outletId = outletId,
                )
            )
        }.body()
    }

    suspend fun deleteMenuItem(baseUrl: String, id: String, outletId: String? = null) {
        val url = normalizeBaseUrl(baseUrl)
        client.post("$url/api/menu/$id/delete") {
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }
    }

    suspend fun fetchOrders(
        baseUrl: String,
        statuses: List<ServerOrderStatus>,
        outletId: String? = null,
    ): List<ServerOrderDto> {
        val url = normalizeBaseUrl(baseUrl)
        val statusFilter = statuses.joinToString(",") { it.name }
        return client.get("$url/api/orders") {
            parameter("status", statusFilter)
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.body()
    }

    suspend fun updateOrderStatus(
        baseUrl: String,
        orderId: String,
        status: ServerOrderStatus,
        outletId: String? = null,
    ): ServerOrderDto {
        val url = normalizeBaseUrl(baseUrl)
        return client.post("$url/api/orders/$orderId/status") {
            setBody(ServerOrderStatusRequest(status.name, outletId))
        }.body()
    }

    suspend fun syncTransactions(
        baseUrl: String,
        request: TransactionBatchRequest,
    ): TransactionBatchResponse {
        val url = normalizeBaseUrl(baseUrl)
        return client.post("$url/api/sync/transactions/batch") {
            setBody(request)
        }.body()
    }

    suspend fun getDailyRecap(baseUrl: String, date: String, outletId: String? = null): DailyRecapResponse {
        val url = normalizeBaseUrl(baseUrl)
        return client.get("$url/api/recap/daily") {
            parameter("date", date)
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.body()
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }
}
