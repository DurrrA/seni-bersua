package com.durrr.first.data.repo

import com.durrr.first.domain.model.Item
import com.durrr.first.network.ServerApiClient

class MenuSyncRepository(
    private val menuRepository: MenuRepository,
    private val apiClient: ServerApiClient,
) {
    suspend fun pullFromServer(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val remote = apiClient.fetchMenu(baseUrl, outletId)
        val localById = menuRepository.getItems(outletId).associateBy { it.id }
        remote.forEach { remoteItem ->
            val local = localById[remoteItem.id]
            menuRepository.upsertItem(
                Item(
                    id = remoteItem.id,
                    name = remoteItem.name,
                    price = remoteItem.price,
                    groupId = local?.groupId,
                    code = local?.code,
                    isActive = true,
                    outletId = remoteItem.outletId ?: outletId,
                ),
                outletId = remoteItem.outletId ?: outletId,
            )
        }
        return remote.size
    }

    suspend fun pushToServer(
        baseUrl: String,
        outletId: String = SettingsRepository.DEFAULT_OUTLET_ID,
    ): Int {
        val local = menuRepository.getItems(outletId)
        local.forEach { item ->
            if (item.isActive) {
                apiClient.upsertMenuItem(
                    baseUrl = baseUrl,
                    id = item.id,
                    name = item.name,
                    price = item.price,
                    outletId = outletId,
                )
            } else {
                apiClient.deleteMenuItem(baseUrl, item.id, outletId)
            }
        }
        return local.size
    }
}
