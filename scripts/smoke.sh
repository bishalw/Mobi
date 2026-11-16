#!/usr/bin/env bash
set -euo pipefail

# Staff credentials default to the ones deploy.sh gave the seeded accounts (.env.local),
# so a custom ADMIN_BOOTSTRAP_PASSWORD doesn't quietly turn checks into skips.
ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env.local}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
BASE="${SMOKE_BASE_URL:-http://localhost}"
ADMIN_USER="${SMOKE_ADMIN_USER:-${ADMIN_BOOTSTRAP_USERNAME:-admin}}"
ADMIN_PASS="${SMOKE_ADMIN_PASS:-${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}}"

pass() { echo "[PASS] $1"; }
fail() { echo "[FAIL] $1"; exit 1; }

check_status() {
  local label=$1 expected=$2 actual=$3
  if [[ "$actual" != "$expected" ]]; then
    fail "$label: expected HTTP $expected, got $actual"
  fi
  pass "$label (HTTP $expected)"
}

# extract_json <key> <json> — returns value or empty string, never exits non-zero
extract_json() {
  local key=$1 json=$2
  echo "$json" | grep -o "\"$key\":[^,}]*" | head -1 | sed 's/.*://;s/[" ]//g' || true
}

extract_token() {
  local json=$1
  echo "$json" | grep -o '"token":"[^"]*"' | cut -d'"' -f4 || true
}

# ── Register ──────────────────────────────────────────────────────────────────
UNIQUE="smoke_$(date +%s)"
SKU="SMOKE-$UNIQUE"
REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$UNIQUE\",\"password\":\"Password1!\"}")
check_status "register new user" "201" "$REG_STATUS"

# Duplicate register must be rejected
DUP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$UNIQUE\",\"password\":\"Password1!\"}")
[[ "$DUP_STATUS" != "200" ]] || fail "duplicate register should have been rejected, got HTTP 200"
pass "duplicate register rejected (HTTP $DUP_STATUS)"

# ── Login ─────────────────────────────────────────────────────────────────────
# The refresh token arrives as an httpOnly cookie; a cookie jar stands in for the browser.
COOKIES=$(mktemp)
trap 'rm -f "$COOKIES" "$COOKIES.old"' EXIT
LOGIN_RESP=$(curl -s -c "$COOKIES" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$UNIQUE\",\"password\":\"Password1!\"}")
TOKEN=$(extract_token "$LOGIN_RESP")
[[ -n "$TOKEN" ]] || fail "login did not return a token"
USER_ID=$(extract_json "userId" "$LOGIN_RESP")
[[ -n "$USER_ID" ]] || fail "login did not return a userId"
pass "login (got JWT for user $USER_ID)"

AUTH="Authorization: Bearer $TOKEN"

# ── Refresh-token rotation ────────────────────────────────────────────────────
grep -q mobi_refresh "$COOKIES" || fail "login did not set the refresh cookie"
cp "$COOKIES" "$COOKIES.old"
REFRESH_RESP=$(curl -s -b "$COOKIES" -c "$COOKIES" -X POST "$BASE/auth/refresh")
[[ -n "$(extract_token "$REFRESH_RESP")" ]] || fail "refresh did not return a new access token. body: $REFRESH_RESP"
pass "refresh issues a new access token"

# Presenting the already-rotated cookie again is treated as theft: rejected, and the
# whole session is revoked, so even the newest cookie stops working.
REUSE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIES.old" -X POST "$BASE/auth/refresh")
check_status "reused refresh token rejected" "401" "$REUSE_STATUS"
AFTER_REUSE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIES" -X POST "$BASE/auth/refresh")
check_status "session revoked after token reuse" "401" "$AFTER_REUSE_STATUS"

# ── Admin login (needed to seed product + inventory) ─────────────────────────
ADMIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
ADMIN_TOKEN=$(extract_token "$ADMIN_RESP")
if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "[WARN] Admin login failed — skipping seeding, using SMOKE_PRODUCT_ID=${SMOKE_PRODUCT_ID:-1}"
fi
ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"

# ── Seed: product + inventory ─────────────────────────────────────────────────
PRODUCT_ID="${SMOKE_PRODUCT_ID:-1}"

if [[ -n "$ADMIN_TOKEN" ]]; then
  PROD_RESP=$(curl -s -X POST "$BASE/productapi/products" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d "{\"sku\":\"$SKU\",\"name\":\"Smoke Widget\",\"description\":\"Smoke test product\",\"price\":9.99,\"active\":true}" || true)
  NEW_PROD=$(extract_json "id" "$PROD_RESP")
  [[ -n "$NEW_PROD" ]] && PRODUCT_ID="$NEW_PROD"

  # Opening stock goes through the ledger; the first adjustment creates the stock record.
  ADJ_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/inventoryapi/products/$PRODUCT_ID/adjustments" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d '{"quantityDelta":20,"reason":"INITIAL","note":"smoke seed"}' || true)
  [[ "$ADJ_CODE" == "201" ]] || fail "opening stock adjustment: expected 201, got $ADJ_CODE"
  pass "seeded product $PRODUCT_ID with 20 units (ledger)"
fi

# ── Place valid order ─────────────────────────────────────────────────────────
IDEM_KEY="smoke-order-$(date +%s)"
ORDER_RESP=$(curl -s -X POST "$BASE/orderapi/orders" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}")

ORDER_ID=$(extract_json "id" "$ORDER_RESP")
ORDER_STATUS_FIELD=$(extract_json "status" "$ORDER_RESP")

[[ -n "$ORDER_ID" ]] || fail "place order: no order id in response. body: $ORDER_RESP"
[[ "$ORDER_STATUS_FIELD" == "INVENTORY_RESERVED" ]] || \
  fail "place order: expected INVENTORY_RESERVED, got '$ORDER_STATUS_FIELD'. body: $ORDER_RESP"
pass "place valid order (id=$ORDER_ID, status=INVENTORY_RESERVED)"

# ── Idempotent retry — same key must return same order ───────────────────────
RETRY_RESP=$(curl -s -X POST "$BASE/orderapi/orders" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}")
RETRY_ID=$(extract_json "id" "$RETRY_RESP")
[[ "$RETRY_ID" == "$ORDER_ID" ]] || \
  fail "idempotent retry: got different order id ($RETRY_ID vs $ORDER_ID)"
pass "idempotent retry (same order returned)"

# ── Pay for order ─────────────────────────────────────────────────────────────
# Customers can no longer confirm without paying; that endpoint is admin-only now.
SELF_CONFIRM_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/confirm" \
  -H "$AUTH")
check_status "customer cannot confirm without paying" "403" "$SELF_CONFIRM_STATUS"

PAY_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/pay" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"paymentMethodToken":"tok_visa"}')
PAY_STATUS=$(echo "$PAY_RESP" | tail -1)
PAY_BODY=$(echo "$PAY_RESP" | sed '$d')
check_status "pay for order" "200" "$PAY_STATUS"
[[ "$(extract_json "status" "$PAY_BODY")" == "CONFIRMED" ]] || fail "paid order is not CONFIRMED. body: $PAY_BODY"
pass "paid order is CONFIRMED"

# Paying again must not charge twice.
PAY2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/pay" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -d '{"paymentMethodToken":"tok_visa"}')
check_status "paying a confirmed order is a no-op" "200" "$PAY2_STATUS"

PAYMENTS=$(curl -s "$BASE/paymentapi/orders/$ORDER_ID/payments" -H "$AUTH")
[[ "$(echo "$PAYMENTS" | grep -o '"status":"SUCCEEDED"' | wc -l | tr -d ' ')" == "1" ]] || \
  fail "expected exactly one succeeded payment. body: $PAYMENTS"
pass "exactly one successful payment recorded"

# The charge endpoint is service-to-service only; the gateway must not expose it.
INTERNAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/paymentapi/internal/payments" \
  -H "Content-Type: application/json" -d '{}')
check_status "internal payment endpoint hidden at the edge" "404" "$INTERNAL_STATUS"

# ── Declined card ─────────────────────────────────────────────────────────────
DECLINE_RESP=$(curl -s -X POST "$BASE/orderapi/orders" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -H "Idempotency-Key: smoke-decline-$(date +%s)" \
  -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}")
DECLINE_ORDER_ID=$(extract_json "id" "$DECLINE_RESP")
if [[ -n "$DECLINE_ORDER_ID" ]]; then
  DECLINED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$DECLINE_ORDER_ID/pay" \
    -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"paymentMethodToken":"tok_chargeDeclined"}')
  check_status "declined card" "402" "$DECLINED_STATUS"
  # The reservation survives a decline, so a second card can still complete the order.
  RETRY_PAY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$DECLINE_ORDER_ID/pay" \
    -H "Content-Type: application/json" -H "$AUTH" \
    -d '{"paymentMethodToken":"tok_mastercard"}')
  check_status "retry with another card after a decline" "200" "$RETRY_PAY_STATUS"
else
  echo "[WARN] Could not place an order for the decline check (inventory may be exhausted)"
fi

# ── Partial-failure order (bogus product → 4xx) ───────────────────────────────
BAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -H "Idempotency-Key: smoke-bad-$(date +%s)" \
  -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":999999,\"quantity\":9999}]}")
[[ "$BAD_STATUS" =~ ^4 ]] || fail "partial-failure order: expected 4xx, got $BAD_STATUS"
pass "partial-failure order rejected (HTTP $BAD_STATUS)"

# ── Cancel order ──────────────────────────────────────────────────────────────
CANCEL_RESP=$(curl -s -X POST "$BASE/orderapi/orders" \
  -H "Content-Type: application/json" -H "$AUTH" \
  -H "Idempotency-Key: smoke-cancel-$(date +%s)" \
  -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}")
CANCEL_ORDER_ID=$(extract_json "id" "$CANCEL_RESP")

if [[ -n "$CANCEL_ORDER_ID" ]]; then
  CANCEL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "$BASE/orderapi/orders/$CANCEL_ORDER_ID/cancel" -H "$AUTH")
  check_status "cancel order" "200" "$CANCEL_STATUS"
else
  echo "[WARN] Could not place a second order to cancel (inventory may be exhausted)"
fi

# ── Admin refund (through the order) ──────────────────────────────────────────
CUSTOMER_REFUND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/refund" \
  -H "Content-Type: application/json" -H "$AUTH" -d '{}')
check_status "customer cannot refund" "403" "$CUSTOMER_REFUND_STATUS"

CUSTOMER_PAYMENTS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/paymentapi/admin/payments" -H "$AUTH")
check_status "customer cannot list all payments" "403" "$CUSTOMER_PAYMENTS_STATUS"

if [[ -n "$ADMIN_TOKEN" ]]; then
  ADMIN_PAYMENTS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/paymentapi/admin/payments?page=0&size=5" -H "$ADMIN_AUTH")
  check_status "admin lists all payments" "200" "$ADMIN_PAYMENTS_STATUS"

  REFUND_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/refund" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" -d '{"reason":"Smoke test refund"}')
  check_status "admin refunds order" "200" "$(echo "$REFUND_RESP" | tail -1)"
  [[ "$(extract_json "status" "$(echo "$REFUND_RESP" | sed '$d')")" == "REFUNDED" ]] || \
    fail "refunded order is not REFUNDED. body: $REFUND_RESP"
  pass "order is REFUNDED"

  REFUNDED_PAYMENTS=$(curl -s "$BASE/paymentapi/orders/$ORDER_ID/payments" -H "$AUTH")
  echo "$REFUNDED_PAYMENTS" | grep -q '"status":"REFUNDED"' || fail "payment not REFUNDED. body: $REFUNDED_PAYMENTS"
  pass "payment is REFUNDED, in step with the order"

  REFUND_AGAIN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/refund" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" -d '{}')
  check_status "refunding twice is a no-op" "200" "$REFUND_AGAIN_STATUS"

  # ── Lockout and admin unlock ──────────────────────────────────────────────────
  # Needs about 8 credential requests on top of the rest of this script; the kind
  # values allow 60 a minute (values-local.yaml). Against a cloud's limit of 10, set
  # SMOKE_SKIP_LOCKOUT=1.
  if [[ "${SMOKE_SKIP_LOCKOUT:-0}" != "1" ]]; then
    LOCK_USER="${UNIQUE}_lock"
    LOCK_REG=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
      -d "{\"username\":\"$LOCK_USER\",\"password\":\"Password1!\"}")
    LOCK_USER_ID=$(extract_json "userId" "$LOCK_REG")
    [[ -n "$LOCK_USER_ID" ]] || fail "could not register the lockout user. body: $LOCK_REG"

    login_status() {
      curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
        -d "{\"username\":\"$LOCK_USER\",\"password\":\"$1\"}"
    }
    for attempt in 1 2 3 4; do
      check_status "wrong password $attempt/5" "401" "$(login_status 'wrong-password')"
    done
    check_status "5th wrong password locks the account" "423" "$(login_status 'wrong-password')"
    check_status "right password refused while locked" "423" "$(login_status 'Password1!')"

    USERS_LOOKUP=$(curl -s "$BASE/auth/admin/users/by-ids?ids=$LOCK_USER_ID" -H "$ADMIN_AUTH")
    echo "$USERS_LOOKUP" | grep -q '"lockedUntil":"' || fail "admin view does not show the lock. body: $USERS_LOOKUP"
    pass "admin sees the account as locked"

    UNLOCK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/admin/users/$LOCK_USER_ID/unlock" -H "$ADMIN_AUTH")
    check_status "admin unlocks the account" "200" "$UNLOCK_STATUS"
    check_status "login works after unlock" "200" "$(login_status 'Password1!')"
  fi
fi
CUSTOMER_UNLOCK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/admin/users/$USER_ID/unlock" -H "$AUTH")
check_status "customer cannot unlock accounts" "403" "$CUSTOMER_UNLOCK_STATUS"

# ── Procurement: PO → supplier → receiving → stock ───────────────────────────
# docs/specs/procurement.md §10. About 4 more credential calls. SMOKE_SKIP_PROCUREMENT=1
# skips it (e.g. a stack without procurement-service).
if [[ "${SMOKE_SKIP_PROCUREMENT:-0}" != "1" ]]; then
  # Without an admin there is no PO to test: that is a failure, not a skip.
  [[ -n "$ADMIN_TOKEN" ]] || fail "admin login failed, so the procurement checks can't run (SMOKE_SKIP_PROCUREMENT=1 to skip them)"
  SUPPLIER_USER="${SMOKE_SUPPLIER_USER:-${SUPPLIER_BOOTSTRAP_USERNAME:-supplier}}"
  SUPPLIER_PASS="${SMOKE_SUPPLIER_PASS:-${SUPPLIER_BOOTSTRAP_PASSWORD:-supplierpass}}"
  WAREHOUSE_USER="${SMOKE_WAREHOUSE_USER:-${WAREHOUSE_BOOTSTRAP_USERNAME:-warehouse}}"
  WAREHOUSE_PASS="${SMOKE_WAREHOUSE_PASS:-${WAREHOUSE_BOOTSTRAP_PASSWORD:-warehousepass}}"
  PO_API="$BASE/procurementapi/purchase-orders"
  login_token() {
    # Body built first: bash 3.2 (macOS) mangles escaped quotes inside "$( ... )".
    local body="{\"username\":\"$1\",\"password\":\"$2\"}"
    local resp
    resp=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" -d "$body")
    extract_token "$resp"
  }
  stock_of() {
    extract_json "availableQuantity" "$(curl -s "$BASE/inventoryapi/products/$PRODUCT_ID" -H "$ADMIN_AUTH")"
  }

  # Supplier 1 must sell the smoke product. PUT replaces the catalogue, so append to it.
  CATALOGUE=$(curl -s "$BASE/procurementapi/suppliers/1/products" -H "$ADMIN_AUTH")
  ENTRY="{\"productId\":$PRODUCT_ID,\"unitCost\":5.99}"
  if [[ "$CATALOGUE" == "[]" ]]; then NEW_CATALOGUE="[$ENTRY]"; else NEW_CATALOGUE="${CATALOGUE%]},$ENTRY]"; fi
  CAT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE/procurementapi/suppliers/1/products" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" -d "{\"products\":$NEW_CATALOGUE}")
  check_status "admin adds the product to the supplier catalogue" "200" "$CAT_STATUS"

  # 1. Admin raises a PO for 50 and sends it.
  PO_RESP=$(curl -s -X POST "$PO_API" -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d "{\"supplierId\":1,\"notes\":\"smoke\",\"lines\":[{\"productId\":$PRODUCT_ID,\"quantity\":50}]}")
  PO_ID=$(extract_json "id" "$PO_RESP")
  [[ -n "$PO_ID" ]] || fail "create purchase order. body: $PO_RESP"
  LINE_ID=$(echo "$PO_RESP" | grep -o '"lines":\[{"id":[0-9]*' | grep -o '[0-9]*$' || true)
  [[ -n "$LINE_ID" ]] || fail "PO $PO_ID has no line id. body: $PO_RESP"
  [[ "$(extract_json "status" "$(curl -s -X POST "$PO_API/$PO_ID/send" -H "$ADMIN_AUTH")")" == "SENT" ]] || fail "send PO $PO_ID"
  pass "admin created and sent PO $PO_ID"

  # 2. The supplier sees it; another supplier can't; it accepts and ships 50.
  SUPPLIER_TOKEN=$(login_token "$SUPPLIER_USER" "$SUPPLIER_PASS")
  [[ -n "$SUPPLIER_TOKEN" ]] || fail "supplier login"
  SUPPLIER_AUTH="Authorization: Bearer $SUPPLIER_TOKEN"
  check_status "supplier sees its PO" "200" "$(curl -s -o /dev/null -w "%{http_code}" "$PO_API/$PO_ID" -H "$SUPPLIER_AUTH")"

  OTHER_SUPPLIER=$(curl -s -X POST "$BASE/procurementapi/suppliers" -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d "{\"name\":\"Smoke Supplier $UNIQUE\",\"contactEmail\":\"smoke@supplier.test\"}")
  OTHER_SUPPLIER_ID=$(extract_json "id" "$OTHER_SUPPLIER")
  OTHER_USER="${UNIQUE}_sup"
  OTHER_REG=$(curl -s -X POST "$BASE/auth/register" -H "Content-Type: application/json" \
    -d "{\"username\":\"$OTHER_USER\",\"password\":\"Password1!\"}")
  OTHER_USER_ID=$(extract_json "userId" "$OTHER_REG")
  curl -s -o /dev/null -X PATCH "$BASE/auth/admin/users/$OTHER_USER_ID/role" -H "Content-Type: application/json" \
    -H "$ADMIN_AUTH" -d "{\"role\":\"SUPPLIER\",\"supplierId\":$OTHER_SUPPLIER_ID}"
  OTHER_TOKEN=$(login_token "$OTHER_USER" "Password1!")
  [[ -n "$OTHER_TOKEN" ]] || fail "second supplier login"
  check_status "another supplier cannot see the PO" "404" \
    "$(curl -s -o /dev/null -w "%{http_code}" "$PO_API/$PO_ID" -H "Authorization: Bearer $OTHER_TOKEN")"

  ACCEPT_RESP=$(curl -s -X POST "$PO_API/$PO_ID/accept" -H "Content-Type: application/json" -H "$SUPPLIER_AUTH" \
    -d "{\"expectedDate\":\"$(date -u +%F)\"}")
  [[ "$(extract_json "status" "$ACCEPT_RESP")" == "ACCEPTED" ]] || fail "supplier accepts PO. body: $ACCEPT_RESP"
  SHIP_RESP=$(curl -s -X POST "$PO_API/$PO_ID/shipments" -H "Content-Type: application/json" -H "$SUPPLIER_AUTH" \
    -d "{\"carrier\":\"Smoke Freight\",\"trackingNumber\":\"SMK1\",\"lines\":[{\"poLineId\":$LINE_ID,\"quantityShipped\":50}]}")
  [[ "$(extract_json "status" "$SHIP_RESP")" == "SHIPPED" ]] || fail "supplier ships PO. body: $SHIP_RESP"
  pass "supplier accepted and sent a shipment notice for 50"

  # 3. Warehouse counts 40 good + 2 damaged (8 short).
  STOCK_BEFORE=$(stock_of)
  WAREHOUSE_TOKEN=$(login_token "$WAREHOUSE_USER" "$WAREHOUSE_PASS")
  [[ -n "$WAREHOUSE_TOKEN" ]] || fail "warehouse login"
  RECEIPT_KEY="smoke-receipt-$UNIQUE"
  receive() {
    curl -s -X POST "$PO_API/$PO_ID/receipts" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $WAREHOUSE_TOKEN" -H "Idempotency-Key: $RECEIPT_KEY" \
      -d "{\"note\":\"2 crushed\",\"lines\":[{\"poLineId\":$LINE_ID,\"quantityGood\":40,\"quantityDamaged\":2}]}"
  }
  RECEIPT_RESP=$(receive)
  [[ "$(extract_json "status" "$RECEIPT_RESP")" == "PARTIALLY_RECEIVED" ]] || fail "warehouse receipt. body: $RECEIPT_RESP"
  pass "warehouse received 40 good + 2 damaged: PO PARTIALLY_RECEIVED"

  # 4. Stock rises by the good units only, through the event, within 10 s.
  EXPECTED=$((STOCK_BEFORE + 40))
  for _ in $(seq 1 20); do
    [[ "$(stock_of)" == "$EXPECTED" ]] && break
    sleep 0.5
  done
  [[ "$(stock_of)" == "$EXPECTED" ]] || fail "stock should be $EXPECTED after the receipt, is $(stock_of)"
  curl -s "$BASE/inventoryapi/products/$PRODUCT_ID/adjustments" -H "$ADMIN_AUTH" | grep -q '"referenceType":"PO_RECEIPT"' \
    || fail "no PO_RECEIPT ledger row"
  pass "stock +40 with a PO_RECEIPT ledger entry"

  # 5. Replaying the same Idempotency-Key counts nothing twice.
  REPLAY_RESP=$(receive)
  [[ "$(extract_json "status" "$REPLAY_RESP")" == "PARTIALLY_RECEIVED" ]] || fail "receipt replay. body: $REPLAY_RESP"
  sleep 3
  [[ "$(stock_of)" == "$EXPECTED" ]] || fail "replay changed stock to $(stock_of)"
  pass "replayed receipt did not add stock again"

  # 6. Admin closes it short.
  CLOSE_RESP=$(curl -s -X POST "$PO_API/$PO_ID/close" -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d '{"note":"Rest backordered"}')
  [[ "$(extract_json "status" "$CLOSE_RESP")" == "RECEIVED" ]] || fail "close short. body: $CLOSE_RESP"
  pass "admin closed the PO short: RECEIVED"

  # 7. Who can't reach what.
  check_status "supplier cannot read inventory" "403" \
    "$(curl -s -o /dev/null -w "%{http_code}" "$BASE/inventoryapi/products/$PRODUCT_ID" -H "$SUPPLIER_AUTH")"
  check_status "customer cannot read purchase orders" "403" \
    "$(curl -s -o /dev/null -w "%{http_code}" "$PO_API" -H "$AUTH")"

  # 8. The received stock sells.
  BUY_RESP=$(curl -s -X POST "$BASE/orderapi/orders" -H "Content-Type: application/json" -H "$AUTH" \
    -H "Idempotency-Key: smoke-buy-$UNIQUE" \
    -d "{\"userId\":$USER_ID,\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":25}]}")
  [[ "$(extract_json "status" "$BUY_RESP")" == "INVENTORY_RESERVED" ]] || fail "customer buys received stock. body: $BUY_RESP"
  pass "customer can buy the received stock"
fi

# ── Sign out everywhere ───────────────────────────────────────────────────────
# The first session was revoked by the reuse check above, so start a fresh one.
COOKIES2=$(mktemp)
trap 'rm -f "$COOKIES" "$COOKIES.old" "$COOKIES2"' EXIT
LOGIN2_RESP=$(curl -s -c "$COOKIES2" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$UNIQUE\",\"password\":\"Password1!\"}")
TOKEN2=$(extract_token "$LOGIN2_RESP")
[[ -n "$TOKEN2" ]] || fail "second login did not return a token. body: $LOGIN2_RESP"
LOGOUT_ALL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/logout-all" -H "Authorization: Bearer $TOKEN2")
check_status "sign out everywhere" "204" "$LOGOUT_ALL_STATUS"
AFTER_LOGOUT_ALL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIES2" -X POST "$BASE/auth/refresh")
check_status "refresh fails after signing out everywhere" "401" "$AFTER_LOGOUT_ALL_STATUS"

echo ""
echo "All smoke checks passed."
