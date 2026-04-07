package com.durrr.first.data.db

import app.cash.sqldelight.db.SqlDriver
import com.durrr.first.TokoDatabase

object DatabaseProvider {
    private var database: TokoDatabase? = null

    fun init(driverFactory: DatabaseDriverFactory) {
        val driver = driverFactory.createDriver()
        applyBestEffortMigrations(driver)
        database = TokoDatabase(driver)
    }

    fun get(): TokoDatabase {
        return database ?: error("DatabaseProvider not initialized")
    }

    private fun applyBestEffortMigrations(driver: SqlDriver) {
        val statements = listOf(
            "CREATE TABLE IF NOT EXISTS app_settings (setting_key TEXT NOT NULL PRIMARY KEY, setting_value TEXT)",
            "CREATE TABLE IF NOT EXISTS order_header (id_order TEXT NOT NULL PRIMARY KEY, token TEXT, status TEXT NOT NULL, notes TEXT, created_at TEXT NOT NULL, updated_at TEXT, outlet_id TEXT NOT NULL DEFAULT 'default')",
            "CREATE TABLE IF NOT EXISTS order_item (id_order_item TEXT NOT NULL PRIMARY KEY, id_order TEXT NOT NULL, id_item TEXT, nama_item TEXT NOT NULL, qty INTEGER NOT NULL, price INTEGER NOT NULL, note TEXT, FOREIGN KEY (id_order) REFERENCES order_header(id_order) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS sync_outbox (id_event TEXT NOT NULL PRIMARY KEY, event_type TEXT NOT NULL, payload_json TEXT NOT NULL, created_at TEXT NOT NULL, sent_at TEXT, outlet_id TEXT NOT NULL DEFAULT 'default')",
            "ALTER TABLE toko_group_item ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE toko_item ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE toko_transaksi ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE toko_pembayaran ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE order_header ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE sync_outbox ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "CREATE TABLE IF NOT EXISTS stock_item_balance (item_id TEXT NOT NULL, outlet_id TEXT NOT NULL DEFAULT 'default', qty_on_hand INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL, PRIMARY KEY (item_id, outlet_id), FOREIGN KEY (item_id) REFERENCES toko_item(id_item))",
            "CREATE TABLE IF NOT EXISTS stock_ledger (ledger_id TEXT NOT NULL PRIMARY KEY, outlet_id TEXT NOT NULL DEFAULT 'default', item_id TEXT NOT NULL, movement_type TEXT NOT NULL, qty_delta INTEGER NOT NULL, reference_type TEXT, reference_id TEXT, reason TEXT, created_by TEXT, created_at TEXT NOT NULL, FOREIGN KEY (item_id) REFERENCES toko_item(id_item))",
            "CREATE TABLE IF NOT EXISTS stock_threshold (item_id TEXT NOT NULL, outlet_id TEXT NOT NULL DEFAULT 'default', min_qty INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (item_id, outlet_id), FOREIGN KEY (item_id) REFERENCES toko_item(id_item))",
            "CREATE TABLE IF NOT EXISTS cash_session (session_id TEXT NOT NULL PRIMARY KEY, outlet_id TEXT NOT NULL DEFAULT 'default', opened_by TEXT NOT NULL, opened_at TEXT NOT NULL, opening_cash INTEGER NOT NULL, closed_by TEXT, closed_at TEXT, closing_cash_counted INTEGER, expected_cash INTEGER, variance INTEGER, status TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS cash_movement (movement_id TEXT NOT NULL PRIMARY KEY, session_id TEXT NOT NULL, outlet_id TEXT NOT NULL DEFAULT 'default', movement_type TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT, created_by TEXT, created_at TEXT NOT NULL, FOREIGN KEY (session_id) REFERENCES cash_session(session_id) ON DELETE CASCADE)",
            "ALTER TABLE cash_session ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE cash_session ADD COLUMN opened_by TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE cash_session ADD COLUMN opened_at TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE cash_session ADD COLUMN opening_cash INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE cash_session ADD COLUMN closed_by TEXT",
            "ALTER TABLE cash_session ADD COLUMN closed_at TEXT",
            "ALTER TABLE cash_session ADD COLUMN closing_cash_counted INTEGER",
            "ALTER TABLE cash_session ADD COLUMN expected_cash INTEGER",
            "ALTER TABLE cash_session ADD COLUMN variance INTEGER",
            "ALTER TABLE cash_session ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'",
            "ALTER TABLE cash_movement ADD COLUMN session_id TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE cash_movement ADD COLUMN outlet_id TEXT NOT NULL DEFAULT 'default'",
            "ALTER TABLE cash_movement ADD COLUMN movement_type TEXT NOT NULL DEFAULT 'CASH_IN'",
            "ALTER TABLE cash_movement ADD COLUMN amount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE cash_movement ADD COLUMN note TEXT",
            "ALTER TABLE cash_movement ADD COLUMN created_by TEXT",
            "ALTER TABLE cash_movement ADD COLUMN created_at TEXT NOT NULL DEFAULT ''",
            "CREATE INDEX IF NOT EXISTS idx_toko_transaksi_outlet_date ON toko_transaksi(outlet_id, c_date)",
            "CREATE INDEX IF NOT EXISTS idx_toko_order_outlet_status_date ON order_header(outlet_id, status, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_toko_pembayaran_outlet_date ON toko_pembayaran(outlet_id, c_date)",
            "CREATE INDEX IF NOT EXISTS idx_toko_outbox_outlet_created_sent ON sync_outbox(outlet_id, created_at, sent_at)",
            "CREATE INDEX IF NOT EXISTS idx_stock_balance_outlet_qty ON stock_item_balance(outlet_id, qty_on_hand)",
            "CREATE INDEX IF NOT EXISTS idx_stock_ledger_outlet_item_date ON stock_ledger(outlet_id, item_id, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_stock_threshold_outlet_item ON stock_threshold(outlet_id, item_id)",
            "CREATE INDEX IF NOT EXISTS idx_cash_session_outlet_status_opened ON cash_session(outlet_id, status, opened_at)",
            "CREATE INDEX IF NOT EXISTS idx_cash_movement_session_type_date ON cash_movement(session_id, movement_type, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_cash_movement_outlet_date ON cash_movement(outlet_id, created_at)",
        )
        statements.forEach { sql ->
            runCatching {
                driver.execute(null, sql, 0, null)
            }
        }
    }
}
