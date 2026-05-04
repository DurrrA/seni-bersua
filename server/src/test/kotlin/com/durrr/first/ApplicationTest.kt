package com.durrr.first

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    private fun envelope(
        dataJson: String,
        message: String = "test request",
    ): String = """{"data":$dataJson,"message":"$message","error":null}"""

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("SuCash Web"))
    }

    @Test
    fun testMenuUpsertRoute() = testApplication {
        application {
            module()
        }
        val response = client.post("/api/menu/upsert") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """{"id":"menu-test-case","name":"Menu Test Case","price":9900}""",
                    "Menu upsert request",
                )
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("menu-test-case"))
    }

    @Test
    fun testTransactionBatchSyncAndRecapRoute() = testApplication {
        application {
            module()
        }
        val payload = """
            {
              "outbox_ids": ["evt-test-application"],
              "transaksi": [
                {
                  "id": "trx-test-application",
                  "created_at": "2026-03-14T08:00:00",
                  "meja": "table-1",
                  "discount_plus": 0,
                  "tax": 0,
                  "service_charge": 0,
                  "rounding": 0,
                  "total": 7777,
                  "details": [
                    {
                      "id": "det-test-application",
                      "item_id": "menu-test-case",
                      "item_name": "Menu Test Case",
                      "qty": 1,
                      "price": 7777,
                      "discount": 0,
                      "total": 7777
                    }
                  ],
                  "pembayaran": {
                    "id": "pay-test-application",
                    "paid_at": "2026-03-14T08:01:00",
                    "amount_paid": 10000,
                    "change": 2223,
                    "payment_type_id": "CASH"
                  }
                }
              ]
            }
        """.trimIndent()

        val syncResponse = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, syncResponse.status)
        assertTrue(syncResponse.bodyAsText().contains("evt-test-application"))

        val recapResponse = client.get("/api/recap/daily?date=2026-03-14")
        assertEquals(HttpStatusCode.OK, recapResponse.status)
        assertTrue(recapResponse.bodyAsText().contains("\"date\": \"2026-03-14\""))
    }

    @Test
    fun testCreateOrderWithPaymentConfirmation() = testApplication {
        application {
            module()
        }
        val payload = """
            {
              "customerUuid": "9a8a7f0e-95d8-4b5b-83ec-2ef5dd4fe1d1",
              "paymentConfirmation": "CASHIER",
              "note": "test payment confirmation",
              "items": [
                {
                  "menuId": "menu-espresso",
                  "qty": 1
                }
              ]
            }
        """.trimIndent()
        val response = client.post("/api/orders") {
            contentType(ContentType.Application.Json)
            setBody(envelope(payload, "Create order request"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"paymentConfirmation\": \"CASHIER\""))
    }

    @Test
    fun testTransactionBatchSyncWithEventEnvelopeAndIdempotency() = testApplication {
        application {
            module()
        }
        val payload = """
            {
              "events": [
                {
                  "event_id": "evt-envelope-1",
                  "entity_type": "TRANSAKSI_CHECKOUT",
                  "op": "UPSERT",
                  "payload_json": "{\"id\":\"trx-envelope-1\",\"created_at\":\"2026-03-14T09:00:00\",\"meja\":\"table-2\",\"discount_plus\":0,\"tax\":0,\"service_charge\":0,\"rounding\":0,\"total\":10000,\"details\":[{\"id\":\"det-envelope-1\",\"item_id\":\"menu-espresso\",\"item_name\":\"Espresso\",\"qty\":1,\"price\":10000,\"discount\":0,\"total\":10000}],\"pembayaran\":{\"id\":\"pay-envelope-1\",\"paid_at\":\"2026-03-14T09:01:00\",\"amount_paid\":12000,\"change\":2000,\"payment_type_id\":\"CASH\"}}",
                  "created_at": "2026-03-14T09:00:00"
                }
              ],
              "outlet_id": "default"
            }
        """.trimIndent()

        val firstSync = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, firstSync.status)
        assertTrue(firstSync.bodyAsText().contains("\"event_id\": \"evt-envelope-1\""))
        assertTrue(firstSync.bodyAsText().contains("\"status\": \"ACCEPTED\""))

        val secondSync = client.post("/api/sync/transactions/batch") {
            contentType(ContentType.Application.Json)
            setBody(envelope(payload, "Transaction sync batch request"))
        }
        assertEquals(HttpStatusCode.OK, secondSync.status)
        assertTrue(secondSync.bodyAsText().contains("Already processed"))
    }

    @Test
    fun testMenuCatalogSupportsModifierCustomization() = testApplication {
        application {
            module()
        }

        val upsertModifierPayload = """
            {
              "id": "mod-size",
              "name": "Drink Size",
              "selection_type": "SINGLE",
              "is_required": true,
              "max_selection": 1,
              "options": [
                { "id": "mod-size-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                { "id": "mod-size-large", "name": "Large", "priceDelta": 3000, "order": 2, "isDefault": false }
              ],
              "outlet_id": "default"
            }
        """.trimIndent()
        val upsertModifierResponse = client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            setBody(envelope(upsertModifierPayload, "Upsert modifier group request"))
        }
        assertEquals(HttpStatusCode.OK, upsertModifierResponse.status)

        val assignPayload = """
            {
              "modifier_group_ids": ["mod-size"],
              "outlet_id": "default"
            }
        """.trimIndent()
        val assignResponse = client.post("/api/menu/menu-espresso/modifiers/assign") {
            contentType(ContentType.Application.Json)
            setBody(envelope(assignPayload, "Assign product modifiers request"))
        }
        assertEquals(HttpStatusCode.OK, assignResponse.status)

        val catalogResponse = client.get("/api/menu/catalog?outlet=default")
        assertEquals(HttpStatusCode.OK, catalogResponse.status)
        val body = catalogResponse.bodyAsText()
        assertTrue(body.contains("\"modifierGroups\""))
        assertTrue(body.contains("\"mod-size\""))
        assertTrue(body.contains("\"productModifierLinks\""))
        assertTrue(body.contains("\"menu-espresso\""))
    }

    @Test
    fun testCreateOrderWithLineModifiersAndLineNote() = testApplication {
        application {
            module()
        }

        client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                {
                  "id": "mod-sugar",
                  "name": "Sugar Level",
                  "selection_type": "SINGLE",
                  "is_required": true,
                  "max_selection": 1,
                  "options": [
                    { "id": "mod-sugar-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                    { "id": "mod-sugar-less", "name": "Less", "priceDelta": 0, "order": 2, "isDefault": false }
                  ],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Upsert modifier group request",
                )
            )
        }
        client.post("/api/menu/modifiers/upsert") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                {
                  "id": "mod-size",
                  "name": "Drink Size",
                  "selection_type": "SINGLE",
                  "is_required": true,
                  "max_selection": 1,
                  "options": [
                    { "id": "mod-size-normal", "name": "Normal", "priceDelta": 0, "order": 1, "isDefault": true },
                    { "id": "mod-size-large", "name": "Large", "priceDelta": 3000, "order": 2, "isDefault": false }
                  ],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Upsert modifier group request",
                )
            )
        }
        client.post("/api/menu/menu-espresso/modifiers/assign") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                {
                  "modifier_group_ids": ["mod-sugar", "mod-size"],
                  "outlet_id": "default"
                }
                """.trimIndent(),
                    "Assign product modifiers request",
                )
            )
        }

        val createOrderResponse = client.post("/api/orders") {
            contentType(ContentType.Application.Json)
            setBody(
                envelope(
                    """
                {
                  "customerUuid": "9a8a7f0e-95d8-4b5b-83ec-2ef5dd4fe1d1",
                  "paymentConfirmation": "CASHIER",
                  "items": [
                    {
                      "menuId": "menu-espresso",
                      "qty": 1,
                      "note": "No stirrer",
                      "modifiers": [
                        { "optionId": "mod-sugar-normal" },
                        { "optionId": "mod-size-large" }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                    "Create order request",
                )
            )
        }

        assertEquals(HttpStatusCode.Created, createOrderResponse.status)
        val body = createOrderResponse.bodyAsText()
        assertTrue(body.contains("\"paymentConfirmation\": \"CASHIER\""))
        assertTrue(body.contains("\"lineTotal\": 21000"))
        assertTrue(body.contains("Sugar Level: Normal"))
        assertTrue(body.contains("Drink Size: Large"))
    }
}
