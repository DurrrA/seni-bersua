package com.durrr.first.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class AppScreen {
    HOME,
    MENU,
    POS,
    RECAP,
    ORDERS,
    SETTINGS,
}

@Composable
fun AppContent(dependencies: AppDependencies) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }

    when (screen) {
        AppScreen.HOME -> HomeScreen(onNavigate = { screen = it })
        AppScreen.MENU -> MenuScreen(
            repo = dependencies.menuRepository,
            settingsRepository = dependencies.settingsRepository,
            menuSyncRepository = dependencies.menuSyncRepository,
        )
        AppScreen.POS -> PosScreen(
            menuRepository = dependencies.menuRepository,
            transaksiRepository = dependencies.transaksiRepository,
            settingsRepository = dependencies.settingsRepository,
            transaksiSyncRepository = dependencies.transaksiSyncRepository,
            nowIso = dependencies.nowIso,
            launchScanner = dependencies.launchScanner,
            scannedToken = null,
            onScannedTokenConsumed = {},
            onPreviewReceipt = {},
        )
        AppScreen.RECAP -> RecapScreen(
            recapRepository = dependencies.recapRepository,
            settingsRepository = dependencies.settingsRepository,
            todayDate = dependencies.todayDate,
        )
        AppScreen.ORDERS -> OrdersScreen(
            orderRepository = dependencies.orderCacheRepository,
            orderSyncRepository = dependencies.orderSyncRepository,
            settingsRepository = dependencies.settingsRepository,
        )
        AppScreen.SETTINGS -> SettingsScreen(
            settingsRepository = dependencies.settingsRepository,
            pickImage = dependencies.pickImage,
            menuSyncRepository = dependencies.menuSyncRepository,
            orderSyncRepository = dependencies.orderSyncRepository,
            transaksiSyncRepository = dependencies.transaksiSyncRepository,
        )
    }
}
