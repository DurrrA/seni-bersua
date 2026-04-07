package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item

class MenuRepository(private val db: TokoDatabase) {
    fun getGroups(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<GroupItem> {
        return db.tokoQueries.selectAllGroups(outletId).executeAsList().map {
            GroupItem(
                id = it.id_group_item,
                name = it.nama,
                order = it.urutan.toInt(),
                outletId = it.outlet_id,
            )
        }
    }

    fun upsertGroup(
        group: GroupItem,
        outletId: String = group.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        db.tokoQueries.upsertGroupItem(group.id, group.name, group.order.toLong(), outletId)
    }

    fun deleteGroup(groupId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.deleteGroupItem(groupId, outletId)
    }

    fun getItems(outletId: String = SettingsRepository.DEFAULT_OUTLET_ID): List<Item> {
        return db.tokoQueries.selectAllItems(outletId).executeAsList().map {
            Item(
                id = it.id_item,
                name = it.nama ?: "",
                price = parseLong(it.harga),
                groupId = it.id_group_item,
                code = it.kode,
                isActive = it.is_delete == null || it.is_delete == "0",
                outletId = it.outlet_id,
            )
        }
    }

    fun upsertItem(
        item: Item,
        outletId: String = item.outletId ?: SettingsRepository.DEFAULT_OUTLET_ID,
    ) {
        db.tokoQueries.upsertItem(
            id_item = item.id,
            nama = item.name,
            harga = item.price.toString(),
            id_group_item = item.groupId,
            is_delete = if (item.isActive) "0" else "1",
            kode = item.code,
            jenis_item = null,
            outlet_id = outletId,
        )
    }

    fun deleteItem(itemId: String, outletId: String = SettingsRepository.DEFAULT_OUTLET_ID) {
        db.tokoQueries.softDeleteItem(itemId, outletId)
    }

    private fun parseLong(value: String?): Long = value?.toLongOrNull() ?: 0L
}
