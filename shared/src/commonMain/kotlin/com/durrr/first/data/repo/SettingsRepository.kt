package com.durrr.first.data.repo

import com.durrr.first.TokoDatabase
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.network.security.OpaqueBearerTokenCodec

class SettingsRepository(private val db: TokoDatabase) {
    data class LocalAccountSession(
        val role: String,
        val userId: String,
        val userName: String,
    )

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

    fun getOwnerName(): String? = getOptionalValue(KEY_OWNER_NAME)

    fun getServerApiSharedSecret(): String? = getOptionalValue(KEY_SERVER_API_SHARED_SECRET)

    fun ensureDefaultCashierId(existingName: String? = null): String {
        val existing = getDefaultCashierId()
        if (existing != null) return existing
        val generated = buildDefaultCashierId(existingName)
        upsert(KEY_DEFAULT_CASHIER_ID, generated)
        return generated
    }

    fun hasOwnerPinConfigured(): Boolean = isValidPin(getValue(KEY_OWNER_PIN))

    fun hasCashierPinConfigured(): Boolean = isValidPin(getValue(KEY_DEFAULT_CASHIER_PIN))

    fun hasAnyLoginPinConfigured(): Boolean = hasOwnerPinConfigured() || hasCashierPinConfigured()

    fun verifyOwnerPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        return getValue(KEY_OWNER_PIN) == pin
    }

    fun verifyCashierPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        return getValue(KEY_DEFAULT_CASHIER_PIN) == pin
    }

    fun setActiveUserOwner(): Boolean {
        val ownerName = getOwnerName()?.ifBlank { null } ?: "Owner"
        return setActiveUser(
            role = ROLE_OWNER,
            userId = "owner",
            userName = ownerName,
        )
    }

    fun setActiveUserCashier(): Boolean {
        val cashierName = getDefaultCashierName()?.ifBlank { null } ?: "Cashier"
        val cashierId = ensureDefaultCashierId(cashierName)
        return setActiveUser(
            role = ROLE_CASHIER,
            userId = cashierId,
            userName = cashierName,
        )
    }

    fun setActiveUser(role: String, userId: String, userName: String): Boolean {
        if (role != ROLE_OWNER && role != ROLE_CASHIER) return false
        if (userId.isBlank() || userName.isBlank()) return false
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ROLE, role)
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ID, userId)
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_NAME, userName)
            }
            true
        }.getOrDefault(false)
    }

    fun clearActiveUser(): Boolean {
        return runCatching {
            ensureSettingsTable()
            db.transaction {
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ROLE, "")
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_ID, "")
                db.tokoQueries.upsertAppSetting(KEY_ACTIVE_USER_NAME, "")
            }
            true
        }.getOrDefault(false)
    }

    fun getActiveUserSession(): LocalAccountSession? {
        val role = getOptionalValue(KEY_ACTIVE_USER_ROLE) ?: return null
        val userId = getOptionalValue(KEY_ACTIVE_USER_ID) ?: return null
        val userName = getOptionalValue(KEY_ACTIVE_USER_NAME) ?: return null
        if (role != ROLE_OWNER && role != ROLE_CASHIER) return null
        return LocalAccountSession(
            role = role,
            userId = userId,
            userName = userName,
        )
    }

    fun resolveServerApiBearerToken(role: String?): String? {
        val normalizedRole = role?.trim()?.uppercase()
        if (normalizedRole != ROLE_OWNER && normalizedRole != ROLE_CASHIER) return null
        val secret = getServerApiSharedSecret() ?: return null
        val pin = when (normalizedRole) {
            ROLE_OWNER -> getOptionalValue(KEY_OWNER_PIN)
            ROLE_CASHIER -> getOptionalValue(KEY_DEFAULT_CASHIER_PIN)
            else -> null
        } ?: return null
        return OpaqueBearerTokenCodec.issue(
            secret = secret,
            role = normalizedRole,
            pin = pin,
        )
    }

    fun getActiveUserServerApiBearerToken(): String? {
        return resolveServerApiBearerToken(getActiveUserSession()?.role)
    }

    fun resolveCurrentCashierId(): String {
        return getActiveUserSession()?.userId
            ?: ensureDefaultCashierId(getDefaultCashierName())
    }

    fun resolveCurrentCashierName(): String {
        return getActiveUserSession()?.userName
            ?: getDefaultCashierName()
            ?.ifBlank { null }
            ?: "Cashier"
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
        const val KEY_ACTIVE_USER_ROLE = "active_user_role"
        const val KEY_ACTIVE_USER_ID = "active_user_id"
        const val KEY_ACTIVE_USER_NAME = "active_user_name"
        const val KEY_SERVER_API_SHARED_SECRET = "server_api_shared_secret"
        const val DEFAULT_OUTLET_ID = "default"
        const val SETUP_MODE_LOCAL_FIRST = "LOCAL_FIRST"
        const val ROLE_OWNER = "OWNER"
        const val ROLE_CASHIER = "CASHIER"

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

        private fun isValidPin(pin: String): Boolean {
            return pin.length in 4..6 && pin.all(Char::isDigit)
        }
    }
}
