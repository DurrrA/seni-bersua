package com.durrr.first

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.durrr.first.data.db.AndroidPlatformContextHolder
import com.durrr.first.data.db.DatabaseDriverFactory
import com.durrr.first.data.db.DatabaseProvider
import com.durrr.first.data.repo.CashFlowRepository
import com.durrr.first.data.repo.MenuRepository
import com.durrr.first.data.repo.MenuSyncRepository
import com.durrr.first.data.repo.OrderCacheRepository
import com.durrr.first.data.repo.OrderSyncRepository
import com.durrr.first.data.repo.RecapRepository
import com.durrr.first.data.repo.ReceiptRepository
import com.durrr.first.data.repo.SettingsRepository
import com.durrr.first.data.repo.StockRepository
import com.durrr.first.data.repo.CashSessionRepository
import com.durrr.first.data.repo.SyncRepository
import com.durrr.first.data.repo.TransaksiRepository
import com.durrr.first.data.repo.TransaksiSyncRepository
import com.durrr.first.network.ServerApiClient
import com.durrr.first.ui.AppDependencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberAppDependencies(
    context: Context,
    launchScanner: () -> Unit,
    pickImage: ((String?) -> Unit) -> Unit = { onPicked -> onPicked(null) },
): AppDependencies {
    val db = remember {
        AndroidPlatformContextHolder.context = context.applicationContext
        DatabaseProvider.init(DatabaseDriverFactory())
        DatabaseProvider.get()
    }
    val apiClient = remember { ServerApiClient() }
    
    val menuRepository = remember { MenuRepository(db) }
    val transaksiRepository = remember { TransaksiRepository(db) }
    val recapRepository = remember { RecapRepository(db) }
    val cashFlowRepository = remember { CashFlowRepository(db) }
    val stockRepository = remember { StockRepository(db) }
    val cashSessionRepository = remember { CashSessionRepository(db) }
    val receiptRepository = remember { ReceiptRepository(db) }
    val settingsRepository = remember { SettingsRepository(db) }
    val orderRepository = remember { OrderCacheRepository(db) }
    val syncRepository = remember { SyncRepository(db) }
    val isoFormatter = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }
    val nowIso = remember { { isoFormatter.format(Date()) } }

    // Sync Repositories
    val orderSyncRepository = remember { OrderSyncRepository(orderRepository, apiClient) }
    val menuSyncRepository = remember { MenuSyncRepository(menuRepository, apiClient) }
    val transaksiSyncRepository = remember {
        TransaksiSyncRepository(
            syncRepository = syncRepository,
            apiClient = apiClient,
            nowIso = nowIso,
        )
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    return AppDependencies(
        menuRepository = menuRepository,
        transaksiRepository = transaksiRepository,
        recapRepository = recapRepository,
        cashFlowRepository = cashFlowRepository,
        stockRepository = stockRepository,
        cashSessionRepository = cashSessionRepository,
        receiptRepository = receiptRepository,
        settingsRepository = settingsRepository,
        orderCacheRepository = orderRepository,
        orderSyncRepository = orderSyncRepository,
        menuSyncRepository = menuSyncRepository,
        transaksiSyncRepository = transaksiSyncRepository,
        nowIso = nowIso,
        todayDate = { dateFormatter.format(Date()) },
        launchScanner = launchScanner,
        pickImage = pickImage,
    )
}
