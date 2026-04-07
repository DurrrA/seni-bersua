package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.ReceiptConfig

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
        const val DEFAULT_OUTLET_ID = "default"

        private fun defaultConfig(): ReceiptConfig = ReceiptConfig(
            storeName = "SuCash",
            storeAddressOrPhone = "",
            headerLogoPath = "",
            watermarkLogoPath = "",
            footerText = "Thank you",
        )
    }
}
