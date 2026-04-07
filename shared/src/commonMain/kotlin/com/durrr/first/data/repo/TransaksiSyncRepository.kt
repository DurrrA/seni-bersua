package com.durrr.first.data.repo

import com.durrr.first.domain.model.OutboxEvent
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.network.ServerApiClient
import com.durrr.first.network.dto.TransactionBatchRequest
import com.durrr.first.network.dto.TransaksiDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TransaksiSyncRepository(
    private val syncRepository: SyncRepository,
    private val apiClient: ServerApiClient,
    private val nowIso: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueueCheckout(
        transaksi: TransaksiDto,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): String {
        val eventId = IdGenerator.newId("evt_")
        syncRepository.enqueue(
            OutboxEvent(
                id = eventId,
                type = EVENT_TYPE_TRANSAKSI,
                payloadJson = json.encodeToString(transaksi),
                createdAt = nowIso(),
                sentAt = null,
                outletId = outletId,
            ),
            outletId = outletId,
        )
        return eventId
    }

    suspend fun flushPending(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val pending = syncRepository.getPending(outletId)
            .filter { it.type == EVENT_TYPE_TRANSAKSI }
            .mapNotNull { event ->
                runCatching { event to json.decodeFromString<TransaksiDto>(event.payloadJson) }.getOrNull()
            }

        if (pending.isEmpty()) return 0

        val request = TransactionBatchRequest(
            outboxIds = pending.map { it.first.id },
            transaksi = pending.map { it.second },
            outletId = outletId,
        )
        val response = apiClient.syncTransactions(baseUrl, request)
        val sentAt = nowIso()
        response.accepted.forEach { outboxId ->
            syncRepository.markSent(outboxId, sentAt, outletId)
        }
        return response.accepted.size
    }

    companion object {
        private const val EVENT_TYPE_TRANSAKSI = "TRANSAKSI_CHECKOUT"
    }
}
