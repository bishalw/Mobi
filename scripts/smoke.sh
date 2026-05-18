#!/usr/bin/env bash
set -euo pipefail

BASE="${SMOKE_BASE_URL:-http://localhost}"
ADMIN_USER="${SMOKE_ADMIN_USER:-admin}"
ADMIN_PASS="${SMOKE_ADMIN_PASS:-adminpass}"

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
LOGIN_RESP=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$UNIQUE\",\"password\":\"Password1!\"}")
TOKEN=$(extract_token "$LOGIN_RESP")
[[ -n "$TOKEN" ]] || fail "login did not return a token"
USER_ID=$(extract_json "userId" "$LOGIN_RESP")
[[ -n "$USER_ID" ]] || fail "login did not return a userId"
pass "login (got JWT for user $USER_ID)"

AUTH="Authorization: Bearer $TOKEN"

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

  curl -s -o /dev/null -X POST "$BASE/inventoryapi/products" \
    -H "Content-Type: application/json" -H "$ADMIN_AUTH" \
    -d "{\"productId\":$PRODUCT_ID,\"availableQuantity\":20,\"warehouseLocation\":\"A1\"}" || true
  pass "seeded product $PRODUCT_ID with 20 units"
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

# ── Confirm order ─────────────────────────────────────────────────────────────
CONFIRM_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/confirm" \
  -H "$AUTH")
check_status "confirm order" "204" "$CONFIRM_STATUS"

# Confirm again — must be idempotent (204 if inventory is CONFIRMED no-op, or 409 on wrong order status)
CONFIRM2_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/orderapi/orders/$ORDER_ID/confirm" \
  -H "$AUTH")
[[ "$CONFIRM2_STATUS" == "204" || "$CONFIRM2_STATUS" == "409" ]] || \
  fail "confirm idempotency: expected 204 or 409, got $CONFIRM2_STATUS"
pass "confirm order idempotency (HTTP $CONFIRM2_STATUS)"

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

echo ""
echo "All smoke checks passed."
