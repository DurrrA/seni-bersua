# SuCash Architecture Diagrams

## Class Diagram (English, with Actual Class Mapping)

```mermaid
classDiagram
    class DatabaseProvider {
      +createDatabase(context) TokoDatabase
    }

    class TokoDatabase

    class MenuRepository {
      +getGroups() List~MenuGroup~
      +upsertGroup(group)
      +getItems() List~MenuItem~
      +upsertItem(item)
      +deleteItem(itemId)
    }

    class TransactionRepository {
      <<TransaksiRepository>>
      +createDraftTransaction(tableTokenOrLabel) Transaction
      +addLine(transactionId, itemId, qty, price, note) TransactionLine
      +checkoutCash(transactionId, paidAmount, paymentTypeId) TotalsResult
    }

    class OrderCacheRepository {
      +upsertOrder(orderHeader, orderLines)
      +getOrdersByStatus(status) List~Order~
    }

    class RecapRepository {
      +getDailyRecap(date) DailyRecap
    }

    class SyncRepository {
      +enqueue(event)
      +getPending() List~OutboxEvent~
      +markSent(id, sentAt)
    }

    class TotalsCalculator {
      +calculate(lines, discountPlus, tax, serviceCharge, rounding, paid) TotalsResult
    }

    class MenuGroup {
      <<GroupItem>>
      +id: String
      +name: String
      +order: Int
    }

    class MenuItem {
      <<Item>>
      +id: String
      +name: String
      +price: Long
      +groupId: String?
      +code: String?
      +isActive: Boolean
    }

    class Customer {
      <<Pelanggan>>
      +id: String
      +name: String?
      +barcode: String?
      +points: Long
      +discount: Long
    }

    class Transaction {
      <<Transaksi>>
      +id: String
      +createdAt: String
      +tableRef: String?
      +discountPlus: Long
      +tax: Long
      +serviceCharge: Long
      +rounding: Long
      +total: Long
    }

    class TransactionLine {
      <<TransaksiDetail>>
      +id: String
      +transactionId: String
      +itemId: String?
      +itemName: String
      +qty: Long
      +price: Long
      +discount: Long
      +total: Long
    }

    class Payment {
      <<Pembayaran>>
      +id: String
      +transactionId: String
      +paidAt: String
      +amountPaid: Long
      +change: Long
      +paymentTypeId: String
    }

    class OrderStatus

    class OrderHeader {
      +id: String
      +token: String?
      +status: OrderStatus
      +notes: String?
      +createdAt: String
      +updatedAt: String?
    }

    class OrderLine {
      <<OrderItem>>
      +id: String
      +orderId: String
      +itemId: String?
      +itemName: String
      +qty: Long
      +price: Long
      +note: String?
    }

    class Order {
      <<OrderWithItems>>
      +header: OrderHeader
      +items: List~OrderLine~
    }

    class OutboxEvent
    class DailyRecap

    DatabaseProvider --> TokoDatabase : creates
    MenuRepository --> TokoDatabase : uses
    TransactionRepository --> TokoDatabase : uses
    OrderCacheRepository --> TokoDatabase : uses
    RecapRepository --> TokoDatabase : uses
    SyncRepository --> TokoDatabase : uses
    TransactionRepository --> TotalsCalculator : calculates totals

    Order *-- OrderHeader
    Order *-- OrderLine
    OrderHeader "1" o-- "*" OrderLine : contains
    OrderLine --> MenuItem : optional itemId link

    Transaction "1" o-- "*" TransactionLine : contains
    Transaction "1" o-- "*" Payment : has
    MenuGroup "1" o-- "*" MenuItem : groups
```

## ERD (Unified Sale View)

```mermaid
erDiagram
    MENU_GROUP {
      TEXT group_id PK
      TEXT name
      INTEGER sort_order
    }

    MENU_ITEM {
      TEXT item_id PK
      TEXT group_id FK
      TEXT name
      TEXT price
      TEXT code
      TEXT item_type
      TEXT is_deleted
    }

    CUSTOMER {
      TEXT customer_id PK
      TEXT name
      TEXT barcode
      TEXT points
    }

    PAYMENT_METHOD_GROUP {
      TEXT payment_group_id PK
      TEXT name
    }

    PAYMENT_METHOD {
      TEXT payment_method_id PK
      TEXT payment_group_id FK
      TEXT name
    }

    SALE {
      TEXT sale_id PK
      TEXT customer_id FK
      TEXT created_by_user_id FK
      TEXT source_channel
      TEXT status
      TEXT token
      TEXT opened_at
      TEXT closed_at
      TEXT table_ref
      TEXT discount_plus
      TEXT tax
      TEXT service_charge
      TEXT rounding
      TEXT total
      TEXT paid
    }

    SALE_LINE {
      TEXT sale_line_id PK
      TEXT sale_id FK
      TEXT item_id
      TEXT item_name
      TEXT qty
      TEXT price
      TEXT discount
      TEXT line_total
    }

    PAYMENT {
      TEXT payment_id PK
      TEXT sale_id FK
      TEXT payment_method_id FK
      TEXT paid_amount
      TEXT change_amount
      TEXT paid_at
    }

    OUTBOX_EVENT {
      TEXT event_id PK
      TEXT event_type
      TEXT payload_json
      TEXT created_at
      TEXT sent_at
    }

    MENU_GROUP ||--o{ MENU_ITEM : groups
    CUSTOMER ||--o{ SALE : places
    PAYMENT_METHOD_GROUP ||--o{ PAYMENT_METHOD : contains
    SALE ||--o{ SALE_LINE : has_lines
    SALE ||--o{ PAYMENT : has_payments
    PAYMENT_METHOD ||--o{ PAYMENT : used_by
    MENU_ITEM ||--o{ SALE_LINE : item_ref_optional
```

## IRD (Information Relationship Diagram View)

### Entities and Keys

| Entity | Primary Key | Foreign Keys |
|---|---|---|
| `MENU_GROUP` | `group_id` | - |
| `MENU_ITEM` | `item_id` | `group_id -> MENU_GROUP.group_id` |
| `CUSTOMER` | `customer_id` | - |
| `PAYMENT_METHOD_GROUP` | `payment_group_id` | - |
| `PAYMENT_METHOD` | `payment_method_id` | `payment_group_id -> PAYMENT_METHOD_GROUP.payment_group_id` |
| `SALE` | `sale_id` | `customer_id -> CUSTOMER.customer_id` |
| `SALE_LINE` | `sale_line_id` | `sale_id -> SALE.sale_id`, `item_id -> MENU_ITEM.item_id` (logical/optional) |
| `PAYMENT` | `payment_id` | `sale_id -> SALE.sale_id`, `payment_method_id -> PAYMENT_METHOD.payment_method_id` |
| `OUTBOX_EVENT` | `event_id` | - |

### Relationship Matrix

| Parent | Child | Cardinality | Meaning |
|---|---|---|---|
| `MENU_GROUP` | `MENU_ITEM` | `1:N` | One group has many menu items |
| `CUSTOMER` | `SALE` | `1:N` | One customer can have many sales |
| `PAYMENT_METHOD_GROUP` | `PAYMENT_METHOD` | `1:N` | One method group contains many methods |
| `SALE` | `SALE_LINE` | `1:N` | One sale has many sale lines |
| `SALE` | `PAYMENT` | `1:N` | One sale can have multiple payments |
| `PAYMENT_METHOD` | `PAYMENT` | `1:N` | One method is used by many payments |
| `MENU_ITEM` | `SALE_LINE` | `1:N` (optional link) | One item can appear in many sale lines |

### IRD Notes

- `SALE` is a unified conceptual entity for the full lifecycle (order + checkout).
- `OUTBOX_EVENT` is standalone for async sync and idempotent push workflows.
- `SALE_LINE.item_id` remains optional to preserve historical rows even if item records change or are deleted.

## Physical Table Mapping (Actual SQLDelight Tables)

- `MENU_GROUP` -> `toko_group_item`
- `MENU_ITEM` -> `toko_item`
- `CUSTOMER` -> `toko_pelanggan`
- `PAYMENT_METHOD_GROUP` -> `toko_group_bayar`
- `PAYMENT_METHOD` -> `toko_jenis_bayar`
- `SALE` -> `toko_transaksi` + `order_header` (unified conceptual view)
- `SALE_LINE` -> `toko_transaksi_detail` + `order_item` (unified conceptual view)
- `PAYMENT` -> `toko_pembayaran`
- `OUTBOX_EVENT` -> `sync_outbox`

## Important Clarifications

- This ERD is intentionally simplified as one sales lifecycle (`SALE` -> `SALE_LINE` -> `PAYMENT`).
- In physical storage today, order and transaction are still separate tables.
- `item_id` on order lines is a logical link and is not enforced as a foreign key in the current schema.
- `pay-at-table` is not implemented yet; token/order fields are kept for future compatibility.
