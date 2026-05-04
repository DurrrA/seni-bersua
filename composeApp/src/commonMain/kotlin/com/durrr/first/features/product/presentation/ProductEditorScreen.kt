package com.durrr.first.features.product.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.ui.design.AppTheme
import kotlinx.coroutines.launch

private val FigmaBlue = Color(0xFF273BBF)
private val FigmaBorder = Color(0xFFD5D9E2)

@Composable
fun ProductEditorScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
    itemId: String?,
    onManageModifiers: () -> Unit = {},
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var modifierBundles by remember { mutableStateOf(emptyList<ModifierGroupBundle>()) }
    var itemName by remember { mutableStateOf("") }
    var itemCode by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var itemActive by remember { mutableStateOf(true) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var selectedModifierGroupIds by remember { mutableStateOf(setOf<String>()) }
    var showGroupDropdown by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun currentOutletId(): String {
        return settingsRepository.getValue(SettingsRepository.KEY_OUTLET_ID)
            .ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    }

    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()

    LaunchedEffect(itemId) {
        groups = repo.getGroups(currentOutletId())
        modifierBundles = repo.getModifierGroupBundles(currentOutletId())
        if (itemId.isNullOrBlank()) {
            itemName = ""
            itemCode = ""
            itemPrice = ""
            itemActive = true
            selectedGroupId = null
            selectedModifierGroupIds = emptySet()
            return@LaunchedEffect
        }

        val item = repo.getItems(currentOutletId()).firstOrNull { it.id == itemId } ?: return@LaunchedEffect
        itemName = item.name
        itemCode = item.code.orEmpty()
        itemPrice = item.price.toString()
        itemActive = item.isActive
        selectedGroupId = item.groupId
        selectedModifierGroupIds = repo.getModifierGroupIdsForItem(item.id, currentOutletId())
    }

    fun save() {
        val name = itemName.trim()
        val price = itemPrice.toLongOrNull()
        if (name.isBlank()) {
            statusMessage = "Nama barang wajib diisi."
            return
        }
        if (price == null || price < 0L) {
            statusMessage = "Harga jual harus angka valid."
            return
        }

        val model = Item(
            id = itemId ?: IdGenerator.newId("item_"),
            name = name,
            price = price,
            groupId = selectedGroupId,
            code = itemCode.trim().ifBlank { null },
            isActive = itemActive,
            outletId = currentOutletId(),
        )
        repo.upsertItem(model, currentOutletId())
        repo.assignModifierGroupsToItem(model.id, selectedModifierGroupIds.toList(), currentOutletId())
        scope.launch {
            val baseUrl = serverBaseUrl()
            if (baseUrl != null) runCatching {
                menuSyncRepository.pushToServer(baseUrl, currentOutletId())
            }
        }
        onSaved()
    }

    ProductEditorContent(
        title = if (itemId == null) "Tambah Barang" else "Edit Barang",
        groups = groups,
        modifierBundles = modifierBundles,
        selectedGroupId = selectedGroupId,
        showGroupDropdown = showGroupDropdown,
        onOpenGroupDropdown = { showGroupDropdown = true },
        onDismissGroupDropdown = { showGroupDropdown = false },
        onSelectGroup = {
            selectedGroupId = it
            showGroupDropdown = false
        },
        itemName = itemName,
        onItemNameChange = { itemName = it },
        itemCode = itemCode,
        onItemCodeChange = { itemCode = it },
        itemPrice = itemPrice,
        onItemPriceChange = { itemPrice = it },
        itemActive = itemActive,
        onItemActiveChange = { itemActive = it },
        selectedModifierGroupIds = selectedModifierGroupIds,
        onToggleModifierGroup = { modifierGroupId ->
            selectedModifierGroupIds = if (selectedModifierGroupIds.contains(modifierGroupId)) {
                selectedModifierGroupIds - modifierGroupId
            } else {
                selectedModifierGroupIds + modifierGroupId
            }
        },
        onManageModifiers = onManageModifiers,
        statusMessage = statusMessage,
        onSave = ::save,
    )
}

@Composable
private fun ProductEditorContent(
    title: String,
    groups: List<GroupItem>,
    modifierBundles: List<ModifierGroupBundle>,
    selectedGroupId: String?,
    showGroupDropdown: Boolean,
    onOpenGroupDropdown: () -> Unit,
    onDismissGroupDropdown: () -> Unit,
    onSelectGroup: (String) -> Unit,
    itemName: String,
    onItemNameChange: (String) -> Unit,
    itemCode: String,
    onItemCodeChange: (String) -> Unit,
    itemPrice: String,
    onItemPriceChange: (String) -> Unit,
    itemActive: Boolean,
    onItemActiveChange: (Boolean) -> Unit,
    selectedModifierGroupIds: Set<String>,
    onToggleModifierGroup: (String) -> Unit,
    onManageModifiers: () -> Unit,
    statusMessage: String?,
    onSave: () -> Unit,
) {
    val selectedModifierCount = selectedModifierGroupIds.size
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .border(1.dp, FigmaBorder, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (title == "Tambah Barang") {
                    "Isi detail produk baru lalu pilih modifier group bila diperlukan."
                } else {
                    "Perbarui detail produk dan pengaturan modifier group."
                },
                color = Color(0xFF666666),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = itemName,
                onValueChange = onItemNameChange,
                label = { Text("Nama barang") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = itemCode,
                onValueChange = onItemCodeChange,
                label = { Text("Kode barang") },
                supportingText = { Text("Opsional. Gunakan kode unik untuk pencarian cepat.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = itemPrice,
                onValueChange = onItemPriceChange,
                label = { Text("Harga jual") },
                supportingText = { Text("Masukkan angka tanpa titik atau koma.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onOpenGroupDropdown,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF333333),
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Pilih kategori")
            }
            DropdownMenu(expanded = showGroupDropdown, onDismissRequest = onDismissGroupDropdown) {
                if (groups.isEmpty()) {
                    DropdownMenuItem(text = { Text("Belum ada kategori") }, onClick = onDismissGroupDropdown)
                } else {
                    groups.sortedBy { it.order }.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = { onSelectGroup(group.id) },
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Status aktif")
                Switch(checked = itemActive, onCheckedChange = onItemActiveChange)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Modifier groups ($selectedModifierCount dipilih)",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = onManageModifiers,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FigmaBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    ) {
                        Text("Kelola modifier")
                    }
                }
                if (modifierBundles.isEmpty()) {
                    Text("Belum ada modifier group. Tambahkan dulu seperti Sugar Level, Ice Level, Size, atau Add-ons.")
                } else {
                    modifierBundles.forEach { bundle ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = selectedModifierGroupIds.contains(bundle.group.id),
                                    onCheckedChange = { onToggleModifierGroup(bundle.group.id) },
                                )
                                Column {
                                    Text(bundle.group.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (bundle.group.selectionType == "MULTIPLE") {
                                            "Bisa pilih sampai ${bundle.group.maxSelection}"
                                        } else {
                                            "Pilih satu"
                                        },
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            Text(
                                bundle.options.joinToString(", ") { option ->
                                    if (option.priceDelta == 0L) option.name else "${option.name} (+${option.priceDelta})"
                                },
                                color = Color(0xFF555555),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (selectedModifierCount == 0) {
                        Text(
                            "Tidak ada modifier group dipilih. Produk ini akan dijual tanpa opsi tambahan.",
                            color = Color(0xFF666666),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage.orEmpty(), color = Color(0xFFB91C1C))
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue, contentColor = Color.White),
            ) {
                Text("Simpan")
            }
        }
    }
}

@Preview
@Composable
private fun ProductEditorScreenPreview() {
    AppTheme {
        var selectedGroupId by remember { mutableStateOf<String?>(null) }
        var showGroupDropdown by remember { mutableStateOf(false) }
        var itemName by remember { mutableStateOf("Americano") }
        var itemCode by remember { mutableStateOf("MN003") }
        var itemPrice by remember { mutableStateOf("22000") }
        var itemActive by remember { mutableStateOf(true) }

        ProductEditorContent(
            title = "Tambah Barang",
            groups = listOf(
                GroupItem("grp1", "Minuman", 1),
                GroupItem("grp2", "Makanan", 2),
            ),
            modifierBundles = listOf(
                ModifierGroupBundle(
                    group = com.durrr.first.domain.model.ModifierGroup("mod1", "Sugar Level", "SINGLE", true, 1),
                    options = listOf(
                        com.durrr.first.domain.model.ModifierOption("opt1", "mod1", "0%", 0, 1, false),
                        com.durrr.first.domain.model.ModifierOption("opt2", "mod1", "50%", 0, 2, true),
                    ),
                ),
            ),
            selectedGroupId = selectedGroupId,
            showGroupDropdown = showGroupDropdown,
            onOpenGroupDropdown = { showGroupDropdown = true },
            onDismissGroupDropdown = { showGroupDropdown = false },
            onSelectGroup = {
                selectedGroupId = it
                showGroupDropdown = false
            },
            itemName = itemName,
            onItemNameChange = { itemName = it },
            itemCode = itemCode,
            onItemCodeChange = { itemCode = it },
            itemPrice = itemPrice,
            onItemPriceChange = { itemPrice = it },
            itemActive = itemActive,
            onItemActiveChange = { itemActive = it },
            selectedModifierGroupIds = setOf("mod1"),
            onToggleModifierGroup = {},
            onManageModifiers = {},
            statusMessage = null,
            onSave = {},
        )
    }
}
