package com.durrr.first.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.durrr.first.TokoDatabase

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(TokoDatabase.Schema, AndroidPlatformContextHolder.context, "toko.db")
    }
}
