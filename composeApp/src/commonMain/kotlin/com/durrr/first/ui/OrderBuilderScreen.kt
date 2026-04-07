package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.model.OrderDraft
import com.durrr.first.ui.model.OrderDraftLine
import com.durrr.first.ui.model.OrderDraftStore
import kotlinx.coroutines.launch

private data class DraftCartItem(
    val item: Item,
    val qty: Long,
)

@Composable
fun OrderBuilderScreen(
    menuRepository: MenuRepository,
    menuSyncRepository: MenuSyncRepository,
    settingsRepository: SettingsRepository,
    launchScanner: () -> Unit,
    scannedToken: String?,
    onScannedTokenConsumed: () -> Unit,
    onProceedToCheckout: (String) -> Unit,
) {
    var menuItems by remember { mutableStateOf(emptyList<Item>()) }
    var cartItems by remember { mutableStateOf(emptyList<DraftCartItem>()) }
    var tableToken by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun serverBaseUrl(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .ifBlank { "http://10.0.2.2:8080" }
    }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    suspend fun refreshMenuFromServer() {
        menuItems = menuRepository.getItems(currentOutletId()).filter { it.isActive }
        runCatching {
            val pulled = menuSyncRepository.pullFromServer(serverBaseUrl(), currentOutletId())
            menuItems = menuRepository.getItems(currentOutletId()).filter { it.isActive }
            syncMessage = "Menu synced from server: $pulled item(s)."
        }.onFailure {
            syncMessage = "Menu sync pending: ${it.message ?: "Unknown error"}"
        }
    }

    LaunchedEffect(Unit) {
        refreshMenuFromServer()
    }

    LaunchedEffect(scannedToken) {
        if (!scannedToken.isNullOrBlank()) {
            tableToken = scannedToken
            onScannedTokenConsumed()
        }
    }

    val filteredMenu = menuItems.filter { item ->
        searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
    }
    val lineCount = cartItems.sumOf { it.qty }
    val subtotal = cartItems.sumOf { it.qty * it.item.price }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader(
                title = "New Order",
                subtitle = "Create walk-in order without customer ID",
            )
            if (!syncMessage.isNullOrBlank()) {
                Text(
                    text = syncMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Button(onClick = { scope.launch { refreshMenuFromServer() } }) {
                    Text("Sync Menu")
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Table (Optional)")
                    OutlinedTextField(
                        value = tableToken,
                        onValueChange = { tableToken = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Table label / token") },
                        placeholder = { Text("Example: A1, Takeaway, Walk-in") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                        Button(onClick = launchScanner) { Text("Scan QR") }
                        Button(onClick = { tableToken = "" }) { Text("Clear") }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Menu")
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search menu item") },
                    )
                    if (filteredMenu.isEmpty()) {
                        AppEmptyState(
                            title = "No items found",
                            message = "Add items from Menu screen first.",
                        )
                    } else {
                        filteredMenu.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(item.name)
                                    Text(
                                        "Rp ${item.price}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = {
                                        val existing = cartItems.firstOrNull { it.item.id == item.id }
                                        cartItems = if (existing == null) {
                                            cartItems + DraftCartItem(item, 1)
                                        } else {
                                            cartItems.map { line ->
                                                if (line.item.id == item.id) line.copy(qty = line.qty + 1) else line
                                            }
                                        }
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    AppSectionHeader("Order Cart")
                    if (cartItems.isEmpty()) {
                        Text("No items selected yet.")
                    } else {
                        cartItems.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("${line.item.name} x${line.qty}")
                                    Text(
                                        "Line: Rp ${line.item.price * line.qty}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                    Button(
                                        onClick = {
                                            cartItems = cartItems.mapNotNull { cart ->
                                                if (cart.item.id != line.item.id) return@mapNotNull cart
                                                val nextQty = cart.qty - 1
                                                if (nextQty <= 0) null else cart.copy(qty = nextQty)
                                            }
                                        },
                                    ) { Text("-") }
                                    Button(
                                        onClick = {
                                            cartItems = cartItems.map { cart ->
                                                if (cart.item.id == line.item.id) cart.copy(qty = cart.qty + 1) else cart
                                            }
                                        },
                                    ) { Text("+") }
                                }
                            }
                        }
                        Text("Items: $lineCount")
                        Text("Subtotal: Rp $subtotal")
                    }
                    Button(
                        onClick = {
                            if (cartItems.isEmpty()) return@Button
                            val draftId = IdGenerator.newId("odr_")
                            OrderDraftStore.putDraft(
                                OrderDraft(
                                    id = draftId,
                                    tableToken = tableToken.ifBlank { null },
                                    lines = cartItems.map { line ->
                                        OrderDraftLine(
                                            itemId = line.item.id,
                                            itemName = line.item.name,
                                            qty = line.qty,
                                            price = line.item.price,
                                        )
                                    },
                                )
                            )
                            onProceedToCheckout(draftId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cartItems.isNotEmpty(),
                    ) {
                        Text("Continue to Checkout")
                    }
                }
            }
        }
    }
}
