# SuCash KMP

Kotlin Multiplatform project with 3 modules:

- `composeApp`: Android POS app (Compose Multiplatform)
- `shared`: shared SQLDelight DB, domain models, repositories, DTOs
- `server`: Ktor backend

## Android App Screens

- `Orders`: local cached incoming orders with status workflow `Accept -> Cooking -> Served -> Done`
- `New Order`: walk-in order builder (optional table token, menu pick, cart)
- `Checkout`: cash checkout for the current draft order
- `Menu`: group management, item CRUD, search/filter, temporary bundles (placeholder)
- `Recap`: today/week/month filters, metrics, chart, payment-method breakdown, top/slow movers
- `Settings`: receipt settings (store info, logo URIs, footer), sync, and shortcuts
- `Cash Flow` (subpage): totals by method and recent entries
- `Stock` (subpage): low-stock alerts, manual adjustment, threshold, movement history
- `Cash Closing` (subpage): open shift, cash in/out, close shift with expected-vs-counted variance
- `Receipt Preview` (route): `receiptPreview/{transaksiId}`

## Build and Run

Prerequisites:

- JDK 17 configured (`JAVA_HOME` + `java` in PATH)
- Android SDK installed and configured in Android Studio

Build commands:

```bash
./gradlew clean
./gradlew :shared:compileKotlinAndroid
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :server:build
```

Project `.env`:

1. Root `.env` is now supported automatically by server runtime.
2. Template file: `.env.example`
3. Copy and edit locally:

```bash
cp .env.example .env
```

Main keys:

- `SUCASH_SERVER_HOST` (default `0.0.0.0`)
- `SUCASH_SERVER_PORT` (default `8080`)
- `SUCASH_SERVER_DB_PATH` (default `data/sucash-server.db`)
- `TURSO_DATABASE_URL` (prepared for Turso integration)
- `TURSO_AUTH_TOKEN` (prepared for Turso integration, keep secret)

Run server:

```bash
./gradlew :server:run
```

Optional server DB path (defaults to `data/sucash-server.db`):

```bash
export SUCASH_SERVER_DB_PATH=/absolute/path/to/sucash-server.db
./gradlew :server:run
```

Server config precedence:

1. Environment variable (shell/CI)
2. Root `.env`
3. Hardcoded default

## Web Flow (Server)

After `:server:run`, open:

- `http://localhost:8080/` for company/profile + seeded customer UUID links
- `http://localhost:8080/dashboard` for cashier order monitor + accept/preparing/served actions + recap summary filters (Today/Week/Month)
- `http://localhost:8080/t/{customerUuid}` for customer checkout page
- `http://localhost:8080/scan/{customerUuid}` as barcode-scan redirect target
- subdomain-style host is supported as redirect to table route (example host header: `{customerUuid}.localhost` -> `/t/{customerUuid}`)

API endpoints used by the React web UI:

- `GET /api/menu?outlet=...`
- `POST /api/menu/upsert`
- `POST /api/menu/{id}/delete?outlet=...`
- `GET /api/customers`
- `GET /api/customers/{uuid}`
- `GET /api/orders?status=NEW,ACCEPTED,PREPARING,SERVED&outlet=...`
- `POST /api/orders`
- `POST /api/orders/{id}/status`
- `POST /api/sync/transactions/batch`
- `GET /api/recap/daily?date=YYYY-MM-DD&outlet=...`
- `GET /api/recap/summary?range=TODAY|WEEK|MONTH&date=YYYY-MM-DD&outlet=...`

## Mobile <-> Server

From Android app:

1. Open `Settings`
2. Set `Server Base URL`
   - emulator: `http://10.0.2.2:8080`
   - physical device: `http://<your-pc-lan-ip>:8080`
3. Save settings
4. Set `Outlet ID` (recommended; defaults to `default` if left blank)
5. Open `Orders` screen and tap `Pull Orders`

Status updates in mobile (`Accept`, `Prepare`, `Serve`) are sent to server and reflected in web dashboard.
`New Order` also auto-pulls latest menu from server on open, and has a `Sync Menu` button for manual refresh.

Menu sync:

1. Open `Menu` screen
2. App auto-pulls menu from server on first open
3. Local item save/delete auto-pushes to server (if network/server available)
4. You can still use `Pull Menu` / `Push Menu` buttons for manual retry

Transaction sync:

1. Open `Orders` -> `New Walk-in Order` -> `Checkout`
2. App enqueues transaction to local outbox
3. App immediately tries to flush pending outbox to `POST /api/sync/transactions/batch`
4. `GET /api/recap/daily` is updated for dashboard recap

Stock engine (Sprint 2 foundation):

1. Checkout now auto-decrements local stock for each sold item (`SALE` movement).
2. Local stock tables in shared DB:
   - `stock_item_balance`
   - `stock_ledger`
   - `stock_threshold`
3. Server sync ingestion now mirrors stock decrement and writes server stock ledger for new synced transactions.
4. Low-stock and stock-history read hooks are available in shared `StockRepository`.
5. Current default policy allows negative stock; setting key prepared: `allow_negative_stock`.

Cash closing flow:

1. Shift lifecycle tables in shared DB:
   - `cash_session`
   - `cash_movement`
2. Mobile flow:
   - open shift with opening cash
   - record cash in/out
   - close shift with counted cash
3. App computes expected cash and variance at close.

Website order confirmation:

1. On `/t/{customerUuid}`, customer can pick payment confirmation:
   - `Leave Blank`
   - `Pay at Cashier`
2. Value is stored on server and shown in dashboard/mobile order detail notes.

Outlet scoping:

1. Sync/menu/order/recap APIs are now outlet-aware.
2. Mobile uses `Settings -> Outlet ID`; if empty, app falls back to `default`.
3. For web, pass outlet in query string (example: `/dashboard?outlet=main`) and API calls should include `outlet`.

Manual full sync:

1. Open `Settings`
2. Save `Server Base URL` first
3. Use `Manual Sync` section:
   - `Sync All Now`
   - or individual `Pull Orders`, `Pull Menu`, `Push Menu`, `Flush Transaksi`

## Notes

- QR scanner is Android (`QrScannerActivity`).
- Receipt export/share and printer integration are scaffolded with TODO hooks.
- Temporary bundles UI exists and is intentionally not applied to checkout pricing yet.
