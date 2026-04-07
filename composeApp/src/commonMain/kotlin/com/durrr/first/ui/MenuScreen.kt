package com.durrr.first.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppCard
import com.durrr.first.ui.design.AppEmptyState
import com.durrr.first.ui.design.AppErrorBanner
import com.durrr.first.ui.design.AppSectionHeader
import com.durrr.first.ui.design.Dimens
import com.durrr.first.ui.model.BundleDraft
import com.durrr.first.ui.model.BundleItemInput
import com.durrr.first.ui.model.BundleStore
import kotlinx.coroutines.launch

@Composable
fun MenuScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
) {
    var groups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var items by remember { mutableStateOf(emptyList<Item>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var groupEditId by remember { mutableStateOf<String?>(null) }
    var groupName by remember { mutableStateOf("") }
    var groupOrder by remember { mutableStateOf("0") }

    var itemEditId by remember { mutableStateOf<String?>(null) }
    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemCode by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf("") }
    var itemActive by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showGroupDropdown by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var filterGroupId by remember { mutableStateOf<String?>(null) }
    var showFilterDropdown by remember { mutableStateOf(false) }

    var bundleName by remember { mutableStateOf("") }
    var bundleStartDate by remember { mutableStateOf("") }
    var bundleEndDate by remember { mutableStateOf("") }
    var bundlePrice by remember { mutableStateOf("") }
    val bundleItemSelection = remember { mutableStateMapOf<String, Boolean>() }

    fun currentOutletId(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun refresh() {
        val outletId = currentOutletId()
        groups = repo.getGroups(outletId)
        items = repo.getItems(outletId)
    }

    fun serverBaseUrl(): String {
        return settingsRepository
            .getValue(SettingsRepository.KEY_SERVER_BASE_URL)
            .ifBlank { "http://10.0.2.2:8080" }
    }

    suspend fun pushMenuToServerAfterLocalChange() {
        isSyncing = true
        syncMessage = null
        runCatching {
            val count = menuSyncRepository.pushToServer(serverBaseUrl(), currentOutletId())
            "Pushed $count menu item(s) to server."
        }.onFailure {
            syncMessage = "Local save succeeded, push pending: ${it.message ?: "Unknown error"}"
        }.onSuccess {
            syncMessage = it
        }
        isSyncing = false
    }

    LaunchedEffect(Unit) {
        refresh()
        isSyncing = true
        runCatching {
            val count = menuSyncRepository.pullFromServer(serverBaseUrl(), currentOutletId())
            refresh()
            syncMessage = "Pulled $count menu item(s) from server."
        }.onFailure {
            syncMessage = "Auto pull skipped: ${it.message ?: "Unknown error"}"
        }
        isSyncing = false
    }

    val filteredItems = items.filter { item ->
        val matchSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
        val matchGroup = filterGroupId.isNullOrBlank() || item.groupId == filterGroupId
        matchSearch && matchGroup
    }

    LazyColumn(
        modifier = Modifier.padding(Dimens.md),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
    ) {
        item {
            AppSectionHeader("Menu Management", "Groups, items, and temporary bundles")
        }
        item {
            AppCard {
                AppSectionHeader("Menu Sync", "Sync menu with server/web")
                Text("Server: ${serverBaseUrl()}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                syncMessage = null
                                runCatching {
                                    val count = menuSyncRepository.pullFromServer(serverBaseUrl(), currentOutletId())
                                    refresh()
                                    "Pulled $count menu item(s) from server."
                                }.onFailure {
                                    syncMessage = "Pull failed: ${it.message ?: "Unknown error"}"
                                }.onSuccess {
                                    syncMessage = it
                                }
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing,
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Pull Menu")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                syncMessage = null
                                runCatching {
                                    val count = menuSyncRepository.pushToServer(serverBaseUrl(), currentOutletId())
                                    "Pushed $count menu item(s) to server."
                                }.onFailure {
                                    syncMessage = "Push failed: ${it.message ?: "Unknown error"}"
                                }.onSuccess {
                                    syncMessage = it
                                }
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing,
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Push Menu")
                    }
                }
                if (!syncMessage.isNullOrBlank()) {
                    Text(syncMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            if (error != null) AppErrorBanner(error.orEmpty())
        }
        item {
            GroupManagementCard(
                groups = groups,
                groupEditId = groupEditId,
                groupName = groupName,
                groupOrder = groupOrder,
                onNameChange = { groupName = it },
                onOrderChange = { groupOrder = it },
                onEdit = { group ->
                    groupEditId = group.id
                    groupName = group.name
                    groupOrder = group.order.toString()
                },
                onDelete = { groupId ->
                    runCatching { repo.deleteGroup(groupId, currentOutletId()) }
                        .onFailure { error = it.message ?: "Failed to delete group." }
                    refresh()
                },
                onSave = {
                    if (groupName.isBlank()) return@GroupManagementCard
                    val group = GroupItem(
                        id = groupEditId ?: IdGenerator.newId("grp_"),
                        name = groupName.trim(),
                        order = groupOrder.toIntOrNull() ?: 0,
                        outletId = currentOutletId(),
                    )
                    repo.upsertGroup(group, currentOutletId())
                    groupEditId = null
                    groupName = ""
                    groupOrder = "0"
                    refresh()
                },
                onCancel = {
                    groupEditId = null
                    groupName = ""
                    groupOrder = "0"
                },
            )
        }
        item {
            AppCard {
                AppSectionHeader("Items", "Search and filter by group")
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search item") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Button(onClick = { showFilterDropdown = true }) {
                        val selectedName = groups.firstOrNull { it.id == filterGroupId }?.name ?: "All Groups"
                        Text("Filter: $selectedName")
                    }
                    DropdownMenu(expanded = showFilterDropdown, onDismissRequest = { showFilterDropdown = false }) {
                        DropdownMenuItem(
                            text = { Text("All Groups") },
                            onClick = {
                                filterGroupId = null
                                showFilterDropdown = false
                            },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    filterGroupId = group.id
                                    showFilterDropdown = false
                                },
                            )
                        }
                    }
                }
                if (filteredItems.isEmpty()) {
                    AppEmptyState("No items yet", "Create your first item.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = Dimens.lg * 10),
                        verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                    ) {
                        filteredItems.forEach { item ->
                            AppCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text("Rp ${item.price}")
                                        Text("Status: ${if (item.isActive) "Active" else "Inactive"}")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                        Button(onClick = {
                                            itemEditId = item.id
                                            itemName = item.name
                                            itemPrice = item.price.toString()
                                            itemCode = item.code.orEmpty()
                                            itemType = ""
                                            itemActive = item.isActive
                                            selectedGroupId = item.groupId
                                        }) { Text("Edit") }
                                        Button(onClick = {
                                            repo.deleteItem(item.id, currentOutletId())
                                            refresh()
                                            scope.launch { pushMenuToServerAfterLocalChange() }
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                AppSectionHeader(
                    "${if (itemEditId == null) "Add" else "Edit"} Item",
                    "Select group by name",
                )
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = itemPrice,
                    onValueChange = { itemPrice = it },
                    label = { Text("Price") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = itemPrice.isNotBlank() && itemPrice.toLongOrNull() == null,
                )
                if (itemPrice.isNotBlank() && itemPrice.toLongOrNull() == null) {
                    Text("Price must be numeric", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = itemCode,
                    onValueChange = { itemCode = it },
                    label = { Text("Code (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = itemType,
                    onValueChange = { itemType = it },
                    label = { Text("Type (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Button(onClick = { showGroupDropdown = true }) {
                        Text("Group: ${groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Choose Group"}")
                    }
                    DropdownMenu(expanded = showGroupDropdown, onDismissRequest = { showGroupDropdown = false }) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    selectedGroupId = group.id
                                    showGroupDropdown = false
                                },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Text("Active")
                    Switch(
                        checked = itemActive,
                        onCheckedChange = { itemActive = it },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    Button(
                        onClick = {
                            val parsedPrice = itemPrice.toLongOrNull()
                            if (itemName.isBlank() || parsedPrice == null) return@Button
                            repo.upsertItem(
                                Item(
                                    id = itemEditId ?: IdGenerator.newId("item_"),
                                    name = itemName.trim(),
                                    price = parsedPrice,
                                    groupId = selectedGroupId,
                                    code = itemCode.ifBlank { null },
                                    isActive = itemActive,
                                    outletId = currentOutletId(),
                                ),
                                outletId = currentOutletId(),
                            )
                            itemEditId = null
                            itemName = ""
                            itemPrice = ""
                            itemCode = ""
                            itemType = ""
                            itemActive = true
                            selectedGroupId = null
                            refresh()
                            scope.launch { pushMenuToServerAfterLocalChange() }
                        },
                    ) { Text("Save Item") }
                    if (itemEditId != null) {
                        Button(
                            onClick = {
                                itemEditId = null
                                itemName = ""
                                itemPrice = ""
                                itemCode = ""
                                itemType = ""
                                itemActive = true
                                selectedGroupId = null
                            },
                        ) { Text("Cancel") }
                    }
                }
            }
        }
        item {
            AppCard {
                AppSectionHeader(
                    title = "Temporary Menu / Bundles",
                    subtitle = "TODO: Not applied to POS checkout yet",
                )
                if (BundleStore.bundles.isEmpty()) {
                    AppEmptyState(
                        title = "No bundles yet",
                        message = "Create bundles for temporary promos.",
                    )
                } else {
                    BundleStore.bundles.forEach { bundle ->
                        AppCard {
                            Text(bundle.name, style = MaterialTheme.typography.titleMedium)
                            Text("${bundle.startDate} - ${bundle.endDate}")
                            Text("Bundle Price: Rp ${bundle.price}")
                            Text("Items: ${bundle.items.joinToString { it.itemName }}")
                        }
                    }
                }
                OutlinedTextField(
                    value = bundleName,
                    onValueChange = { bundleName = it },
                    label = { Text("Bundle Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bundleStartDate,
                    onValueChange = { bundleStartDate = it },
                    label = { Text("Start Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bundleEndDate,
                    onValueChange = { bundleEndDate = it },
                    label = { Text("End Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bundlePrice,
                    onValueChange = { bundlePrice = it },
                    label = { Text("Bundle Price") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Select Items", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                    items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Checkbox(
                                checked = bundleItemSelection[item.id] == true,
                                onCheckedChange = { checked -> bundleItemSelection[item.id] = checked },
                            )
                            Text(item.name)
                        }
                    }
                }
                Button(
                    onClick = {
                        val selectedItems = items
                            .filter { bundleItemSelection[it.id] == true }
                            .map {
                                BundleItemInput(
                                    itemId = it.id,
                                    itemName = it.name,
                                    qty = 1,
                                )
                            }
                        val parsedPrice = bundlePrice.toLongOrNull() ?: 0L
                        if (bundleName.isBlank() || selectedItems.isEmpty()) return@Button
                        BundleStore.bundles.add(
                            BundleDraft(
                                id = IdGenerator.newId("bundle_"),
                                name = bundleName.trim(),
                                startDate = bundleStartDate,
                                endDate = bundleEndDate,
                                price = parsedPrice,
                                items = selectedItems,
                            )
                        )
                        bundleName = ""
                        bundleStartDate = ""
                        bundleEndDate = ""
                        bundlePrice = ""
                        bundleItemSelection.clear()
                    },
                ) {
                    Text("Save Bundle")
                }
            }
        }
    }
}

@Composable
private fun GroupManagementCard(
    groups: List<GroupItem>,
    groupEditId: String?,
    groupName: String,
    groupOrder: String,
    onNameChange: (String) -> Unit,
    onOrderChange: (String) -> Unit,
    onEdit: (GroupItem) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AppCard {
        AppSectionHeader("Groups", "Manage item groups by name")
        if (groups.isEmpty()) {
            AppEmptyState("No groups yet", "Create a group first.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text("Order: ${group.order}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                            Button(onClick = { onEdit(group) }) { Text("Edit") }
                            Button(onClick = { onDelete(group.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = groupName,
            onValueChange = onNameChange,
            label = { Text("Group Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = groupOrder,
            onValueChange = onOrderChange,
            label = { Text("Order (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            Button(onClick = onSave) {
                Text(if (groupEditId == null) "Add Group" else "Update Group")
            }
            if (groupEditId != null) {
                Button(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
