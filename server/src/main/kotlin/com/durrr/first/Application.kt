package com.durrr.first

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.netty.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.*
import com.durrr.first.network.dto.UpsertMenuItemRequest
import com.durrr.first.network.dto.TransactionBatchRequest
import java.time.LocalDate
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

    routing {
        get("/") {
            val subdomainUuid = call.request.extractUuidSubdomain()
            if (subdomainUuid != null && ServerDatabase.findCustomer(subdomainUuid) != null) {
                call.respondRedirect("/t/$subdomainUuid", permanent = false)
                return@get
            }
            call.respondWebIndex()
        }

        get("/dashboard") {
            call.respondWebIndex()
        }

        get("/t/{customerUuid}") {
            call.respondWebIndex()
        }

        get("/scan/{customerUuid}") {
            val customerUuid = call.parameters["customerUuid"].orEmpty()
            if (customerUuid.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing customer UUID")
                return@get
            }
            call.respondRedirect("/t/$customerUuid", permanent = false)
        }

        get("/web/app.js") {
            call.respondWebResource("web/app.js", ContentType.Application.JavaScript)
        }

        get("/web/styles.css") {
            call.respondWebResource("web/styles.css", ContentType.Text.CSS)
        }

        route("/api") {
            get("/menu") {
                val outletId = call.request.queryParameters["outlet"]
                call.respond(ServerDatabase.listMenu(outletId.orEmpty().ifBlank { "default" }))
            }

            post("/menu/upsert") {
                val request = call.receive<UpsertMenuItemRequest>()
                val updated = ServerDatabase.upsertMenu(request)
                if (updated == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid menu payload")
                } else {
                    call.respond(updated)
                }
            }

            post("/menu/{id}/delete") {
                val id = call.parameters["id"].orEmpty()
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                if (id.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing menu id")
                    return@post
                }
                val deleted = ServerDatabase.deleteMenu(id, outletId)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, "Menu not found")
                } else {
                    call.respond(HttpStatusCode.OK, "Deleted")
                }
            }

            get("/customers") {
                call.respond(ServerDatabase.listCustomers())
            }

            get("/customers/{uuid}") {
                val uuid = call.parameters["uuid"].orEmpty()
                val customer = ServerDatabase.findCustomer(uuid)
                if (customer == null) {
                    call.respond(HttpStatusCode.NotFound, "Customer not found")
                } else {
                    call.respond(customer)
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
                call.respond(ServerDatabase.listOrders(statuses, outletId))
            }

            post("/orders") {
                val request = call.receive<CreateOrderRequest>()
                val order = ServerDatabase.createOrder(request)
                if (order == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid order request")
                } else {
                    call.respond(HttpStatusCode.Created, order)
                }
            }

            post("/orders/{id}/status") {
                val orderId = call.parameters["id"].orEmpty()
                val body = call.receive<UpdateOrderStatusRequest>()
                val updated = ServerDatabase.updateOrderStatus(
                    orderId = orderId,
                    status = body.status,
                    outletId = body.outletId.orEmpty().ifBlank { "default" },
                )
                if (updated == null) {
                    call.respond(HttpStatusCode.BadRequest, "Cannot update order status")
                } else {
                    call.respond(updated)
                }
            }

            post("/orders/{id}/accept") {
                val orderId = call.parameters["id"].orEmpty()
                val updated = ServerDatabase.updateOrderStatus(orderId, "ACCEPTED", "default")
                if (updated == null) {
                    call.respond(HttpStatusCode.BadRequest, "Cannot accept order")
                } else {
                    call.respond(updated)
                }
            }

            post("/sync/transactions/batch") {
                val request = call.receive<TransactionBatchRequest>()
                call.respond(ServerDatabase.syncTransactionsBatch(request))
            }

            get("/recap/daily") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                call.respond(ServerDatabase.dailyRecap(date, outletId))
            }

            get("/recap/summary") {
                val date = call.request.queryParameters["date"]
                    ?.takeIf { it.isNotBlank() }
                    ?: LocalDate.now().toString()
                val range = call.request.queryParameters["range"]
                    ?.takeIf { it.isNotBlank() }
                    ?: "TODAY"
                val outletId = call.request.queryParameters["outlet"].orEmpty().ifBlank { "default" }
                call.respond(
                    ServerDatabase.recapSummary(
                        range = range,
                        date = date,
                        outletId = outletId,
                    )
                )
            }
        }
    }
}

private suspend fun ApplicationCall.respondWebIndex() {
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
