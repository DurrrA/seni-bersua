package com.durrr.first.features.cart.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.domain.model.ModifierGroupBundle
import com.durrr.first.domain.model.ModifierOption
import com.durrr.first.domain.service.IdGenerator
import com.durrr.first.features.cart.domain.OrderDraft
import com.durrr.first.features.cart.domain.OrderDraftLine
import com.durrr.first.features.cart.domain.OrderDraftModifierSelection
import com.durrr.first.features.cart.domain.OrderDraftStore

private data class DraftCartItem(
    val item: Item,
    val qty: Long,
    val basePrice: Long,
    val price: Long,
    val modifiers: List<OrderDraftModifierSelection>,
)

private val FigmaBlue = Color(0xFF273BBF)
private val FigmaBorder = Color(0xFFB7B7B7)
private val BadgeRed = Color(0xFFE53935)

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
    var menuGroups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var cartItems by remember { mutableStateOf(emptyList<DraftCartItem>()) }
    var tableToken by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var expandedModifierItemId by remember { mutableStateOf<String?>(null) }
    var modifierBundleByItemId by remember { mutableStateOf<Map<String, List<ModifierGroupBundle>>>(emptyMap()) }
    var pendingSelectionByItemId by remember { mutableStateOf<Map<String, Map<String, Set<String>>>>(emptyMap()) }

    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()
    fun currentOutletId(): String = settingsRepository.getValue(SettingsRepository.KEY_OUTLET_ID).ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }

    fun loadModifierBundles(itemId: String): List<ModifierGroupBundle> {
        modifierBundleByItemId[itemId]?.let { return it }
        val linkedGroupIds = menuRepository.getModifierGroupIdsForItem(itemId, currentOutletId())
        val bundles = if (linkedGroupIds.isEmpty()) {
            emptyList()
        } else {
            menuRepository.getModifierGroupBundles(currentOutletId())
                .filter { linkedGroupIds.contains(it.group.id) }
        }
        modifierBundleByItemId = modifierBundleByItemId + (itemId to bundles)
        return bundles
    }

    fun defaultSelectionMap(bundles: List<ModifierGroupBundle>): Map<String, Set<String>> {
        return bundles.associate { bundle ->
            val defaultSelection = if (bundle.group.selectionType == "SINGLE") {
                bundle.options.firstOrNull { it.isDefault }?.let { setOf(it.id) } ?: emptySet()
            } else {
                bundle.options.filter { it.isDefault }.map { it.id }.toSet()
            }
            bundle.group.id to defaultSelection
        }
    }

    fun toSelections(
        bundles: List<ModifierGroupBundle>,
        selectionMap: Map<String, Set<String>>,
    ): List<OrderDraftModifierSelection> {
        return bundles.flatMap { bundle ->
            val selectedIds = selectionMap[bundle.group.id].orEmpty()
            bundle.options.filter { selectedIds.contains(it.id) }.map { option ->
                OrderDraftModifierSelection(
                    groupId = bundle.group.id,
                    groupName = bundle.group.name,
                    optionId = option.id,
                    optionName = option.name,
                    priceDelta = option.priceDelta,
                )
            }
        }
    }

    fun cartLineKey(itemId: String, modifiers: List<OrderDraftModifierSelection>): String {
        val optionKey = modifiers.map { it.optionId }.sorted().joinToString("|")
        return "$itemId::$optionKey"
    }

    fun addToCart(item: Item, modifiers: List<OrderDraftModifierSelection>) {
        val linePrice = item.price + modifiers.sumOf { it.priceDelta }
        val key = cartLineKey(item.id, modifiers)
        val current = cartItems.firstOrNull { cartLineKey(it.item.id, it.modifiers) == key }
        cartItems = if (current == null) {
            cartItems + DraftCartItem(
                item = item,
                qty = 1,
                basePrice = item.price,
                price = linePrice,
                modifiers = modifiers,
            )
        } else {
            cartItems.map {
                if (cartLineKey(it.item.id, it.modifiers) == key) it.copy(qty = it.qty + 1) else it
            }
        }
    }

    fun addQuick(item: Item) {
        val bundles = loadModifierBundles(item.id)
        if (bundles.isEmpty()) {
            addToCart(item, emptyList())
            return
        }
        val selectionMap = pendingSelectionByItemId[item.id] ?: defaultSelectionMap(bundles).also {
            pendingSelectionByItemId = pendingSelectionByItemId + (item.id to it)
        }
        addToCart(item, toSelections(bundles, selectionMap))
    }

    fun removeOne(itemId: String) {
        val index = cartItems.indexOfLast { it.item.id == itemId }
        if (index < 0) return
        val target = cartItems[index]
        cartItems = if (target.qty <= 1) {
            cartItems.toMutableList().also { it.removeAt(index) }
        } else {
            cartItems.mapIndexed { i, line -> if (i == index) line.copy(qty = line.qty - 1) else line }
        }
    }

    fun proceedToCheckout() {
        if (cartItems.isEmpty()) return
        val draftId = IdGenerator.newId("odr_")
        OrderDraftStore.putDraft(
            OrderDraft(
                id = draftId,
                tableToken = tableToken.ifBlank { null },
                lines = cartItems.map {
                    OrderDraftLine(
                        itemId = it.item.id,
                        itemName = it.item.name,
                        qty = it.qty,
                        basePrice = it.basePrice,
                        price = it.price,
                        modifiers = it.modifiers,
                    )
                },
            )
        )
        onProceedToCheckout(draftId)
    }

    suspend fun refreshMenuFromServer() {
        menuItems = menuRepository.getItems(currentOutletId()).filter { it.isActive }
        menuGroups = menuRepository.getGroups(currentOutletId())
        val baseUrl = serverBaseUrl()
        if (baseUrl != null) {
            runCatching {
                val pulled = menuSyncRepository.pullFromServer(baseUrl, currentOutletId())
                menuItems = menuRepository.getItems(currentOutletId()).filter { it.isActive }
                menuGroups = menuRepository.getGroups(currentOutletId())
                syncMessage = "Menu synced: $pulled item(s)"
            }.onFailure {
                syncMessage = it.message ?: "Menu sync pending"
            }
        }
    }

    LaunchedEffect(Unit) { refreshMenuFromServer() }
    LaunchedEffect(scannedToken) {
        if (!scannedToken.isNullOrBlank()) {
            tableToken = scannedToken
            onScannedTokenConsumed()
        }
    }

    val groupNameMap = remember(menuGroups) { menuGroups.associate { it.id to it.name } }
    val groups = listOf(null) + menuItems.mapNotNull { it.groupId }.distinct()
    val filteredMenu = menuItems.filter { item ->
        (searchQuery.isBlank() || item.name.contains(searchQuery, true) || item.code.orEmpty().contains(searchQuery, true)) &&
            (selectedGroupId == null || item.groupId == selectedGroupId)
    }
    val itemCount = cartItems.sumOf { it.qty }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
    ) {
        val columns = when {
            maxWidth >= 1500.dp -> 4
            maxWidth >= 980.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama atau kode barang...") },
                leadingIcon = { Text("\uD83D\uDD0D") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tableToken,
                    onValueChange = { tableToken = it },
                    placeholder = { Text("Token / Meja") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = launchScanner, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text("Scan QR")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(groups.size) { index ->
                    val groupId = groups[index]
                    FigmaTab(
                        title = if (groupId == null) "Semua Produk" else groupNameMap[groupId] ?: inferGroupName(itemName = menuItems.firstOrNull { it.groupId == groupId }?.name),
                        selected = selectedGroupId == groupId,
                        onClick = { selectedGroupId = groupId },
                    )
                }
            }

            if (!syncMessage.isNullOrBlank()) {
                Text(syncMessage.orEmpty(), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                items(filteredMenu) { item ->
                    val itemQty = cartItems.filter { it.item.id == item.id }.sumOf { it.qty }
                    val bundles = modifierBundleByItemId[item.id].orEmpty()
                    val selectionMap = pendingSelectionByItemId[item.id]
                        ?: if (bundles.isNotEmpty()) defaultSelectionMap(bundles) else emptyMap()

                    ProductCard(
                        item = item,
                        groupName = groupNameMap[item.groupId] ?: inferGroupName(item.name),
                        quantity = itemQty,
                        modifierBundles = bundles,
                        modifierSelectionByGroup = selectionMap,
                        modifierExpanded = expandedModifierItemId == item.id,
                        onAdd = {
                            val loaded = loadModifierBundles(item.id)
                            if (loaded.isEmpty()) {
                                addToCart(item, emptyList())
                            } else {
                                pendingSelectionByItemId = pendingSelectionByItemId + (
                                    item.id to (pendingSelectionByItemId[item.id] ?: defaultSelectionMap(loaded))
                                )
                                expandedModifierItemId = item.id
                            }
                        },
                        onIncrease = { addQuick(item) },
                        onDecrease = { removeOne(item.id) },
                        onToggleModifierOption = { groupId, option, selected ->
                            val loaded = if (bundles.isEmpty()) loadModifierBundles(item.id) else bundles
                            val group = loaded.firstOrNull { it.group.id == groupId }?.group ?: return@ProductCard
                            val current = pendingSelectionByItemId[item.id].orEmpty().toMutableMap()
                            val currentSet = current[groupId].orEmpty()
                            val nextSet = when {
                                group.selectionType == "SINGLE" && selected -> setOf(option.id)
                                group.selectionType == "SINGLE" && !selected -> emptySet()
                                selected -> (currentSet + option.id).take(group.maxSelection).toSet()
                                else -> currentSet - option.id
                            }
                            current[groupId] = nextSet
                            pendingSelectionByItemId = pendingSelectionByItemId + (item.id to current)
                        },
                        onApplyModifierAdd = {
                            val loaded = if (bundles.isEmpty()) loadModifierBundles(item.id) else bundles
                            val selected = toSelections(loaded, pendingSelectionByItemId[item.id].orEmpty())
                            addToCart(item, selected)
                            expandedModifierItemId = null
                        },
                        onCancelModifier = { expandedModifierItemId = null },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            FloatingActionButton(
                onClick = ::proceedToCheckout,
                containerColor = FigmaBlue,
                contentColor = Color.White,
            ) {
                Text("🛒")
            }
            if (itemCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                        .background(BadgeRed, CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = itemCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun inferGroupName(itemName: String?): String {
    val text = itemName.orEmpty().lowercase()
    return when {
        text.contains("coffee") || text.contains("kopi") || text.contains("latte") ||
            text.contains("espresso") || text.contains("cappuccino") || text.contains("macchiato") -> "Coffee"
        else -> "Non-Coffee"
    }
}

@Composable
private fun ProductCard(
    item: Item,
    groupName: String,
    quantity: Long,
    modifierBundles: List<ModifierGroupBundle>,
    modifierSelectionByGroup: Map<String, Set<String>>,
    modifierExpanded: Boolean,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onToggleModifierOption: (groupId: String, option: ModifierOption, selected: Boolean) -> Unit,
    onApplyModifierAdd: () -> Unit,
    onCancelModifier: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Color.Transparent, RoundedCornerShape(18.dp)),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (modifierExpanded && modifierBundles.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFF), RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFDCE6FF), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(item.code ?: "Kode Produk", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(groupName, color = Color(0xFF334155), style = MaterialTheme.typography.labelMedium)
                    }
                }
                modifierBundles.forEach { bundle ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(bundle.group.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(bundle.options.size) { index ->
                                val option = bundle.options[index]
                                val selected = modifierSelectionByGroup[bundle.group.id].orEmpty().contains(option.id)
                                OptionChip(
                                    label = option.name,
                                    priceDelta = option.priceDelta,
                                    selected = selected,
                                    onClick = { onToggleModifierOption(bundle.group.id, option, !selected) },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancelModifier, modifier = Modifier.weight(1f)) { Text("Batal") }
                    Button(
                        onClick = onApplyModifierAdd,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                    ) { Text("Tambah + Modifier") }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFF6E4B8), Color(0xFFB4782C))),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .background(Color(0xFF5B5B5B), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(groupName, color = Color.White)
                }
            }
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(item.code ?: "Kode Produk", color = Color.Gray)
                Text("Rp ${item.price}", color = FigmaBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                if (quantity <= 0) {
                    Button(
                        onClick = onAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Tambah") }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onDecrease, modifier = Modifier.weight(1f)) { Text("-") }
                        Text(
                            quantity.toString(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        OutlinedButton(onClick = onIncrease, modifier = Modifier.weight(1f)) { Text("+") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    priceDelta: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) FigmaBlue else Color.White,
            contentColor = if (selected) Color.White else Color(0xFF475569),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) FigmaBlue else Color(0xFFD1D9E6),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        val suffix = if (priceDelta == 0L) "" else " +$priceDelta"
        Text("$label$suffix")
    }
}

@Composable
private fun FigmaTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = if (selected) FigmaBlue else Color(0xFF8A8A8A),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) FigmaBlue else FigmaBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(title)
    }
}
