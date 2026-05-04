package com.durrr.first.platform.database

import com.durrr.first.TokoDatabase
import com.durrr.first.data.db.DatabaseDriverFactory
import com.durrr.first.data.db.DatabaseProvider

object AndroidDatabaseModule {
    fun provideDatabase(): TokoDatabase {
        DatabaseProvider.init(DatabaseDriverFactory())
        return DatabaseProvider.get()
    }
}
