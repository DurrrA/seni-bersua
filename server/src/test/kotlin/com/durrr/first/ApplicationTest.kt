package com.durrr.first

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

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
            setBody("""{"id":"menu-test-case","name":"Menu Test Case","price":9900}""")
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
            setBody(payload)
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
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("\"paymentConfirmation\": \"CASHIER\""))
    }
}
