package com.durrr.first.ui

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
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.domain.model.ReceiptConfig
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    pickImage: ((String?) -> Unit) -> Unit,
    menuSyncRepository: MenuSyncRepository? = null,
    orderSyncRepository: OrderSyncRepository? = null,
    transaksiSyncRepository: TransaksiSyncRepository? = null,
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
        serverBaseUrl = settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .ifBlank { "http://10.0.2.2:8080" }
        outletId = settingsRepository.getValue(SettingsRepository.KEY_OUTLET_ID)
    }

    Column(
        modifier = Modifier
            .padding(Dimens.md)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        fun currentBaseUrl(): String = serverBaseUrl.trim().ifBlank { "http://10.0.2.2:8080" }
        fun currentOutletId(): String = outletId.trim().ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }

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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Receipt Settings")
                }
                if (!savedMessage.isNullOrBlank()) {
                    Text(savedMessage.orEmpty())
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
                    placeholder = { Text("http://10.0.2.2:8080") },
                )
                OutlinedTextField(
                    value = outletId,
                    onValueChange = { outletId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Outlet ID (Optional)") },
                )
                Button(
                    onClick = {
                        val baseUrlSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_SERVER_BASE_URL,
                            serverBaseUrl.trim(),
                        )
                        val outletSaved = settingsRepository.upsert(
                            SettingsRepository.KEY_OUTLET_ID,
                            outletId.trim(),
                        )
                        savedMessage = if (baseUrlSaved && outletSaved) {
                            "Saved server settings"
                        } else {
                            "Failed to save server settings"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Server Settings")
                }
                Text(
                    text = "Tip: Android emulator uses 10.0.2.2 for localhost.",
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
                                    val results = mutableListOf<String>()
                                    orderSyncRepository?.let {
                                        val pulledOrders = it.pullOrders(currentBaseUrl(), currentOutletId())
                                        results += "orders:$pulledOrders"
                                    }
                                    menuSyncRepository?.let {
                                        val pulledMenu = it.pullFromServer(currentBaseUrl(), currentOutletId())
                                        val pushedMenu = it.pushToServer(currentBaseUrl(), currentOutletId())
                                        results += "menu_pull:$pulledMenu"
                                        results += "menu_push:$pushedMenu"
                                    }
                                    transaksiSyncRepository?.let {
                                        val flushed = it.flushPending(currentBaseUrl(), currentOutletId())
                                        results += "transaksi_flush:$flushed"
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
                        enabled = !syncBusy,
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
                                            val pulled = orderSyncRepository.pullOrders(currentBaseUrl(), currentOutletId())
                                            "Pulled $pulled order(s)"
                                        }.onFailure {
                                            syncMessage = "Order sync failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy,
                            ) { Text("Pull Orders") }
                        }
                        if (transaksiSyncRepository != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            val flushed = transaksiSyncRepository.flushPending(currentBaseUrl(), currentOutletId())
                                            "Flushed $flushed transaksi event(s)"
                                        }.onFailure {
                                            syncMessage = "Transaksi sync failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy,
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
                                            val pulled = menuSyncRepository.pullFromServer(currentBaseUrl(), currentOutletId())
                                            "Pulled $pulled menu item(s)"
                                        }.onFailure {
                                            syncMessage = "Menu pull failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy,
                            ) { Text("Pull Menu") }
                            Button(
                                onClick = {
                                    scope.launch {
                                        syncBusy = true
                                        syncMessage = null
                                        runCatching {
                                            val pushed = menuSyncRepository.pushToServer(currentBaseUrl(), currentOutletId())
                                            "Pushed $pushed menu item(s)"
                                        }.onFailure {
                                            syncMessage = "Menu push failed: ${it.message ?: "Unknown error"}"
                                        }.onSuccess {
                                            syncMessage = it
                                        }
                                        syncBusy = false
                                    }
                                },
                                enabled = !syncBusy,
                            ) { Text("Push Menu") }
                        }
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
