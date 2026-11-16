# Spec: Stock ledger, procurement, receiving and supplier portal

Status: approved · Owner: platform · Last updated: 2026-11-12

## 1. Goal

Today stock only changes through checkout (reserve, confirm, return). There is no way to
put stock *in*. This spec adds the full replenishment loop:

```text
Admin            creates purchase order (PO)  ──►  Supplier: accepts, sends shipment notice
                                                         │ ships goods
                                                         ▼
Warehouse        receives & counts against PO ──►  inventory +good units (ledger entry)
```

Rule that drives the whole design: **stock only goes up when the warehouse counts goods in.**
A supplier's word ("shipped 50") never changes stock.

## 2. Non-goals

- Supplier invites or self-signup. One supplier and its login are seeded (see §4.3).
- Supplier invoicing, payment to suppliers, landed cost, currency other than USD.
- Multiple warehouses. `warehouse_location` stays a free-text bin label.
- Outbound shipping of customer orders (pick/pack/ship). Separate spec later.
- Automatic reordering. Low stock only raises an alert (§9).

## 3. Roles

| Role | New? | Can |
| --- | --- | --- |
| `USER` | | Unchanged |
| `ADMIN` | | Everything below, plus manage suppliers and POs |
| `WAREHOUSE` | yes | View inventory, make stock adjustments, receive POs, view POs and shipment notices |
| `SUPPLIER` | yes | View, accept or reject **its own** POs; send shipment notices. No stock access |
| `SERVICE` | | Unchanged (service-to-service tokens) |

Authority strings match the role name, no prefix (as today).

## 4. Phase 1: roles and seeded accounts (auth-service) — done

### 4.1 Data

- `user_data.role` is already `VARCHAR(255)`, so no enum change is needed. Validation of allowed
  role values in `AdminUserService` gains `WAREHOUSE` and `SUPPLIER`.
- Migration `V5__user_supplier_link.sql`: `ALTER TABLE user_data ADD COLUMN supplier_id BIGINT NULL`.
- Invariant: `supplier_id` is non-null **iff** `role = 'SUPPLIER'`. Enforced in the service layer.

### 4.2 Token

- `TokenIssuer.issueUserToken` adds claim `supplier_id` (number) when the user is a supplier.
- `CurrentUser` (auth, procurement) exposes `Optional<Long> supplierId()`.

### 4.3 Bootstrap

Generalise `AdminUserBootstrap` into `BootstrapUsers`, driven by properties:

```properties
bootstrap.users.admin.enabled=${ADMIN_BOOTSTRAP_ENABLED:false}
bootstrap.users.admin.username=${ADMIN_BOOTSTRAP_USERNAME:admin}
bootstrap.users.admin.password=${ADMIN_BOOTSTRAP_PASSWORD:}
bootstrap.users.admin.role=ADMIN

bootstrap.users.warehouse.enabled=${WAREHOUSE_BOOTSTRAP_ENABLED:false}
bootstrap.users.warehouse.username=${WAREHOUSE_BOOTSTRAP_USERNAME:warehouse}
bootstrap.users.warehouse.password=${WAREHOUSE_BOOTSTRAP_PASSWORD:}
bootstrap.users.warehouse.role=WAREHOUSE

bootstrap.users.supplier.enabled=${SUPPLIER_BOOTSTRAP_ENABLED:false}
bootstrap.users.supplier.username=${SUPPLIER_BOOTSTRAP_USERNAME:supplier}
bootstrap.users.supplier.password=${SUPPLIER_BOOTSTRAP_PASSWORD:}
bootstrap.users.supplier.role=SUPPLIER
bootstrap.users.supplier.supplier-id=${SUPPLIER_BOOTSTRAP_SUPPLIER_ID:1}
```

- Same behaviour as today's admin bootstrap: create or reset on every start; password required when enabled.
- `values-local.yaml` and `.env.local` enable all three (`warehousepass`, `supplierpass`).
  `values-cloud.yaml` disables all three.
- Supplier id `1` matches the supplier seeded by procurement-service (§6.4). auth does not
  call procurement to check it; the id is trusted config.

### 4.4 Admin user management

- Role dropdown on the Users page gains Warehouse and Supplier.
- Setting role `SUPPLIER` requires a `supplierId`; changing away from it clears the link.
- A role change revokes the user's refresh tokens, so the new role applies at next sign-in.
- `UserSummaryResponse` gains `supplierId`.

### 4.5 Frontend

- `Role` type gains `WAREHOUSE | SUPPLIER`.
- Sidebar and router are driven by a single role → routes map:
  - WAREHOUSE: Inventory, Receiving, Purchase orders (read-only)
  - SUPPLIER: My purchase orders
  - Landing page after login per role.

### 4.6 Tests

Token carries `supplier_id` for suppliers only; bootstrap creates all three; role validation.

## 5. Phase 2: stock ledger (inventory-service) — done

### 5.1 Data — `V4__stock_adjustments.sql`

```sql
CREATE TABLE stock_adjustments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    product_id       BIGINT       NOT NULL,
    quantity_delta   INTEGER      NOT NULL,            -- + adds, - removes
    reason           ENUM('RECEIVED','DAMAGED','COUNT_CORRECTION','LOST','INITIAL') NOT NULL,
    note             VARCHAR(500),
    reference_type   VARCHAR(40),                      -- e.g. 'PO_RECEIPT'
    reference_id     VARCHAR(100),                     -- e.g. receipt id
    created_by       VARCHAR(100) NOT NULL,            -- username or service client id
    created_at       DATETIME(6)  NOT NULL,
    available_after  INTEGER      NOT NULL,            -- snapshot for audit
    PRIMARY KEY (id),
    KEY idx_stock_adjustments_product_created (product_id, created_at),
    CONSTRAINT uk_stock_adjustments_reference UNIQUE (reference_type, reference_id, product_id)
) ENGINE=InnoDB;
```

The unique reference key makes event-driven receipts idempotent (§7.3).

### 5.2 Rules

- Every change to `available_quantity` outside checkout goes through `StockLedgerService.adjust`,
  which updates `inventory_items` and inserts the ledger row in one transaction, under the same
  pessimistic row lock checkout's reservations take.
- `available_quantity` may never go below 0. A negative delta larger than available → `409`.
- If the product has no `inventory_items` row, a positive adjustment creates it
  (removes the "admin must create stock record first" step). Negative → `404`.
- `RECEIVED` and `INITIAL` must be positive; `DAMAGED` and `LOST` negative;
  `COUNT_CORRECTION` either sign; delta `0` is rejected.
- Note required for `COUNT_CORRECTION`, `DAMAGED`, `LOST`.

### 5.3 API

| Method | Path | Who | Body / result |
| --- | --- | --- | --- |
| `POST` | `/inventoryapi/products/{productId}/adjustments` | WAREHOUSE, ADMIN | `{quantityDelta, reason, note}` → item + adjustment |
| `GET` | `/inventoryapi/products/{productId}/adjustments?page&size` | WAREHOUSE, ADMIN | Newest first |
| `GET` | `/inventoryapi/products` | public | Unchanged |
| `PATCH` | `/inventoryapi/products/{productId}` | WAREHOUSE, ADMIN | `{warehouseLocation, reorderLevel}` |

- `RECEIVED` via this endpoint is rejected: receipts come only from procurement (§7), so every
  received unit traces to a PO.
- Migration also adds `inventory_items.reorder_level INT NOT NULL DEFAULT 0` (used in §9).

### 5.4 Retire legacy endpoints

Delete `POST /products/{id}/reserve|release|deduct` and `POST /products` (stock creation is
implicit in §5.2). Checkout uses the `/reservations` API only. Remove from SecurityConfig, the
frontend client and docs. Seed script switches to `INITIAL` adjustments.

### 5.5 Frontend

- Inventory page: visible to WAREHOUSE; per-row **Adjust** (dialog: reason, ± quantity, note)
  and **History** drawer (ledger rows).
- Low-stock badge when `available ≤ reorderLevel`.

### 5.6 Tests

Ledger + quantity in one transaction; negative floor; reason/sign rules; implicit create;
duplicate reference ignored; WAREHOUSE allowed, USER and SUPPLIER `403`.

## 6. Phase 3: procurement-service (new) — done

Port `8088`, database `procurement`, gateway prefix `/procurementapi/**`.
Scaffold copied from payment-service (security, correlation id, tracing, outbox, Flyway, Helm).

### 6.1 Data — `V1__baseline.sql`

```text
suppliers(id, name, contact_email, active, created_at)
supplier_products(supplier_id, product_id, supplier_sku, unit_cost)       PK(supplier_id, product_id)

purchase_orders(id, po_number UNIQUE, supplier_id, status, expected_date,
                notes, created_by, created_at, updated_at, sent_at,
                accepted_at, cancelled_at, version)
purchase_order_lines(id, po_id, product_id, quantity_ordered, unit_cost,
                     quantity_received, quantity_damaged)

shipment_notices(id, po_id, carrier, tracking_number, expected_arrival,
                 created_by, created_at)
shipment_notice_lines(id, notice_id, po_line_id, quantity_shipped)

receipts(id, receipt_key UNIQUE, po_id, received_by, received_at, note)
receipt_lines(id, receipt_id, po_line_id, quantity_good, quantity_damaged)

outbox_events(...)   -- same shape as order/payment
```

`po_number` format `PO-000123`. Money is `DECIMAL(12,2)`.

As built, beyond the list above: `purchase_order_lines.quantity_shipped` (running total from
notices, capped at ordered), `purchase_orders.status_note` (reject / cancel / close reason),
`rejected_at`, `closed_at`, and a unique `(po_id, product_id)` per PO. PO responses carry an
`actions` list (what the caller can do next, by role and status). `GET
/suppliers/{id}/products` reads a catalogue (ADMIN, WAREHOUSE; costs for admins only).

### 6.2 PO lifecycle

```text
DRAFT ──send──► SENT ──accept──► ACCEPTED ──ship notice──► SHIPPED
  │               │  └─reject──► REJECTED                     │
  └──cancel──►  CANCELLED ◄──cancel (admin, before any receipt)┘
                                                              │
                  receipt (some lines short) ──► PARTIALLY_RECEIVED ──receipt──► RECEIVED
```

- Only `DRAFT` is editable (lines, supplier, dates).
- Receiving allowed from `ACCEPTED`, `SHIPPED`, `PARTIALLY_RECEIVED` (goods can arrive without a notice).
- `RECEIVED` when every line has `received + damaged ≥ ordered`. Admin can **Close short**
  (`PARTIALLY_RECEIVED → RECEIVED`) with a note.
- Over-receipt allowed up to the ordered quantity per line; more → `409`.
- Every transition is checked in one place (`PurchaseOrderStateMachine`) and unit-tested.

### 6.3 API

| Method | Path | Who |
| --- | --- | --- |
| `GET/POST` | `/procurementapi/suppliers` | ADMIN (GET also WAREHOUSE) |
| `PUT` | `/procurementapi/suppliers/{id}` | ADMIN |
| `PUT` | `/procurementapi/suppliers/{id}/products` | ADMIN — catalogue + unit costs |
| `GET` | `/procurementapi/purchase-orders?status&supplierId&page` | ADMIN, WAREHOUSE; SUPPLIER forced to own |
| `POST` | `/procurementapi/purchase-orders` | ADMIN — creates DRAFT |
| `GET/PUT` | `/procurementapi/purchase-orders/{id}` | GET: ADMIN, WAREHOUSE, own SUPPLIER; PUT: ADMIN, DRAFT only |
| `POST` | `/procurementapi/purchase-orders/{id}/send` | ADMIN |
| `POST` | `/procurementapi/purchase-orders/{id}/cancel` | ADMIN |
| `POST` | `/procurementapi/purchase-orders/{id}/close` | ADMIN |
| `POST` | `/procurementapi/purchase-orders/{id}/accept` | own SUPPLIER — `{expectedDate}` |
| `POST` | `/procurementapi/purchase-orders/{id}/reject` | own SUPPLIER — `{reason}` |
| `POST` | `/procurementapi/purchase-orders/{id}/shipments` | own SUPPLIER |
| `POST` | `/procurementapi/purchase-orders/{id}/receipts` | WAREHOUSE, ADMIN — `Idempotency-Key` header |

"Own SUPPLIER" = token `supplier_id` equals `po.supplier_id`; otherwise `404` (not `403`, so
suppliers can't probe for other suppliers' POs). Supplier responses omit `created_by` and
other suppliers' data.

### 6.4 Seed

Flyway `V2__seed_default_supplier.sql`: supplier `1` "Mobi Distribution",
`orders@mobi-distribution.test`. Its product catalogue is filled by `seed-phones.sh`
(all products, unit cost = 60 % of price).

### 6.5 Platform

New module wired the same way as payment-service:

- `pom.xml`, Dockerfile, `kind-load-images.sh`;
- Helm values entry, MySQL database + user, gateway route and rate-limit key;
- resource-server properties, BACKEND.md section, architecture diagram.

## 7. Phase 4: receiving — done

### 7.1 Flow

1. Warehouse opens a PO and enters, per line, `good` and `damaged` counts.
2. `POST /receipts` (with `Idempotency-Key`) in one transaction:
   - inserts the receipt and its lines;
   - updates the PO line totals and status;
   - writes outbox event `GOODS_RECEIVED`.
3. The outbox publishes to topic `procurement-events`.
4. inventory-service consumes it. For each line with `good > 0`, it calls
   `adjust(+good, RECEIVED, reference=PO_RECEIPT:{receiptId})`.
   Damaged units are recorded on the PO only; they never entered stock.

### 7.2 Event

```json
{
  "eventId": "uuid", "type": "GOODS_RECEIVED", "occurredAt": "...",
  "receiptId": 42, "poId": 7, "poNumber": "PO-000007", "receivedBy": "warehouse",
  "lines": [{ "productId": 3, "quantityGood": 48, "quantityDamaged": 2 }]
}
```

### 7.3 Guarantees

- Exactly-once effect: outbox (at-least-once publish) + ledger unique key
  `(PO_RECEIPT, receiptId, productId)` → duplicates are skipped.
- Consumer uses the existing retry + `.DLT` setup.
- Stock appears within seconds, not instantly. The UI says "Stock will update shortly".

### 7.4 Frontend

**Receiving** page (WAREHOUSE, ADMIN):

- Queue of receivable POs, with expected arrival taken from the latest shipment notice.
- **Receive** screen: one row per line showing ordered, already received, shipped (from notice),
  and inputs for good and damaged. Defaults to the outstanding quantity; submit confirms totals.

## 8. Phase 5: supplier portal — done

- **My purchase orders** page (SUPPLIER): list of own POs excluding `DRAFT`, detail view.
- Actions:
  - `SENT`: **Accept** (pick expected date) or **Reject** (reason).
  - `ACCEPTED` / `PARTIALLY_RECEIVED`: **Send shipment**, entering carrier, tracking, expected
    arrival and quantity per line (≤ outstanding). Multiple notices allowed.
  - Status moves to `SHIPPED` on the first notice.
- Shows received and damaged counts per line, so the supplier sees shortfalls.
- No inventory endpoints are reachable with a SUPPLIER token (`403`).

## 9. Phase 6: notifications — done

`procurement-events` types: `PO_SENT`, `PO_ACCEPTED`, `PO_REJECTED`, `SHIPMENT_NOTICE_CREATED`,
`GOODS_RECEIVED`. inventory publishes `LOW_STOCK` (on `inventory-events`) when an adjustment
or confirmed sale takes `available` from above `reorderLevel` to at or below it.

| Event | Email to |
| --- | --- |
| `PO_SENT` | supplier `contact_email` (PO summary) |
| `PO_ACCEPTED` / `PO_REJECTED` | admins |
| `SHIPMENT_NOTICE_CREATED` | warehouse users |
| `LOW_STOCK` | admins |

Recipients by role come from notification-service's existing user projection (needs `role` in
`user-events` if not already present). Locally it all lands in Mailpit.

## 10. Phase 7: verification and docs — done

- `smoke.sh` procurement section (skippable with `SMOKE_SKIP_PROCUREMENT=1`):
  1. admin creates PO for 50 × product, sends it;
  2. supplier sees it, another supplier id can't (`404`), accepts it, sends a notice for 50;
  3. warehouse receives 48 good + 2 damaged → PO `PARTIALLY_RECEIVED`;
  4. stock +48 within 10 s (poll), ledger row present;
  5. replaying the same `Idempotency-Key` doesn't double stock;
  6. admin closes short → `RECEIVED`;
  7. supplier token on `/inventoryapi/**` → `403`; customer on `/procurementapi/**` → `403`;
  8. customer buys the product successfully.
- Docs:
  - README roles table;
  - BACKEND.md procurement section;
  - LOCAL_SETUP.md demo accounts;
  - architecture diagram with procurement-service and `procurement-events`.

### As built (phases 4–7)

- The event's actor field is `actor` (the warehouse user), not `receivedBy`, and every event
  also carries `supplierName` and `supplierEmail` so notification-service needs no lookup.
- §10 step 3 said "48 good + 2 damaged → `PARTIALLY_RECEIVED`", which contradicts §6.2
  (received + damaged ≥ ordered is `RECEIVED`). The smoke test counts 40 + 2 instead.
- `LOW_STOCK` on a sale fires at confirmation, when the confirmed quantity is what took the
  product across its reorder level (available had already dropped at reservation).
- Bootstrap accounts get emails (`*_BOOTSTRAP_EMAIL`) and are republished as
  `USER_SNAPSHOT` on every start, so staff notifications have recipients.
- The supplier portal reuses the PO list and detail pages; buttons come from the
  response's `actions`.

## 11. Delivery order and size

| Phase | Size | Depends on |
| --- | --- | --- |
| 1 Roles + seeded accounts | S | — |
| 2 Stock ledger | S–M | 1 |
| 3 procurement-service | L | 1 |
| 4 Receiving | M | 2, 3 |
| 5 Supplier portal | M | 3 |
| 6 Notifications | S | 3, 4 |
| 7 Smoke + docs | S | all |

Phases 2 and 3 can be built in parallel after 1.

## 12. Decisions

1. **WAREHOUSE does not see unit costs.** PO responses to WAREHOUSE tokens omit `unitCost`
   and totals; receiving needs quantities only, and cost data stays need-to-know.
2. **SUPPLIER does not see stock levels.** Inventory is internal; suppliers learn demand from POs.
3. **No cancel after any receipt.** Once goods are in, the PO is a record of them; ending a PO
   early is **Close short** (with a note), which keeps the receipt history intact.
