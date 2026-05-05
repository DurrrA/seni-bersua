package com.durrr.first.features.product.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.durrr.first.core.utils.formatRupiah
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.domain.model.GroupItem
import com.durrr.first.domain.model.Item
import com.durrr.first.ui.design.AppTheme
import com.durrr.first.ui.media.ProductImageBanner
import kotlinx.coroutines.launch

private val FigmaBlue = Color(0xFF273BBF)
private val FigmaBorder = Color(0xFFB7B7B7)

@Composable
fun ProductScreen(
    repo: MenuRepository,
    settingsRepository: SettingsRepository,
    menuSyncRepository: MenuSyncRepository,
    onAddProduct: () -> Unit = {},
    onManageCategories: () -> Unit = {},
    onManageModifiers: () -> Unit = {},
    onEditProduct: (String) -> Unit = { _ -> },
) {
    var groups by remember { mutableStateOf(emptyList<GroupItem>()) }
    var items by remember { mutableStateOf(emptyList<Item>()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterGroupId by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun currentOutletId(): String = settingsRepository.getValue(SettingsRepository.KEY_OUTLET_ID).ifBlank { SettingsRepository.DEFAULT_OUTLET_ID }
    fun serverBaseUrl(): String? = settingsRepository.getOptionalServerBaseUrl()

    fun refresh() {
        groups = repo.getGroups(currentOutletId())
        items = repo.getItems(currentOutletId())
    }

    LaunchedEffect(Unit) {
        refresh()
        val baseUrl = serverBaseUrl()
        if (baseUrl != null) {
            runCatching {
                val pulled = menuSyncRepository.pullFromServer(baseUrl, currentOutletId())
                refresh()
                syncMessage = "Menu synced: $pulled item(s)"
            }.onFailure { syncMessage = it.message ?: "Sync skipped" }
        }
    }

    val filteredItems = items.filter {
        val searchOk = searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.code.orEmpty().contains(searchQuery, true)
        val groupOk = filterGroupId == null || it.groupId == filterGroupId
        searchOk && groupOk
    }

    val groupNameMap = remember(groups) { groups.associate { it.id to it.name } }
    val groupListForTabs = listOf(null) + groups.map { it.id }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
    ) {
        val columns = when {
            maxWidth >= 1500.dp -> 4
            maxWidth >= 980.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Daftar Produk", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FigmaButton("Kelola Kategori", outlined = true, onClick = onManageCategories)
                    FigmaButton("Kelola Modifier", outlined = true, onClick = onManageModifiers)
                    FigmaButton("Tambah Produk", outlined = false, onClick = onAddProduct)
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama atau kode barang...") },
                leadingIcon = { Text("\uD83D\uDD0D") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
            )

            if (!syncMessage.isNullOrBlank()) {
                val isError = syncMessage.orEmpty().contains("fail", ignoreCase = true) ||
                    syncMessage.orEmpty().contains("skip", ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isError) Color(0xFFFFF1F2) else Color(0xFFEEF5FF),
                            RoundedCornerShape(14.dp),
                        )
                        .border(
                            1.dp,
                            if (isError) Color(0xFFFFC2CC) else Color(0xFFB7D4FF),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isError) "\u26A0\uFE0F" else "\u2705")
                    Text(
                        syncMessage.orEmpty(),
                        color = if (isError) Color(0xFF9F1239) else Color(0xFF1D4ED8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Category Tabs
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groupListForTabs.size) { index ->
                    val gid = groupListForTabs[index]
                    FigmaTab(
                        title = if (gid == null) "Semua" else groupNameMap[gid] ?: "Unknown",
                        selected = filterGroupId == gid,
                        onClick = { filterGroupId = gid }
                    )
                }
            }

            // Product Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredItems) { item ->
                    ProductManageCard(
                        item = item,
                        groupName = groupNameMap[item.groupId] ?: "Kategori",
                        onEdit = { onEditProduct(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductManageCard(
    item: Item,
    groupName: String,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, FigmaBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            ProductImageBanner(
                imageUrl = item.imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(if (item.isActive) Color(0xFF0F766E) else Color.Gray, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(if (item.isActive) "Aktif" else "Nonaktif", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(groupName, color = FigmaBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(item.code ?: "-", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatRupiah(item.price), fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FigmaBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun FigmaButton(
    label: String,
    outlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (outlined) Color.White else FigmaBlue,
            contentColor = if (outlined) FigmaBlue else Color.White,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBlue),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(label)
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
            containerColor = if (selected) FigmaBlue else Color.White,
            contentColor = if (selected) Color.White else Color(0xFF64748B),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) FigmaBlue else Color(0xFFE2E8F0)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title)
    }
}
