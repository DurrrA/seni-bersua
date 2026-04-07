package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.OutboxEvent

class SyncRepository(private val db: TokoDatabase) {
    fun enqueue(
        event: OutboxEvent,
        outletId: String = event.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        db.tokoQueries.insertOutboxEvent(
            id_event = event.id,
            event_type = event.type,
            payload_json = event.payloadJson,
            created_at = event.createdAt,
            sent_at = event.sentAt,
            outlet_id = outletId,
        )
    }

    fun getPending(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<OutboxEvent> {
        return db.tokoQueries.selectPendingOutbox(outletId).executeAsList().map {
            OutboxEvent(
                id = it.id_event,
                type = it.event_type,
                payloadJson = it.payload_json,
                createdAt = it.created_at,
                sentAt = it.sent_at,
                outletId = it.outlet_id,
            )
        }
    }

    fun markSent(id: String, sentAt: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.markOutboxSent(sentAt, id, outletId)
    }
}
