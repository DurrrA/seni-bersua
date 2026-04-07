package com.durrr.first.data.db

import com.durrr.first.TokoDatabase

class DatabaseFactory(private val driverFactory: DatabaseDriverFactory) {
    fun createDatabase(): TokoDatabase = TokoDatabase(driverFactory.createDriver())
}
