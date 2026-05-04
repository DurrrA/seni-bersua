package com.durrr.first

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.netty.*
import io.ktor.server.request.host
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.*
import com.durrr.first.network.dto.ApiEnvelopeDto
import com.durrr.first.network.dto.AssignProductModifiersRequest
import com.durrr.first.network.dto.UpsertMenuItemRequest
import com.durrr.first.network.dto.UpsertModifierGroupRequest
import com.durrr.first.network.dto.TransactionBatchRequest
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

fun main() {
    val serverPort = EnvConfig.getInt("SUCASH_SERVER_PORT", SERVER_PORT)
    val serverHost = EnvConfig.get("SUCASH_SERVER_HOST", "0.0.0.0").orEmpty()
    embeddedServer(Netty, port = serverPort, host = serverHost, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    ServerDatabase.init()
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            if (call.request.path().startsWith("/api")) {
                call.respondApiError(
                    status = HttpStatusCode.BadRequest,
                    message = "Invalid API request payload",
                    error = cause.message ?: "Bad Request",
                )
            } else {
                call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad Request")
            }
        }
        status(HttpStatusCode.NotFound) { call, status ->
            if (call.request.path().startsWith("/api")) {
                call.respondApiError(
                    status = status,
                    message = "Route not found",
                    error = "Not Found",
                )
            } else {
                call.respond(status, "Not Found")
            }
        }
        exception<Throwable> { call, cause ->
            if (call.request.path().startsWith("/api")) {
                this@module.log.error(
                    "API request failed: ${call.request.path()}",
                    cause,
                )
                call.respondApiError(
                    status = HttpStatusCode.InternalServerError,
                    message = "Request failed",
                    error = cause.message ?: (cause::class.simpleName ?: "Unknown error"),
                )
            } else {
                this@module.log.error(
                    "Request failed: ${call.request.path()}",
                    cause,
                )
                call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            }
        }
    }

    routing {
        val webDistDir = resolveWebDistDirectory()
        if (webDistDir != null) {
            log.info("Serving React webapp from: ${webDistDir.absolutePath}")
            staticFiles("/web", webDistDir)
        } else {
            log.warn("React webapp dist not found. Falling back to bundled web resources.")
            get("/web/{path...}") {
                val path = call.parameters.getAll("path")
                    ?.joinToString("/")
                    .orEmpty()
                if (path.isBlank()) {
                    call.respond(HttpStatusCode.NotFound, "Missing resource path")
                    return@get
                }
                call.respondWebResource("web/$path", contentTypeForFile(path))
            }
        }

        get("/") {
            if (!call.request.isAllowedRootAccess()) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    "Root page is only accessible from local/private network.",
                )
                return@get
            }
            val subdomainUuid = call.request.extractUuidSubdomain()
            if (subdomainUuid != null && ServerDatabase.findCustomer(subdomainUuid) != null) {
                call.respondRedirect("/t/$subdomainUuid", permanent = false)
                return@get
            }
            call.respondWebIndex(webDistDir)
        }

        get("/dashboard") {
            call.respondWebIndex(webDistDir)
        }

        get("/t/{tableUuid}") {
            call.respondWebIndex(webDistDir)
        }

        get("/scan/{tableUuid}") {
            val tableUuid = call.parameters["tableUuid"].orEmpty()
            if (tableUuid.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing table UUID")
                return@get
            }
            call.respondRedirect("/t/$tableUuid", permanent = false)
        }

        route("/api") {
            get("/menu") {
                val outletId = call.request.queryParameters["outlet"]
                call.respondApiSuccess(
                    data = ServerDatabase.listMenu(outletId.orEmpty().ifBlank { "default" }),
                    message = "Menu fetched",
                )
            }

            get("/menu/catalog") {
                val outletId = call.request.queryParameters["outlet"]
                call.respondApiSuccess(
                    data = ServerDatabase.menuCatalog(outletId.orEmpty().ifBlank { "default" }),
                    message = "Menu catalog fetched",
                )
            }

            post("/menu/upsert") {
                val request = call.receiveApiRequest<UpsertMenuItemRequest>()
                val updated = ServerDatabase.upsertMenu(request)
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Menu upsert failed",
                        error = "Invalid menu payload",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Menu item saved")
                }
            }

            post("/menu/{id}/delete") {
                val id = call.parameters["id"].orEmpty()
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                if (id.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Menu delete failed",
                        error = "Missing menu id",
                    )
                    return@post
                }
                val deleted = ServerDatabase.deleteMenu(id, outletId)
                if (!deleted) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Menu delete failed",
                        error = "Menu not found",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Menu deleted")
                }
            }

            post("/menu/modifiers/upsert") {
                val request = call.receiveApiRequest<UpsertModifierGroupRequest>()
                val ok = ServerDatabase.upsertModifierGroup(request)
                if (!ok) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier group save failed",
                        error = "Invalid modifier group payload",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Modifier group saved")
                }
            }

            post("/menu/{id}/modifiers/assign") {
                val itemId = call.parameters["id"].orEmpty()
                val request = call.receiveApiRequest<AssignProductModifiersRequest>()
                if (itemId.isBlank()) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier assignment failed",
                        error = "Missing item id",
                    )
                    return@post
                }
                val ok = ServerDatabase.assignProductModifiers(itemId, request)
                if (!ok) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Modifier assignment failed",
                        error = "Invalid modifier assignment payload",
                    )
                } else {
                    call.respondApiSuccess(data = true, message = "Modifier assignment saved")
                }
            }

            get("/customers") {
                call.respondApiSuccess(data = ServerDatabase.listCustomers(), message = "Customers fetched")
            }

            post("/customers/seed-tables") {
                val request = call.receiveApiRequest<SeedTablesRequest>()
                val seeded = ServerDatabase.seedTableCustomers(
                    count = request.count,
                    outletId = request.outletId.orEmpty().ifBlank { "default" },
                )
                call.respondApiSuccess(
                    data = seeded,
                    message = "Table customers seeded",
                )
            }

            get("/tables") {
                call.respondApiSuccess(data = ServerDatabase.listCustomers(), message = "Tables fetched")
            }

            post("/tables/seed") {
                val request = call.receiveApiRequest<SeedTablesRequest>()
                val seeded = ServerDatabase.seedTableCustomers(
                    count = request.count,
                    outletId = request.outletId.orEmpty().ifBlank { "default" },
                )
                call.respondApiSuccess(
                    data = seeded,
                    message = "Tables seeded",
                )
            }

            get("/tables/{uuid}") {
                val uuid = call.parameters["uuid"].orEmpty()
                val table = ServerDatabase.findCustomer(uuid)
                if (table == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Table fetch failed",
                        error = "Table not found",
                    )
                } else {
                    call.respondApiSuccess(data = table, message = "Table fetched")
                }
            }

            get("/customers/{uuid}") {
                val uuid = call.parameters["uuid"].orEmpty()
                val customer = ServerDatabase.findCustomer(uuid)
                if (customer == null) {
                    call.respondApiError(
                        status = HttpStatusCode.NotFound,
                        message = "Customer fetch failed",
                        error = "Customer not found",
                    )
                } else {
                    call.respondApiSuccess(data = customer, message = "Customer fetched")
                }
            }

            get("/orders") {
                val statuses = call.request.queryParameters["status"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                call.respondApiSuccess(
                    data = ServerDatabase.listOrders(statuses, outletId),
                    message = "Orders fetched",
                )
            }

            post("/orders") {
                val request = call.receiveApiRequest<CreateOrderRequest>()
                val order = ServerDatabase.createOrder(request)
                if (order == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order creation failed",
                        error = "Invalid order request",
                    )
                } else {
                    call.respondApiSuccess(
                        data = order,
                        message = "Order created",
                        status = HttpStatusCode.Created,
                    )
                }
            }

            post("/orders/{id}/status") {
                val orderId = call.parameters["id"].orEmpty()
                val body = call.receiveApiRequest<UpdateOrderStatusRequest>()
                val updated = ServerDatabase.updateOrderStatus(
                    orderId = orderId,
                    status = body.status,
                    outletId = body.outletId.orEmpty().ifBlank { "default" },
                )
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order status update failed",
                        error = "Cannot update order status",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Order status updated")
                }
            }

            post("/orders/{id}/accept") {
                val orderId = call.parameters["id"].orEmpty()
                val updated = ServerDatabase.updateOrderStatus(orderId, "ACCEPTED", "default")
                if (updated == null) {
                    call.respondApiError(
                        status = HttpStatusCode.BadRequest,
                        message = "Order accept failed",
                        error = "Cannot accept order",
                    )
                } else {
                    call.respondApiSuccess(data = updated, message = "Order accepted")
                }
            }

            post("/sync/transactions/batch") {
                val request = call.receiveApiRequest<TransactionBatchRequest>()
                call.respondApiSuccess(
                    data = ServerDatabase.syncTransactionsBatch(request),
                    message = "Transaction sync processed",
                )
            }

            get("/recap/daily") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                call.respondApiSuccess(
                    data = ServerDatabase.dailyRecap(date, outletId),
                    message = "Daily recap fetched",
                )
            }

            get("/recap/summary") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val range = call.request.queryParameters["range"]
                    ?.takeIf { it.isNotBlank() }
                    ?: "TODAY"
                val fromDate = call.request.queryParameters["from"]
                    ?.takeIf { it.isNotBlank() }
                val toDate = call.request.queryParameters["to"]
                    ?.takeIf { it.isNotBlank() }
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                call.respondApiSuccess(
                    data = ServerDatabase.recapSummary(
                        range = range,
                        date = date,
                        fromDate = fromDate,
                        toDate = toDate,
                        outletId = outletId,
                    ),
                    message = "Recap summary fetched",
                )
            }

            post("/admin/reset-all") {
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                val ok = ServerDatabase.resetOutletData(outletId)
                if (ok) {
                    call.respondApiSuccess(
                        data = true,
                        message = "Reset done for outlet=$outletId",
                    )
                } else {
                    call.respondApiError(
                        status = HttpStatusCode.InternalServerError,
                        message = "Reset failed",
                        error = "Failed to reset outlet=$outletId",
                    )
                }
            }
        }
    }
}

private suspend fun ApplicationCall.respondWebIndex(webDistDir: File?) {
    val distIndex = webDistDir?.resolve("index.html")
    if (distIndex?.exists() == true) {
        respondFile(distIndex)
        return
    }
    respondWebResource("web/index.html", ContentType.Text.Html)
}

private suspend fun ApplicationCall.respondWebResource(path: String, contentType: ContentType) {
    val body = this::class.java.classLoader
        .getResource(path)
        ?.readText()
    if (body == null) {
        respond(HttpStatusCode.NotFound, "Resource not found: $path")
        return
    }
    respondText(body, contentType = contentType)
}

private fun resolveWebDistDirectory(): File? {
    val configuredPath = EnvConfig.get("SUCASH_WEBAPP_DIST", "")
        .orEmpty()
        .trim()
    if (configuredPath.isBlank()) {
        return null
    }
    val configuredFile = File(configuredPath)
    val resolved = if (configuredFile.isAbsolute) {
        configuredFile
    } else {
        File(System.getProperty("user.dir"), configuredPath)
    }
    return resolved.takeIf { it.exists() && it.isDirectory }
}

private fun contentTypeForFile(path: String): ContentType {
    return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "js", "mjs" -> ContentType.Application.JavaScript
        "css" -> ContentType.Text.CSS
        "html" -> ContentType.Text.Html
        "json" -> ContentType.Application.Json
        "svg" -> ContentType.Image.SVG
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "webp" -> ContentType.parse("image/webp")
        "ico" -> ContentType.parse("image/x-icon")
        else -> ContentType.Application.OctetStream
    }
}

private val apiJsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class SeedTablesRequest(
    val count: Int = 10,
    @SerialName("outlet_id")
    val outletId: String? = null,
)

private suspend inline fun <reified T> ApplicationCall.respondApiSuccess(
    data: T? = null,
    message: String = "OK",
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respond(
        status,
        ApiEnvelopeDto(
            data = data,
            message = message,
            error = null,
        ),
    )
}

private suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    message: String,
    error: String? = null,
) {
    respond(
        status,
        ApiEnvelopeDto<Unit>(
            data = null,
            message = message,
            error = error,
        ),
    )
}

private suspend inline fun <reified T> ApplicationCall.receiveApiRequest(): T {
    val rawBody = receiveText()
    if (rawBody.isBlank()) {
        throw IllegalArgumentException("Request body cannot be empty")
    }
    val envelope = try {
        apiJsonParser.decodeFromString<ApiEnvelopeDto<T>>(rawBody)
    } catch (_: SerializationException) {
        throw IllegalArgumentException("Request body must use envelope format {data,message,error}")
    }
    val payload = envelope.data
    if (payload == null) {
        throw IllegalArgumentException("Envelope data is required")
    }
    if (!envelope.error.isNullOrBlank()) {
        throw IllegalArgumentException("Request envelope error must be null")
    }
    return payload
}

private fun io.ktor.server.request.ApplicationRequest.extractUuidSubdomain(): String? {
    val host = headers["Host"]
        ?.substringBefore(':')
        ?.lowercase()
        ?: return null
    val firstLabel = when {
        host.endsWith(".localhost") -> host.removeSuffix(".localhost").substringBefore(".")
        host.contains(".") -> host.substringBefore(".")
        else -> return null
    }
    return runCatching { UUID.fromString(firstLabel) }.getOrNull()?.toString()
}

private fun io.ktor.server.request.ApplicationRequest.isAllowedRootAccess(): Boolean {
    val requestHost = host()
        .substringBefore(':')
        .lowercase()
    val serverHost = EnvConfig.get("SUCASH_SERVER_HOST", "0.0.0.0")
        .orEmpty()
        .substringBefore(':')
        .lowercase()
        .trim()

    if (requestHost == "localhost" || requestHost == "127.0.0.1" || requestHost == "::1") {
        return true
    }
    if (serverHost.isNotBlank() && serverHost != "0.0.0.0" && requestHost == serverHost) {
        return true
    }
    return requestHost.isPrivateIpv4Address()
}

private fun String.isPrivateIpv4Address(): Boolean {
    val parts = split('.')
    if (parts.size != 4) return false
    val numbers = parts.map { it.toIntOrNull() ?: return false }
    val a = numbers[0]
    val b = numbers[1]
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        else -> false
    }
}
