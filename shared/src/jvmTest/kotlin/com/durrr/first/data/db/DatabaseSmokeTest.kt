package com.durrr.first.data.db

import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseSmokeTest {
    @Test
    fun createInsertSelect() {
        val db = DatabaseFactory(DatabaseDriverFactory()).createDatabase()
        val id = "group-1"
        db.tokoQueries.insertGroupItem(id, "Makanan", 1, "default")
        val result = db.tokoQueries.selectGroupItemById(id, "default").executeAsOne()
        assertEquals("Makanan", result.nama)
    }
}
