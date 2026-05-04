package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.domain.service.IdGenerator

class SettingsRepository(private val db: TokoDatabase) {
    fun loadReceiptConfig(): ReceiptConfig {
        return runCatching {
            ensureSettingsTable()
            val values = db.tokoQueries
                .selectAllAppSettings()
                .executeAsList()
                .associate { it.setting_key to (it.setting_value ?: "") }
            ReceiptConfig(
                storeName = values[KEY_STORE_NAME].orEmpty().ifBlank { "SuCash" },
                storeAddressOrPhone = values[KEY_STORE_ADDRESS].orEmpty(),
                headerLogoPath = values[KEY_STORE_LOGO].orEmpty(),
                watermarkLogoPath = values[KEY_WATERMARK_LOGO].orEmpty(),
                footerText = values[KEY_FOOTER_TEXT].orEmpty().ifBlank { "Thank you" },
            )
        }.getOrElse {
            defaultConfig()
        }
    }

    fun saveReceiptConfig(config: ReceiptConfig): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_STORE_NAME, config.storeName)
                db.tokoQueries.upsertAppSetting(KEY_STORE_ADDRESS, config.storeAddressOrPhone)
                db.tokoQueries.upsertAppSetting(KEY_STORE_LOGO, config.headerLogoPath)
                db.tokoQueries.upsertAppSetting(KEY_WATERMARK_LOGO, config.watermarkLogoPath)
                db.tokoQueries.upsertAppSetting(KEY_FOOTER_TEXT, config.footerText)
            }
            true
        }.getOrDefault(false)
    }

    fun upsert(key: String, value: String): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.tokoQueries.upsertAppSetting(key, value)
            true
        }.getOrDefault(false)
    }

    fun getValue(key: String): String {
        return runCatching {
            ensureSettingsTable()
            val row = db.tokoQueries.selectAppSetting(key).executeAsOneOrNull()
            row?.setting_value.orEmpty()
        }.getOrDefault("")
    }

    fun getOptionalValue(key: String): String? {
        return getValue(key).trim().ifBlank { null }
    }

    fun getOptionalServerBaseUrl(): String? = getOptionalValue(KEY_SERVER_BASE_URL)

    fun getDefaultCashierId(): String? = getOptionalValue(KEY_DEFAULT_CASHIER_ID)

    fun getDefaultCashierName(): String? = getOptionalValue(KEY_DEFAULT_CASHIER_NAME)

    fun ensureDefaultCashierId(existingName: String? = null): String {
        val existing = getDefaultCashierId()
        if (existing != null) return existing
        val generated = buildDefaultCashierId(existingName)
        upsert(KEY_DEFAULT_CASHIER_ID, generated)
        return generated
    }

    fun isLocalSetupComplete(): Boolean {
        return getValue(KEY_LOCAL_SETUP_COMPLETED).equals("true", ignoreCase = true)
    }

    fun markLocalSetupCompleted(completed: Boolean): Boolean {
        return upsert(KEY_LOCAL_SETUP_COMPLETED, completed.toString())
    }

    fun resetAllLocal(outletId: String = DEFAULT_OUTLET_ID): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.deleteOrderItemsByOutlet(outletId)
                db.tokoQueries.deleteOrderHeadersByOutlet(outletId)
                db.tokoQueries.deleteTransaksiDetailsByOutlet(outletId)
                db.tokoQueries.deletePembayaranByOutlet(outletId)
                db.tokoQueries.deleteTransaksiByOutlet(outletId)
                db.tokoQueries.deleteProductModifierLinksByOutlet(outletId)
                db.tokoQueries.deleteModifierOptionsByOutlet(outletId)
                db.tokoQueries.deleteModifierGroupsByOutlet(outletId)
                db.tokoQueries.deleteItemsByOutlet(outletId)
                db.tokoQueries.deleteGroupsByOutlet(outletId)
                db.tokoQueries.deleteOutboxByOutlet(outletId)
                db.tokoQueries.deleteStockLedgerByOutlet(outletId)
                db.tokoQueries.deleteStockThresholdByOutlet(outletId)
                db.tokoQueries.deleteStockBalanceByOutlet(outletId)
                db.tokoQueries.deleteCashMovementsByOutlet(outletId)
                db.tokoQueries.deleteCashSessionsByOutlet(outletId)
                db.tokoQueries.deleteAllAppSettings()
            }
            true
        }.getOrDefault(false)
    }

    private fun ensureSettingsTable() {
        db.tokoQueries.createAppSettingsTable()
    }

    companion object {
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_ADDRESS = "store_address"
        const val KEY_STORE_LOGO = "store_logo_path"
        const val KEY_WATERMARK_LOGO = "watermark_logo_path"
        const val KEY_FOOTER_TEXT = "footer_text"
        const val KEY_SERVER_BASE_URL = "server_base_url"
        const val KEY_OUTLET_ID = "outlet_id"
        const val KEY_ALLOW_NEGATIVE_STOCK = "allow_negative_stock"
        const val KEY_AUTO_TAX_PERCENT = "auto_tax_percent"
        const val KEY_AUTO_SERVICE_PERCENT = "auto_service_percent"
        const val KEY_AUTO_ROUNDING = "auto_rounding"
        const val KEY_LOCAL_SETUP_COMPLETED = "local_setup_completed"
        const val KEY_SETUP_MODE = "setup_mode"
        const val KEY_OWNER_NAME = "owner_name"
        const val KEY_OWNER_PIN = "owner_pin"
        const val KEY_DEFAULT_CASHIER_ID = "default_cashier_id"
        const val KEY_DEFAULT_CASHIER_NAME = "default_cashier_name"
        const val KEY_DEFAULT_CASHIER_PIN = "default_cashier_pin"
        const val DEFAULT_OUTLET_ID = "default"
        const val SETUP_MODE_LOCAL_FIRST = "LOCAL_FIRST"

        private fun defaultConfig(): ReceiptConfig = ReceiptConfig(
            storeName = "SuCash",
            storeAddressOrPhone = "",
            headerLogoPath = "",
            watermarkLogoPath = "",
            footerText = "Thank you",
        )

        private fun buildDefaultCashierId(name: String?): String {
            val normalized = name
                ?.trim()
                ?.lowercase()
                ?.replace(Regex("[^a-z0-9]+"), "_")
                ?.trim('_')
                .orEmpty()
            return if (normalized.isNotBlank()) {
                "cashier_$normalized"
            } else {
                IdGenerator.newId("cashier_")
            }
        }
    }
}
