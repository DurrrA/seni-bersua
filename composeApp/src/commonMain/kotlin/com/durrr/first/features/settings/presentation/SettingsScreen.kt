package com.durrr.first.features.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppInfoLine
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    pickImage: ((String?) -> Unit) -> Unit,
    menuSyncRepository: MenuSyncRepository? = null,
    orderSyncRepository: OrderSyncRepository? = null,
    transaksiSyncRepository: TransaksiSyncRepository? = null,
    isOwnerSession: Boolean = false,
    onRequireLocalSetup: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenCashFlow: () -> Unit = {},
    onOpenStock: () -> Unit = {},
    onOpenCashClosing: () -> Unit = {},
) {
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var headerLogoPath by remember { mutableStateOf("") }
    var watermarkLogoPath by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }
    var serverBaseUrl by remember { mutableStateOf("") }
    var outletId by remember { mutableStateOf("") }
    var autoTaxPercent by remember { mutableStateOf("11") }
    var autoServicePercent by remember { mutableStateOf("10") }
    var autoRounding by remember { mutableStateOf("0") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val config = settingsRepository.loadReceiptConfig()
        storeName = config.storeName
        storeAddress = config.storeAddressOrPhone
        headerLogoPath = config.headerLogoPath
        watermarkLogoPath = config.watermarkLogoPath
        footerText = config.footerText
        serverBaseUrl = settingsRepository.getValue(SettingsRepository.KEY_SERVER_BASE_URL)
        outletId = settingsRepository.getValue(SettingsRepository.KEY_OUTLET_ID)
        autoTaxPercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_TAX_PERCENT)
            .ifBlank { "11" }
        autoServicePercent = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_SERVICE_PERCENT)
            .ifBlank { "10" }
        autoRounding = settingsRepository
            .getValue(SettingsRepository.KEY_AUTO_ROUNDING)
            .ifBlank { "0" }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.md)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        fun currentBaseUrlOrNull(): String? = serverBaseUrl.trim().ifBlank { null }
        fun requireBaseUrl(): String = currentBaseUrlOrNull() ?: error("Set Server Base URL first in Settings.")
        fun currentOutletId(): String = outletId.trim().ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
        val hasServerConfigured = currentBaseUrlOrNull() != null
        val activeSession = settingsRepository.getActiveUserSession()
        val ownerAccess = isOwnerSession || activeSession?.role == SettingsRepository.ROLE_OWNER

        fun requireOwnerAccessOrFail() {
            if (!ownerAccess) {
                error("Aksi ini khusus Owner.")
            }
        }

        AppSectionHeader("Settings", "Receipt and local app configuration")

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                AppSectionHeader("Device Access", "Login session untuk tablet ini")
                AppInfoLine("Role", activeSession?.role ?: "-")
                AppInfoLine("User", activeSession?.userName ?: "-")
                AppInfoLine("User ID", activeSession?.userId ?: "-")
                Button(
                    onClick = {
                        settingsRepository.clearActiveUser()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Lock App (Logout)")
                }
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Store Name") },
                )
                OutlinedTextField(
                    value = storeAddress,
                    onValueChange = { storeAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Address / Phone") },
                )
                OutlinedTextField(
                    value = headerLogoPath,
                    onValueChange = { headerLogoPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Header Logo URI") },
                )
                Button(
                    onClick = {
                        pickImage { uri ->
                            if (!uri.isNullOrBlank()) headerLogoPath = uri
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick Header Logo (Android)")
                }
                OutlinedTextField(
                    value = watermarkLogoPath,
                    onValueChange = { watermarkLogoPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Watermark Logo URI") },
                )
                Button(
                    onClick = {
                        pickImage { uri ->
                            if (!uri.isNullOrBlank()) watermarkLogoPath = uri
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick Watermark Logo (Android)")
                }
                OutlinedTextField(
                    value = footerText,
                    onValueChange = { footerText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Footer Text") },
                )
                Button(
                    onClick = {
                        if (!ownerAccess) {
                            savedMessage = "Hanya Owner yang boleh ubah pengaturan struk."
                            return@Button
                        }
                        val saved = settingsRepository.saveReceiptConfig(
                            ReceiptConfig(
                                storeName = storeName,
                                storeAddressOrPhone = storeAddress,
                                headerLogoPath = headerLogoPath,
                                watermarkLogoPath = watermarkLogoPath,
                                footerText = footerText,
                            )
                        )
                        savedMessage = if (saved) {
                            "Saved"
                        } else {
                            "Failed to save settings"
                        }
                    },
                    enabled = ownerAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Receipt Settings")
                }
                if (!savedMessage.isNullOrBlank()) {
                    Text(savedMessage.orEmpty())
                }
                if (!ownerAccess) {
                    Text(
                        text = "Mode kasir: pengaturan struk hanya bisa diubah owner.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedTextField(
                    value = serverBaseUrl,
                    onValueChange = { serverBaseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server Base URL (Optional)") },
                    placeholder = { Text("http://10.0.2.2:8080") },
                )
                OutlinedTextField(
                    value = outletId,
                    onValueChange = { outletId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Outlet ID (Optional)") },
                )
                OutlinedTextField(
                    value = autoTaxPercent,
                    onValueChange = { autoTaxPercent = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tax % (Auto)") },
                    placeholder = { Text("11") },
                )
                OutlinedTextField(
                    value = autoServicePercent,
                    onValueChange = { autoServicePercent = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Service % (Auto)") },
                    placeholder = { Text("10") },
                )
                OutlinedTextField(
                    value = autoRounding,
                    onValueChange = { autoRounding = it.filter { c -> c.isDigit() || c == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rounding (Auto, Rp)") },
                    placeholder = { Text("0") },
                )
                Button(
                    onClick = {
                        if (!ownerAccess) {
                            savedMessage = "Hanya Owner yang boleh ubah koneksi server/outlet."
                            return@Button
                        }
                        val baseUrlSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_SERVER_BASE_URL,
                            serverBaseUrl.trim(),
                        )
                        val outletSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_OUTLET_ID,
                            outletId.trim(),
                        )
                        val taxSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_TAX_PERCENT,
                            autoTaxPercent.ifBlank { "11" },
                        )
                        val serviceSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_SERVICE_PERCENT,
                            autoServicePercent.ifBlank { "10" },
                        )
                        val roundingSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_AUTO_ROUNDING,
                            autoRounding.ifBlank { "0" },
                        )
                        savedMessage = if (baseUrlSaved && outletSaved && taxSaved && serviceSaved && roundingSaved) {
                            "Saved server settings"
                        } else {
                            "Failed to save server settings"
                        }
                    },
                    enabled = ownerAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Connectivity Settings")
                }
                Text(
                    text = "Leave blank for local-only mode. If you pair a server later, Android emulator uses 10.0.2.2 for localhost.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (menuSyncRepository != null || orderSyncRepository != null || transaksiSyncRepository != null) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Manual Sync", "Sync mobile, server, and web data")
                    Button(
                        onClick = {
                            scope.launch {
                                syncBusy = true
                                syncMessage = null
                                runCatching {
                                    val baseUrl = requireBaseUrl()
                                    val results = mutableListOf<String>()
                                    transaksiSyncRepository?.let {
                                        val flushed = it.flushPending(baseUrl, currentOutletId())
                                        results += "transaksi_flush:$flushed"
                                    }
                                    menuSyncRepository?.let {
                                        if (ownerAccess) {
                                            val pushedMenu = it.pushToServer(baseUrl, currentOutletId())
                                            results += "menu_push:$pushedMenu"
                                        } else {
                                            results += "menu_push:skipped(owner_only)"
                                        }
                                        val pulledMenu = it.pullFromServer(baseUrl, currentOutletId())
                                        results += "menu_pull:$pulledMenu"
                                    }
                                    orderSyncRepository?.let {
                                        val pulledOrders = it.pullOrders(baseUrl, currentOutletId())
                                        results += "orders_pull:$pulledOrders"
                                    }
                                    "Sync all done (${results.joinToString(", ")})"
                                }.onFailure {
                                    syncMessage = "Sync failed: ${it.message ?: "Unknown error"}"
                                }.onSuccess {
                                    syncMessage = it
                                }
                                syncBusy = false
                            }
                        },
                        enabled = !syncBusy && hasServerConfigured,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (syncBusy) "Syncing..." else "Sync All Now")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        if (orderSyncRepository != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            val pulled = orderSyncRepository.pullOrders(requireBaseUrl(), currentOutletId())
                                            "Pulled $pulled order(s)"
                                        }.onFailure {
                                            syncMessage = "Order sync failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) { Text("Pull Orders") }
                        }
                        if (transaksiSyncRepository != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            val flushed = transaksiSyncRepository.flushPending(requireBaseUrl(), currentOutletId())
                                            "Flushed $flushed transaksi event(s)"
                                        }.onFailure {
                                            syncMessage = "Transaksi sync failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) { Text("Flush Transaksi") }
                        }
                    }
                    if (menuSyncRepository != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            val pulled = menuSyncRepository.pullFromServer(requireBaseUrl(), currentOutletId())
                                            "Pulled $pulled menu item(s)"
                                        }.onFailure {
                                            syncMessage = "Menu pull failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured,
                            ) { Text("Pull Menu") }
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            requireOwnerAccessOrFail()
                                            val pushed = menuSyncRepository.pushToServer(requireBaseUrl(), currentOutletId())
                                            "Pushed $pushed menu item(s)"
                                        }.onFailure {
                                            syncMessage = "Menu push failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                            ) { Text("Push Menu") }
                        }
                    }
                    if (menuSyncRepository != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            requireOwnerAccessOrFail()
                                            val baseUrl = requireBaseUrl()
                                            val outlet = currentOutletId()
                                            val flushed = transaksiSyncRepository?.flushPending(baseUrl, outlet) ?: 0
                                            val pushed = menuSyncRepository.pushToServer(baseUrl, outlet)
                                            val pulled = menuSyncRepository.pullFromServer(baseUrl, outlet)
                                            val pulledOrders = orderSyncRepository?.pullOrders(baseUrl, outlet) ?: 0
                                            "Aligned data (trx_flush:$flushed, menu_push:$pushed, menu_pull:$pulled, orders_pull:$pulledOrders)"
                                        }.onFailure {
                                            syncMessage = "Align failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) { Text("Force Align Data") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            requireOwnerAccessOrFail()
                                            val outletBeforeReset = currentOutletId()
                                            settingsRepository.resetAllLocal(outletBeforeReset)
                                            "Local reset complete. Returning to setup."
                                        }.onFailure {
                                            syncMessage = "Local reset failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                            onRequireLocalSetup()
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) { Text("Reset Local Data") }
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            requireOwnerAccessOrFail()
                                            val baseUrl = requireBaseUrl()
                                            val outletBeforeReset = currentOutletId()
                                            menuSyncRepository.resetServerAllData(baseUrl, outletBeforeReset)
                                            "Server reset complete."
                                        }.onFailure {
                                            syncMessage = "Server reset failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy && hasServerConfigured && ownerAccess,
                                modifier = Modifier.weight(1f),
                            ) { Text("Reset Server Data") }
                        }
                    }
                    if (!hasServerConfigured) {
                        Text(
                            "Server belum dipasang di device ini. Sync dan reset server akan aktif setelah URL server disimpan. Reset local tetap akan mengembalikan tablet ke setup awal.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!ownerAccess) {
                        Text(
                            "Mode kasir: push menu, force align, reset data, dan ubah koneksi hanya untuk owner.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!syncMessage.isNullOrBlank()) {
                        Text(syncMessage.orEmpty())
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Button(
                onClick = onOpenCashFlow,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cash Flow")
            }
            Button(
                onClick = onOpenStock,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stock")
            }
            Button(
                onClick = onOpenCashClosing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cash Closing")
            }
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    var storeName by remember { mutableStateOf("SuCash") }
    var storeAddress by remember { mutableStateOf("Jl. Preview No. 12") }
    var serverBaseUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var outletId by remember { mutableStateOf("default") }

    AppTheme {
        Column(
            modifier = Modifier
                .padding(Dimens.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            AppSectionHeader("Settings", "Receipt and local app configuration")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Store Name") },
                    )
                    OutlinedTextField(
                        value = storeAddress,
                        onValueChange = { storeAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Address / Phone") },
                    )
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Receipt Settings")
                    }
                }
            }
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    OutlinedTextField(
                        value = serverBaseUrl,
                        onValueChange = { serverBaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server Base URL") },
                    )
                    OutlinedTextField(
                        value = outletId,
                        onValueChange = { outletId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Outlet ID (Optional)") },
                    )
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Server Settings")
                    }
                }
            }
        }
    }
}
