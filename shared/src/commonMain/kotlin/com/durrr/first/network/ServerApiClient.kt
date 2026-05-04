package com.durrr.first.network

import com.durrr.first.network.dto.DailyRecapResponse
import com.durrr.first.network.dto.RecapRangeDto
import com.durrr.first.network.dto.RecapSummaryResponse
import com.durrr.first.network.dto.ApiEnvelopeDto
import com.durrr.first.network.dto.AssignProductModifiersRequest
import com.durrr.first.network.dto.ServerMenuCatalogDto
import com.durrr.first.network.dto.ServerMenuItemDto
import com.durrr.first.network.dto.UpsertModifierGroupRequest
import com.durrr.first.network.dto.ServerOrderDto
import com.durrr.first.network.dto.ServerOrderStatus
import com.durrr.first.network.dto.ServerOrderStatusRequest
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransactionBatchResponse
import com.durrr.first.network.dto.UpsertMenuItemRequest
import com.durrr.first.domain.model.RecapRange
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class ServerApiException(
    val statusCode: Int,
    val endpoint: String,
    val apiMessage: String? = null,
    val apiError: String? = null,
    cause: Throwable? = null,
) : Exception(
    buildString {
        append("API request failed [")
        append(statusCode)
        append("] ")
        append(endpoint)
        if (!apiError.isNullOrBlank()) {
            append(": ")
            append(apiError)
        } else if (!apiMessage.isNullOrBlank()) {
            append(": ")
            append(apiMessage)
        }
    },
    cause,
)

class ServerApiClient {
    private val parser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(HttpRequestRetry) {
            maxRetries = 2
            retryOnServerErrors(maxRetries)
            exponentialDelay(base = 300.0, maxDelayMs = 2_000)
            retryIf { _, response ->
                response.status.value >= 500
            }
            retryOnExceptionIf { _, cause ->
                val message = cause.message.orEmpty()
                message.contains("unexpected end of stream", ignoreCase = true) ||
                    message.contains("connection reset", ignoreCase = true) ||
                    message.contains("broken pipe", ignoreCase = true)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(parser)
        }
    }

    suspend fun fetchMenu(baseUrl: String, outletId: String? = null): List<ServerMenuItemDto> {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu"
        return client.get("$url/api/menu") {
            header(HttpHeaders.Connection, "close")
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.unwrapApiData(endpoint)
    }

    suspend fun fetchMenuCatalog(baseUrl: String, outletId: String? = null): ServerMenuCatalogDto {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu/catalog"
        return client.get("$url/api/menu/catalog") {
            header(HttpHeaders.Connection, "close")
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.unwrapApiData(endpoint)
    }

    suspend fun upsertMenuItem(
        baseUrl: String,
        id: String?,
        name: String,
        price: Long,
        groupId: String? = null,
        groupName: String? = null,
        outletId: String? = null,
    ): ServerMenuItemDto {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu/upsert"
        return client.post("$url/api/menu/upsert") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                ApiEnvelopeDto(
                    data = UpsertMenuItemRequest(
                        id = id,
                        name = name,
                        price = price,
                        groupId = groupId,
                        groupName = groupName,
                        outletId = outletId,
                    ),
                    message = "Upsert menu item request",
                )
            )
        }.unwrapApiData(endpoint)
    }

    suspend fun deleteMenuItem(baseUrl: String, id: String, outletId: String? = null) {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu/$id/delete"
        client.post("$url/api/menu/$id/delete") {
            header(HttpHeaders.Connection, "close")
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.ensureApiSuccess(endpoint)
    }

    suspend fun upsertModifierGroup(
        baseUrl: String,
        request: UpsertModifierGroupRequest,
    ) {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu/modifiers/upsert"
        client.post("$url/api/menu/modifiers/upsert") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                ApiEnvelopeDto(
                    data = request,
                    message = "Upsert modifier group request",
                )
            )
        }.ensureApiSuccess(endpoint)
    }

    suspend fun assignProductModifiers(
        baseUrl: String,
        itemId: String,
        request: AssignProductModifiersRequest,
    ) {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/menu/$itemId/modifiers/assign"
        client.post("$url/api/menu/$itemId/modifiers/assign") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                ApiEnvelopeDto(
                    data = request,
                    message = "Assign product modifiers request",
                )
            )
        }.ensureApiSuccess(endpoint)
    }

    suspend fun fetchOrders(
        baseUrl: String,
        statuses: List<ServerOrderStatus>,
        outletId: String? = null,
    ): List<ServerOrderDto> {
        val url = normalizeBaseUrl(baseUrl)
        val statusFilter = statuses.joinToString(",") { it.name }
        val endpoint = "$url/api/orders"
        return client.get("$url/api/orders") {
            header(HttpHeaders.Connection, "close")
            parameter("status", statusFilter)
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.unwrapApiData(endpoint)
    }

    suspend fun updateOrderStatus(
        baseUrl: String,
        orderId: String,
        status: ServerOrderStatus,
        outletId: String? = null,
    ): ServerOrderDto {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/orders/$orderId/status"
        return client.post("$url/api/orders/$orderId/status") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                ApiEnvelopeDto(
                    data = ServerOrderStatusRequest(status.name, outletId),
                    message = "Update order status request",
                )
            )
        }.unwrapApiData(endpoint)
    }

    suspend fun syncTransactions(
        baseUrl: String,
        request: TransactionBatchRequest,
    ): TransactionBatchResponse {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/sync/transactions/batch"
        return client.post("$url/api/sync/transactions/batch") {
            header(HttpHeaders.Connection, "close")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                ApiEnvelopeDto(
                    data = request,
                    message = "Transaction sync batch request",
                )
            )
        }.unwrapApiData(endpoint)
    }

    suspend fun getDailyRecap(baseUrl: String, date: String, outletId: String? = null): DailyRecapResponse {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/recap/daily"
        return client.get("$url/api/recap/daily") {
            header(HttpHeaders.Connection, "close")
            parameter("date", date)
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.unwrapApiData(endpoint)
    }

    suspend fun getRecapSummary(
        baseUrl: String,
        range: RecapRange,
        date: String,
        outletId: String? = null,
    ): RecapSummaryResponse {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/recap/summary"
        return client.get("$url/api/recap/summary") {
            header(HttpHeaders.Connection, "close")
            parameter("date", date)
            parameter("range", range.toDto().name)
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.unwrapApiData(endpoint)
    }

    suspend fun resetAllData(
        baseUrl: String,
        outletId: String? = null,
    ) {
        val url = normalizeBaseUrl(baseUrl)
        val endpoint = "$url/api/admin/reset-all"
        client.post("$url/api/admin/reset-all") {
            header(HttpHeaders.Connection, "close")
            if (!outletId.isNullOrBlank()) {
                parameter("outlet", outletId)
            }
        }.ensureApiSuccess(endpoint)
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    private fun RecapRange.toDto(): RecapRangeDto {
        return when (this) {
            RecapRange.TODAY -> RecapRangeDto.TODAY
            RecapRange.WEEK -> RecapRangeDto.WEEK
            RecapRange.MONTH -> RecapRangeDto.MONTH
            RecapRange.ALL -> RecapRangeDto.ALL
        }
    }

    private suspend inline fun <reified T> HttpResponse.unwrapApiData(endpoint: String): T {
        val envelope = decodeApiEnvelope<T>(endpoint)
        if (status.value !in 200..299 || !envelope.error.isNullOrBlank()) {
            throw ServerApiException(
                statusCode = status.value,
                endpoint = endpoint,
                apiMessage = envelope.message,
                apiError = envelope.error,
            )
        }
        val payload = envelope.data
        if (payload == null) {
            throw ServerApiException(
                statusCode = status.value,
                endpoint = endpoint,
                apiMessage = envelope.message.ifBlank { "Server returned empty data payload." },
                apiError = envelope.error,
            )
        }
        return payload
    }

    private suspend fun HttpResponse.ensureApiSuccess(endpoint: String) {
        val envelope = decodeApiEnvelope<JsonElement?>(endpoint)
        if (status.value !in 200..299 || !envelope.error.isNullOrBlank()) {
            throw ServerApiException(
                statusCode = status.value,
                endpoint = endpoint,
                apiMessage = envelope.message,
                apiError = envelope.error,
            )
        }
    }

    private suspend inline fun <reified T> HttpResponse.decodeApiEnvelope(
        endpoint: String,
    ): ApiEnvelopeDto<T> {
        val rawBody = bodyAsText()
        if (rawBody.isBlank()) {
            throw ServerApiException(
                statusCode = status.value,
                endpoint = endpoint,
                apiMessage = "Empty API response body",
            )
        }
        return try {
            parser.decodeFromString<ApiEnvelopeDto<T>>(rawBody)
        } catch (error: SerializationException) {
            throw ServerApiException(
                statusCode = status.value,
                endpoint = endpoint,
                apiMessage = "Invalid API envelope format",
                apiError = rawBody.take(220),
                cause = error,
            )
        }
    }
}
