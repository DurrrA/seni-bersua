package com.durrr.first

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.durrr.first.ui.AppDependencies
import com.durrr.first.ui.CashFlowScreen
import com.durrr.first.ui.MenuScreen
import com.durrr.first.ui.OrderBuilderScreen
import com.durrr.first.ui.OrderCheckoutScreen
import com.durrr.first.ui.OrdersScreen
import com.durrr.first.ui.RecapScreen
import com.durrr.first.ui.ReceiptPreviewScreen
import com.durrr.first.ui.SettingsScreen
import com.durrr.first.ui.StockScreen
import com.durrr.first.ui.CashClosingScreen
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

private enum class MainRoute(val route: String, val title: String, val shortLabel: String) {
    ORDERS("orders", "Orders", "O"),
    MENU("menu", "Menu", "M"),
    RECAP("recap", "Recap", "R"),
    SETTINGS("settings", "Settings", "S"),
}

private const val CASHFLOW_ROUTE = "cashflow"
private const val STOCK_ROUTE = "stock"
private const val CASH_CLOSING_ROUTE = "cashClosing"
private const val ORDER_BUILDER_ROUTE = "orderBuilder"
private const val ORDER_CHECKOUT_ROUTE = "orderCheckout/{draftId}"
private const val RECEIPT_PREVIEW_ROUTE = "receiptPreview/{transaksiId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAppScaffold(
    dependencies: AppDependencies,
    viewModel: MainViewModel,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentDestinationRoute = currentDestination?.route

    val currentRoute = MainRoute.values().firstOrNull { it.route == currentDestinationRoute }
    val isMainRoute = currentRoute != null
    val showBackInTopBar = !isMainRoute && navController.previousBackStackEntry != null
    val topBarTitle = when {
        currentDestinationRoute == CASHFLOW_ROUTE -> "Cash Flow"
        currentDestinationRoute == STOCK_ROUTE -> "Stock"
        currentDestinationRoute == CASH_CLOSING_ROUTE -> "Cash Closing"
        currentDestinationRoute == ORDER_BUILDER_ROUTE -> "New Order"
        currentDestinationRoute == ORDER_CHECKOUT_ROUTE -> "Checkout"
        currentDestinationRoute?.startsWith("orderCheckout/") == true -> "Checkout"
        currentDestinationRoute?.contains("receiptPreview") == true -> "Receipt Preview"
        else -> currentRoute?.title ?: "SuCash"
    }
    val navigateToMainRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(Dimens.md),
                ) {
                    Text("SuCash", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Navigate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.padding(top = Dimens.md)) {
                        MainRoute.values().forEach { item ->
                            val selected = currentDestination
                                ?.hierarchy
                                ?.any { it.route == item.route } == true
                            NavigationDrawerItem(
                                label = { Text(item.title) },
                                selected = selected,
                                onClick = {
                                    navigateToMainRoute(item.route)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Text(item.shortLabel) },
                            )
                        }
                        NavigationDrawerItem(
                            label = { Text("Stock") },
                            selected = currentDestinationRoute == STOCK_ROUTE,
                            onClick = {
                                navController.navigate(STOCK_ROUTE)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Text("ST") },
                        )
                        NavigationDrawerItem(
                            label = { Text("Cash Closing") },
                            selected = currentDestinationRoute == CASH_CLOSING_ROUTE,
                            onClick = {
                                navController.navigate(CASH_CLOSING_ROUTE)
                                scope.launch { drawerState.close() }
                            },
                            icon = { Text("CC") },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        if (showBackInTopBar) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Text("<")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("\u2630")
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = MainRoute.ORDERS.route,
                modifier = Modifier.padding(paddingValues),
            ) {
                composable(MainRoute.ORDERS.route) {
                    OrdersScreen(
                        orderRepository = dependencies.orderCacheRepository,
                        orderSyncRepository = dependencies.orderSyncRepository,
                        settingsRepository = dependencies.settingsRepository,
                        onCreateWalkInOrder = { navController.navigate(ORDER_BUILDER_ROUTE) },
                    )
                }
                composable(ORDER_BUILDER_ROUTE) {
                    OrderBuilderScreen(
                        menuRepository = dependencies.menuRepository,
                        menuSyncRepository = dependencies.menuSyncRepository,
                        settingsRepository = dependencies.settingsRepository,
                        launchScanner = dependencies.launchScanner,
                        scannedToken = viewModel.lastScannedToken,
                        onScannedTokenConsumed = viewModel::clearScannedToken,
                        onProceedToCheckout = { draftId ->
                            navController.navigate("orderCheckout/$draftId")
                        },
                    )
                }
                composable(
                    route = ORDER_CHECKOUT_ROUTE,
                    arguments = listOf(navArgument("draftId") { type = NavType.StringType }),
                ) { entry ->
                    val draftId = entry.arguments?.getString("draftId").orEmpty()
                    OrderCheckoutScreen(
                        draftId = draftId,
                        transaksiRepository = dependencies.transaksiRepository,
                        settingsRepository = dependencies.settingsRepository,
                        transaksiSyncRepository = dependencies.transaksiSyncRepository,
                        nowIso = dependencies.nowIso,
                        onBackToOrders = {
                            navController.popBackStack(MainRoute.ORDERS.route, false)
                        },
                        onPreviewReceipt = { transaksiId ->
                            navController.navigate("receiptPreview/$transaksiId")
                        },
                    )
                }
                composable(MainRoute.MENU.route) {
                    MenuScreen(
                        repo = dependencies.menuRepository,
                        settingsRepository = dependencies.settingsRepository,
                        menuSyncRepository = dependencies.menuSyncRepository,
                    )
                }
                composable(MainRoute.RECAP.route) {
                    RecapScreen(
                        recapRepository = dependencies.recapRepository,
                        settingsRepository = dependencies.settingsRepository,
                        todayDate = dependencies.todayDate,
                        onOpenCashFlow = { navController.navigate(CASHFLOW_ROUTE) },
                        onOpenStock = { navController.navigate(STOCK_ROUTE) },
                        onOpenCashClosing = { navController.navigate(CASH_CLOSING_ROUTE) },
                    )
                }
                composable(MainRoute.SETTINGS.route) {
                    SettingsScreen(
                        settingsRepository = dependencies.settingsRepository,
                        pickImage = dependencies.pickImage,
                        menuSyncRepository = dependencies.menuSyncRepository,
                        orderSyncRepository = dependencies.orderSyncRepository,
                        transaksiSyncRepository = dependencies.transaksiSyncRepository,
                        onOpenCashFlow = { navController.navigate(CASHFLOW_ROUTE) },
                        onOpenStock = { navController.navigate(STOCK_ROUTE) },
                        onOpenCashClosing = { navController.navigate(CASH_CLOSING_ROUTE) },
                    )
                }
                composable(CASHFLOW_ROUTE) {
                    CashFlowScreen(
                        repository = dependencies.cashFlowRepository,
                        settingsRepository = dependencies.settingsRepository,
                        todayDate = dependencies.todayDate,
                    )
                }
                composable(STOCK_ROUTE) {
                    StockScreen(
                        stockRepository = dependencies.stockRepository,
                        menuRepository = dependencies.menuRepository,
                        settingsRepository = dependencies.settingsRepository,
                        nowIso = dependencies.nowIso,
                    )
                }
                composable(CASH_CLOSING_ROUTE) {
                    CashClosingScreen(
                        cashSessionRepository = dependencies.cashSessionRepository,
                        settingsRepository = dependencies.settingsRepository,
                        nowIso = dependencies.nowIso,
                    )
                }
                composable(
                    route = RECEIPT_PREVIEW_ROUTE,
                    arguments = listOf(navArgument("transaksiId") { type = NavType.StringType }),
                ) { entry ->
                    val transaksiId = entry.arguments?.getString("transaksiId").orEmpty()
                    ReceiptPreviewScreen(
                        transaksiId = transaksiId,
                        receiptRepository = dependencies.receiptRepository,
                        settingsRepository = dependencies.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
