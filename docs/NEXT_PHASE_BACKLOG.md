# SuCash Next-Phase Backlog (Execution-Ready)

This backlog covers:
- Stock engine: auto decrement, low-stock alert, stock adjustment history
- Cash closing flow: open cash, cash in/out, close shift with variance
- Reporting upgrades: daily/weekly/monthly filters, payment breakdown, top/slow movers
- Final receipt pipeline: print/export/share completed
- Multi-outlet readiness: outlet scoping across menu/order/recap/sync

## Execution Order (Do Not Swap)
1. Multi-outlet readiness (foundation)
2. Stock engine
3. Cash closing flow
4. Reporting upgrades
5. Final receipt pipeline

---

## Phase 1: Multi-Outlet Readiness (Foundation)

### Objective
Make every business object outlet-aware to avoid future data mixing.

### Database Changes

#### Shared SQLDelight (`shared/src/commonMain/sqldelight/com/durrr/first/toko.sq`)
- Add `outlet_id TEXT` to:
  - `toko_item`
  - `toko_group_item`
  - `toko_transaksi`
  - `toko_transaksi_detail` (optional denormalized, recommended for reporting speed)
  - `toko_pembayaran`
  - `order_header`
  - `sync_outbox`
  - `app_settings` (store default selected outlet if needed)
- Add indexes:
  - `idx_transaksi_outlet_date` on `toko_transaksi(outlet_id, c_date)`
  - `idx_order_outlet_status` on `order_header(outlet_id, status, created_at)`
  - `idx_pembayaran_outlet_date` on `toko_pembayaran(outlet_id, c_date)`
  - `idx_outbox_outlet_created` on `sync_outbox(outlet_id, created_at, sent_at)`

#### Server DB (`server/src/main/kotlin/com/durrr/first/ServerDatabase.kt`)
- Add `outlet_id TEXT NOT NULL` to:
  - `menu_item`
  - `order_header`
  - `transaksi_header`
  - `transaksi_detail` (optional denormalized)
  - `pembayaran`
  - `processed_event` (or introduce `(outlet_id, event_id)` uniqueness)
- Add indexes:
  - `(outlet_id, created_at)` for recap/report
  - `(outlet_id, status, created_at)` for order queue
  - `(outlet_id, event_id)` unique for idempotency

### API Contract Changes
- Require `outletId` in:
  - `GET /api/menu`
  - `POST /api/menu/upsert`
  - `POST /api/menu/{id}/delete`
  - `GET /api/orders`
  - `POST /api/orders`
  - `POST /api/orders/{id}/status`
  - `POST /api/sync/transactions/batch`
  - `GET /api/recap/daily`
- Return `outletId` in relevant response DTOs.

### Shared/Client Changes
- Add `outletId` field to shared DTOs in `shared/network/dto`.
- Update `ServerApiClient` to always include `outletId`.
- Update repositories (`MenuSyncRepository`, `OrderSyncRepository`, `TransaksiSyncRepository`) to scope all pulls/pushes by outlet.
- `SettingsRepository`: make outlet required before network sync.

### UI Changes (Compose + Web)
- Settings:
  - enforce non-empty `Outlet ID` before enabling sync actions.
- Orders/Menu/Recap/CashFlow:
  - display active outlet indicator.
- Web dashboard/table ordering:
  - attach outlet ID via query or hidden context.

### Tests
- Server:
  - verify cross-outlet isolation (data from outlet A not visible in outlet B)
  - verify sync idempotency is outlet-aware
- Shared:
  - repository tests with mixed-outlet seed data

### Definition of Done
- No endpoint returns mixed-outlet data.
- All sync payloads and recap requests are outlet-scoped.

---

## Phase 2: Stock Engine

### Objective
Track and enforce stock movement from sales and manual adjustments.

### Database Changes

#### Shared SQLDelight
- Add tables:
  - `stock_item_balance`
    - `item_id TEXT PK`
    - `outlet_id TEXT NOT NULL`
    - `qty_on_hand INTEGER NOT NULL`
    - `updated_at TEXT NOT NULL`
  - `stock_ledger`
    - `ledger_id TEXT PK`
    - `outlet_id TEXT NOT NULL`
    - `item_id TEXT NOT NULL`
    - `movement_type TEXT NOT NULL` (`SALE`, `ADJUST_IN`, `ADJUST_OUT`, `RESTOCK`, `VOID_ROLLBACK`)
    - `qty_delta INTEGER NOT NULL` (negative for out)
    - `reference_type TEXT`
    - `reference_id TEXT`
    - `reason TEXT`
    - `created_by TEXT`
    - `created_at TEXT NOT NULL`
  - `stock_threshold`
    - `item_id TEXT PK`
    - `outlet_id TEXT NOT NULL`
    - `min_qty INTEGER NOT NULL DEFAULT 0`

### Repository Changes
- Add `StockRepository`:
  - `decrementBySale(transaksiId, outletId, lines)`
  - `adjustStock(itemId, outletId, qtyDelta, reason, user)`
  - `getLowStockItems(outletId)`
  - `getStockHistory(outletId, itemId?, range)`

### Business Rules
- During checkout success:
  - insert transaksi + pembayaran
  - apply stock decrement in same DB transaction
  - append stock ledger rows
- Reject checkout only if policy is `hard stock check` and insufficient stock.
  - For MVP: allow negative stock toggle configurable in settings.

### UI Changes
- Menu item form:
  - show current stock
  - set low-stock threshold
- New screen: `Stock`
  - low-stock alert list
  - stock adjustment action (in/out with reason)
  - stock movement history timeline

### Server Sync
- Include stock effects in transaction sync processing (`/api/sync/transactions/batch`) or separate stock sync event.
- Keep idempotent behavior for stock updates by event ID.

### Tests
- Checkout auto-decrement test.
- Adjustment audit trail test.
- Idempotent sync test (no double decrement on replay).
- Low-stock alert query test.

### Definition of Done
- Every quantity change has a ledger record.
- Replayed sync event does not decrement stock twice.

---

## Phase 3: Cash Closing Flow

### Objective
Formalize cashier shift lifecycle and variance tracking.

### Database Changes

#### Shared SQLDelight
- Add tables:
  - `cash_session`
    - `session_id TEXT PK`
    - `outlet_id TEXT NOT NULL`
    - `opened_by TEXT NOT NULL`
    - `opened_at TEXT NOT NULL`
    - `opening_cash INTEGER NOT NULL`
    - `closed_by TEXT`
    - `closed_at TEXT`
    - `closing_cash_counted INTEGER`
    - `expected_cash INTEGER`
    - `variance INTEGER`
    - `status TEXT NOT NULL` (`OPEN`, `CLOSED`)
  - `cash_movement`
    - `movement_id TEXT PK`
    - `session_id TEXT NOT NULL`
    - `outlet_id TEXT NOT NULL`
    - `movement_type TEXT NOT NULL` (`CASH_IN`, `CASH_OUT`)
    - `amount INTEGER NOT NULL`
    - `note TEXT`
    - `created_by TEXT`
    - `created_at TEXT NOT NULL`

### Repository Changes
- Add `CashSessionRepository`:
  - `openSession(outletId, openingCash, userId)`
  - `addCashIn(sessionId, amount, note)`
  - `addCashOut(sessionId, amount, note)`
  - `closeSession(sessionId, countedCash, userId)` -> computes expected + variance
  - `getActiveSession(outletId)`
  - `getSessionHistory(outletId, range)`

### Business Rules
- Only one `OPEN` session per outlet.
- Checkout cash should link to active session if exists.
- Closed session is immutable except admin correction flow (future).

### UI Changes
- New `Cash Closing` screen:
  - open shift card
  - cash in/out entry
  - close shift form (counted cash)
  - variance summary
- Settings/Recap quick links to active session status.

### Server API
- Add endpoints:
  - `POST /api/cash/session/open`
  - `POST /api/cash/session/{id}/movement`
  - `POST /api/cash/session/{id}/close`
  - `GET /api/cash/session/active?outlet=...`
  - `GET /api/cash/session/history?...`

### Tests
- single active session guard test
- close session variance calculation test
- cash movement aggregation test

### Definition of Done
- Shift can be opened and closed with deterministic expected/variance result.

---

## Phase 4: Reporting Upgrades

### Objective
Provide production-usable recap and drill-down reports.

### Report Scope
- Filters:
  - today / week / month / custom date range
  - outlet required
- Metrics:
  - gross sales, net sales, total transactions, average ticket
- Breakdowns:
  - payment method totals
  - top movers (qty/revenue)
  - slow movers (sold lowest but >0 in period)

### Backend/API
- Extend:
  - `GET /api/recap/daily` -> keep for compatibility
- Add:
  - `GET /api/reports/summary?from=...&to=...&outlet=...`
  - `GET /api/reports/payment-method?from=...&to=...&outlet=...`
  - `GET /api/reports/products/top?from=...&to=...&outlet=...&limit=...`
  - `GET /api/reports/products/slow?from=...&to=...&outlet=...&limit=...`

### Shared Repository
- Add `ReportRepository` (or extend `RecapRepository`) with structured responses.
- Prefer SQL aggregation, avoid loading all rows into memory.

### UI Changes
- Recap screen:
  - custom date picker
  - payment-method chart/list
  - top/slow movers sections
- Web dashboard:
  - add date range controls and outlet-aware widgets.

### Tests
- API tests for each report endpoint.
- SQL aggregation correctness tests (known fixtures).

### Definition of Done
- Same report results between mobile and web for identical outlet/date filters.

---

## Phase 5: Final Receipt Pipeline

### Objective
Complete real receipt output and sharing (no TODO placeholders).

### Required Output
- On-screen preview (already exists)
- PDF export
- PNG export (optional if PDF done first)
- Share intent integration
- Printer integration (Android)

### Implementation Tasks

#### Shared
- Add immutable `receipt_snapshot` model saved at checkout:
  - includes store metadata, items, totals, payment, outlet, timestamp
- Ensure receipt render data does not depend on mutable menu/settings later.

#### Android (`composeApp`)
- Implement:
  - `PrintManager` document adapter flow
  - PDF generation path (cache file)
  - share via `Intent.ACTION_SEND`
- Update receipt screen actions:
  - `Print`
  - `Export PDF`
  - `Share`

#### Settings
- Per-outlet receipt settings:
  - header logo URI
  - watermark URI
  - footer text
  - optional printer profile placeholder

### Tests
- snapshot generation test at checkout
- PDF generation smoke test
- action behavior tests (where possible)

### Definition of Done
- Cashier can print or share a final receipt from completed transaction.

---

## Cross-Cutting Technical Standards

### Error Handling
- Standard API error envelope:
  - `code`, `message`, `details`, `traceId`

### Observability
- Add server request logs with `traceId`.
- Add sync metrics counters:
  - pending, sent, failed, retry count.

### Security
- Keep `usesCleartextTraffic` debug-only.
- Add authenticated endpoints before production usage.

### Performance
- Add pagination for orders/history/report endpoints.
- Add SQL indexes for all filter/sort fields.

---

## Suggested Sprint Breakdown

### Sprint 1
- Phase 1 (Multi-outlet) complete + tests

### Sprint 2
- Phase 2 (Stock engine) complete + tests

### Sprint 3
- Phase 3 (Cash closing) complete + tests

### Sprint 4
- Phase 4 (Reporting) complete + tests

### Sprint 5
- Phase 5 (Receipt pipeline finalization) complete + tests

---

## Validation Commands per Sprint

```bash
./gradlew :shared:compileKotlinAndroid
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :server:test
./gradlew :server:run
```

