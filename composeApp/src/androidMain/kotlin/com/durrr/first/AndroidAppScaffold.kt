package com.durrr.first

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.Icon
import com.durrr.first.features.cart.presentation.OrderBuilderScreen
import com.durrr.first.features.cashclosing.presentation.CashClosingScreen
import com.durrr.first.features.cashflow.presentation.CashFlowScreen
import com.durrr.first.features.orders.presentation.OrdersScreen
import com.durrr.first.features.product.presentation.ModifierGroupScreen
import com.durrr.first.features.product.presentation.ProductScreen
import com.durrr.first.features.product.presentation.ProductCategoryScreen
import com.durrr.first.features.product.presentation.ProductEditorScreen
import com.durrr.first.features.recommendation.presentation.RecommendationScreen
import com.durrr.first.features.recap.presentation.RecapScreen
import com.durrr.first.features.settings.presentation.SettingsScreen
import com.durrr.first.features.stock.presentation.StockScreen
import com.durrr.first.features.transaction.presentation.OrderCheckoutScreen
import com.durrr.first.features.transaction.presentation.ReceiptPreviewScreen
import com.durrr.first.ui.AppDependencies
import com.durrr.first.ui.design.Dimens
import kotlinx.coroutines.launch

private enum class MainRoute(val route: String, val title: String, val iconRes: Int) {
    RECAP("recap", "Dashboard", R.drawable.nav_dashboard),
    ORDERS("orders", "Orders", R.drawable.nav_orders),
    MENU("menu", "Produk", R.drawable.nav_produk),
    SETTINGS("settings", "Pengaturan", R.drawable.nav_settings),
}

private const val CASHFLOW_ROUTE = "cashflow"
private const val STOCK_ROUTE = "stock"
private const val CASH_CLOSING_ROUTE = "cashClosing"
private const val ORDER_BUILDER_ROUTE = "orderBuilder"
private const val ORDER_CHECKOUT_ROUTE = "orderCheckout/{draftId}"
private const val RECEIPT_PREVIEW_ROUTE = "receiptPreview/{transaksiId}"
private const val PRODUCT_EDITOR_ROUTE = "productEditor/{itemId}"
private const val PRODUCT_CATEGORY_ROUTE = "productCategories"
private const val MODIFIER_GROUP_ROUTE = "modifierGroups"
private const val RECOMMENDATION_ROUTE = "recommendation"

private val SidebarBlue = Color(0xFF5262BE)
private val SidebarActive = Color(0xFF21A5DF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidAppScaffold(
    dependencies: AppDependencies,
    viewModel: MainViewModel,
    onRequireLocalSetup: () -> Unit = {},
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentDestinationRoute = currentDestination?.route

    val currentRoute = MainRoute.values().firstOrNull { it.route == currentDestinationRoute }
    val isMainRoute = currentRoute != null
    val currentEditorItemId = navBackStackEntry?.arguments?.getString("itemId")
    val topBarTitle = when {
        currentDestinationRoute == CASHFLOW_ROUTE -> "Arus Kas"
        currentDestinationRoute == STOCK_ROUTE -> "Kasir"
        currentDestinationRoute == CASH_CLOSING_ROUTE -> "Cash Closing"
        currentDestinationRoute == ORDER_BUILDER_ROUTE -> "Kasir"
        currentDestinationRoute?.startsWith("orderCheckout/") == true -> "Pembayaran"
        currentDestinationRoute?.contains("receiptPreview") == true -> "Receipt Preview"
        currentDestinationRoute == PRODUCT_CATEGORY_ROUTE -> "Kategori Produk"
        currentDestinationRoute == MODIFIER_GROUP_ROUTE -> "Modifier Group"
        currentDestinationRoute == PRODUCT_EDITOR_ROUTE -> if (currentEditorItemId == "new") "Tambah Barang" else "Edit Barang"
        currentDestinationRoute == RECOMMENDATION_ROUTE -> "Rekomendasi"
        else -> currentRoute?.title ?: "SuCash"
    }

    val navigateToMainRoute: (MainRoute) -> Unit = { route ->
        if (currentDestination?.hierarchy?.any { it.route == route.route } != true) {
            navController.navigate(route.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    val drawerContent: @Composable () -> Unit = {
        SidebarContent(
            currentDestination = currentDestination,
            currentDestinationRoute = currentDestinationRoute,
            onNavigateMain = { route ->
                navigateToMainRoute(route)
                scope.launch { drawerState.close() }
            },
            onNavigateExtra = { route ->
                navController.navigate(route)
                scope.launch { drawerState.close() }
            },
        )
    }

    val appBody: @Composable () -> Unit = {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("\u2630", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        if (!isMainRoute) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Text("<", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = MainRoute.ORDERS.route,
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
                            onProceedToCheckout = { draftId -> navController.navigate("orderCheckout/$draftId") },
                        )
                    }
                    composable(
                        route = ORDER_CHECKOUT_ROUTE,
                        arguments = listOf(navArgument("draftId") { type = NavType.StringType }),
                    ) { entry ->
                        val draftId = entry.arguments?.getString("draftId").orEmpty()
                        OrderCheckoutScreen(
                            draftId = draftId,
                            menuRepository = dependencies.menuRepository,
                            transaksiRepository = dependencies.transaksiRepository,
                            settingsRepository = dependencies.settingsRepository,
                            transaksiSyncRepository = dependencies.transaksiSyncRepository,
                            nowIso = dependencies.nowIso,
                            onBackToOrders = { navController.popBackStack(MainRoute.ORDERS.route, false) },
                            onPreviewReceipt = { transaksiId -> navController.navigate("receiptPreview/$transaksiId") },
                        )
                    }
                    composable(MainRoute.MENU.route) {
                        ProductScreen(
                            repo = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            onAddProduct = { navController.navigate("productEditor/new") },
                            onManageCategories = { navController.navigate(PRODUCT_CATEGORY_ROUTE) },
                            onManageModifiers = { navController.navigate(MODIFIER_GROUP_ROUTE) },
                            onEditProduct = { itemId -> navController.navigate("productEditor/$itemId") },
                        )
                    }
                    composable(
                        route = PRODUCT_EDITOR_ROUTE,
                        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                    ) { entry ->
                        val rawItemId = entry.arguments?.getString("itemId")
                        ProductEditorScreen(
                            repo = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            pickImage = dependencies.pickImage,
                            itemId = rawItemId?.takeUnless { it == "new" },
                            onManageModifiers = { navController.navigate(MODIFIER_GROUP_ROUTE) },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                    composable(PRODUCT_CATEGORY_ROUTE) {
                        ProductCategoryScreen(
                            repo = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                        )
                    }
                    composable(MODIFIER_GROUP_ROUTE) {
                        ModifierGroupScreen(
                            repo = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                        )
                    }
                    composable(MainRoute.RECAP.route) {
                        RecapScreen(
                            recapRepository = dependencies.recapRepository,
                            cashFlowRepository = dependencies.cashFlowRepository,
                            recapSyncRepository = dependencies.recapSyncRepository,
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
                            onRequireLocalSetup = onRequireLocalSetup,
                            onOpenCashFlow = { navController.navigate(CASHFLOW_ROUTE) },
                            onOpenStock = { navController.navigate(STOCK_ROUTE) },
                            onOpenCashClosing = { navController.navigate(CASH_CLOSING_ROUTE) },
                        )
                    }
                    composable(CASHFLOW_ROUTE) {
                        CashFlowScreen(
                            repository = dependencies.cashFlowRepository,
                            cashSessionRepository = dependencies.cashSessionRepository,
                            settingsRepository = dependencies.settingsRepository,
                            todayDate = dependencies.todayDate,
                            nowIso = dependencies.nowIso,
                            onOpenDashboard = { navController.navigate(MainRoute.RECAP.route) },
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
                    composable(RECOMMENDATION_ROUTE) {
                        RecommendationScreen(
                            menuRepository = dependencies.menuRepository,
                            settingsRepository = dependencies.settingsRepository,
                            menuSyncRepository = dependencies.menuSyncRepository,
                            pickDate = dependencies.pickDate,
                        )
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(196.dp),
                drawerContainerColor = Color.Transparent,
            ) {
                drawerContent()
            }
        },
        content = { appBody() },
    )
}

@Composable
private fun SidebarContent(
    currentDestination: androidx.navigation.NavDestination?,
    currentDestinationRoute: String?,
    onNavigateMain: (MainRoute) -> Unit,
    onNavigateExtra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isProductDestination =
        currentDestination?.hierarchy?.any { it.route == MainRoute.MENU.route } == true ||
            currentDestinationRoute == PRODUCT_EDITOR_ROUTE ||
            currentDestinationRoute == PRODUCT_CATEGORY_ROUTE ||
            currentDestinationRoute == MODIFIER_GROUP_ROUTE

    Column(
        modifier = modifier
            .width(188.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .background(SidebarBlue)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.nav_kasir),
                    contentDescription = "SuCash",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SuCash",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Quick Navigation",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SidebarEntry(
            label = "Dashboard",
            iconRes = R.drawable.nav_dashboard,
            selected = currentDestination?.hierarchy?.any { it.route == MainRoute.RECAP.route } == true,
            onClick = { onNavigateMain(MainRoute.RECAP) },
        )
        SidebarEntry(
            label = "Kasir",
            iconRes = R.drawable.nav_kasir,
            selected = currentDestinationRoute == ORDER_BUILDER_ROUTE || currentDestinationRoute?.startsWith("orderCheckout/") == true,
            onClick = { onNavigateExtra(ORDER_BUILDER_ROUTE) },
        )
        SidebarEntry(
            label = "Orders",
            iconRes = R.drawable.nav_orders,
            selected = currentDestination?.hierarchy?.any { it.route == MainRoute.ORDERS.route } == true,
            onClick = { onNavigateMain(MainRoute.ORDERS) },
        )
        SidebarEntry(
            label = "Produk",
            iconRes = R.drawable.nav_produk,
            selected = isProductDestination,
            onClick = { onNavigateMain(MainRoute.MENU) },
        )
        SidebarEntry(
            label = "Arus Kas",
            iconRes = R.drawable.nav_arus_kas,
            selected = currentDestinationRoute == CASHFLOW_ROUTE,
            onClick = { onNavigateExtra(CASHFLOW_ROUTE) },
        )
        SidebarEntry(
            label = "Rekomendasi",
            iconRes = R.drawable.nav_rekomendasi,
            selected = currentDestinationRoute == RECOMMENDATION_ROUTE,
            onClick = { onNavigateExtra(RECOMMENDATION_ROUTE) },
        )
        SidebarEntry(
            label = "Pengaturan",
            iconRes = MainRoute.SETTINGS.iconRes,
            selected = currentDestination?.hierarchy?.any { it.route == MainRoute.SETTINGS.route } == true,
            onClick = { onNavigateMain(MainRoute.SETTINGS) },
        )
        }
    }
}

@Composable
private fun SidebarEntry(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) SidebarActive else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.pagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            Image(
                painter = painterResource(R.drawable.nav_rekomendasi),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
