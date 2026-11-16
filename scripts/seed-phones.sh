#!/usr/bin/env bash
set -e

# Demo catalogue, stock and a few orders, for a freshly deployed local stack.
# Uses the admin account from .env.local (ADMIN_BOOTSTRAP_USERNAME / _PASSWORD).
ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env.local}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi
BASE="${SMOKE_BASE_URL:-http://localhost}"
ADMIN_USER="${ADMIN_BOOTSTRAP_USERNAME:-admin}"
ADMIN_PASS="${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}"

echo "==> Logging in as admin..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: Could not get admin token. Is the stack running?"
  exit 1
fi
echo "    Got admin token."

echo ""
echo "==> Creating products..."

create_product() {
  local id
  id=$(curl -s -X POST "$BASE/productapi/products" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2 || true)
  # Stop here rather than seed stock and orders against empty ids.
  if [[ -z "$id" ]]; then
    echo "ERROR: could not create product $1 (is product-service up, or does the SKU already exist?)" >&2
    exit 1
  fi
  echo "$id"
}

P1=$(create_product '{"sku":"IPH-15-PRO-256","name":"iPhone 15 Pro 256GB","description":"Apple iPhone 15 Pro, 256GB, Titanium Black","price":999.99,"active":true}')
echo "    Created iPhone 15 Pro         id=$P1"

P2=$(create_product '{"sku":"IPH-15-PRO-512","name":"iPhone 15 Pro 512GB","description":"Apple iPhone 15 Pro, 512GB, Titanium White","price":1199.99,"active":true}')
echo "    Created iPhone 15 Pro 512GB   id=$P2"

P3=$(create_product '{"sku":"SAM-S24-ULT-256","name":"Samsung Galaxy S24 Ultra","description":"Samsung Galaxy S24 Ultra, 256GB, Phantom Black","price":1299.99,"active":true}')
echo "    Created Samsung S24 Ultra     id=$P3"

P4=$(create_product '{"sku":"SAM-S24-128","name":"Samsung Galaxy S24","description":"Samsung Galaxy S24, 128GB, Marble Gray","price":799.99,"active":true}')
echo "    Created Samsung Galaxy S24    id=$P4"

P5=$(create_product '{"sku":"PIX-8-PRO-128","name":"Google Pixel 8 Pro","description":"Google Pixel 8 Pro, 128GB, Obsidian","price":899.99,"active":true}')
echo "    Created Google Pixel 8 Pro    id=$P5"

P6=$(create_product '{"sku":"PIX-8-128","name":"Google Pixel 8","description":"Google Pixel 8, 128GB, Hazel","price":699.99,"active":true}')
echo "    Created Google Pixel 8        id=$P6"

P7=$(create_product '{"sku":"ONE-12-PRO-256","name":"OnePlus 12 Pro","description":"OnePlus 12 Pro, 256GB, Silky Black","price":749.99,"active":true}')
echo "    Created OnePlus 12 Pro        id=$P7"

P8=$(create_product '{"sku":"MOTO-EDGE-128","name":"Motorola Edge 40","description":"Motorola Edge 40, 128GB, Eclipse Black","price":549.99,"active":true}')
echo "    Created Motorola Edge 40      id=$P8"

echo ""
echo "==> Creating inventory..."

# Opening stock is an INITIAL ledger entry (which creates the stock record), then the
# shelf and the low-stock threshold are set on it.
create_inventory() { # productId quantity location reorderLevel
  local auth=(-H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN")
  curl -s -X POST "$BASE/inventoryapi/products/$1/adjustments" "${auth[@]}" \
    -d "{\"quantityDelta\":$2,\"reason\":\"INITIAL\",\"note\":\"Opening stock\"}" > /dev/null
  curl -s -X PATCH "$BASE/inventoryapi/products/$1" "${auth[@]}" \
    -d "{\"warehouseLocation\":\"$3\",\"reorderLevel\":$4}" > /dev/null
}

create_inventory "$P1" 42 "Shelf A1" 10
create_inventory "$P2" 18 "Shelf A2" 10
create_inventory "$P3" 35 "Shelf B1" 10
create_inventory "$P4" 60 "Shelf B2" 15
create_inventory "$P5" 25 "Shelf C1" 5
create_inventory "$P6" 4  "Shelf C2" 5   # starts below its reorder level: shows the low-stock badge
# OnePlus 12 Pro ($P7) is deliberately left unstocked: it shows as out of stock until
# a purchase order for it is received.
create_inventory "$P8" 12 "Shelf D2" 5
echo "    Inventory created for all products."

echo ""
echo "==> Supplier catalogue and a purchase order..."

# Supplier 1 ("Mobi Distribution", seeded by procurement-service) sells every product
# at 60% of its retail price.
cost() { awk -v p="$1" 'BEGIN { printf "%.2f", p * 0.6 }'; }
CATALOGUE="{\"products\":["
for pair in "$P1:999.99" "$P2:1199.99" "$P3:1299.99" "$P4:799.99" "$P5:899.99" "$P6:699.99" "$P7:749.99" "$P8:549.99"; do
  CATALOGUE+="{\"productId\":${pair%%:*},\"unitCost\":$(cost "${pair#*:}")},"
done
CATALOGUE="${CATALOGUE%,}]}"
curl -s -X PUT "$BASE/procurementapi/suppliers/1/products" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "$CATALOGUE" > /dev/null

# The unstocked OnePlus, ordered and sent, so the supplier login has something to accept.
PO_ID=$(curl -s -X POST "$BASE/procurementapi/purchase-orders" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"supplierId\":1,\"notes\":\"Restock: OnePlus 12 Pro is out of stock\",\"lines\":[{\"productId\":$P7,\"quantity\":20},{\"productId\":$P6,\"quantity\":30}]}" \
  | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$PO_ID" ]; then
  curl -s -X POST "$BASE/procurementapi/purchase-orders/$PO_ID/send" -H "Authorization: Bearer $ADMIN_TOKEN" > /dev/null
  echo "    Catalogue set; purchase order $PO_ID sent to Mobi Distribution."
else
  echo "    WARNING: could not create the demo purchase order (is procurement-service up?)"
fi

echo ""
echo "==> Registering test user..."
REG=$(curl -s -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass123"}')
USER_ID=$(echo "$REG" | grep -o '"userId":[0-9]*' | cut -d: -f2)

if [ -z "$USER_ID" ]; then
  echo "    User may already exist, logging in..."
fi

USER_TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

USER_ID=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass123"}' | grep -o '"userId":[0-9]*' | cut -d: -f2)
echo "    Test user id=$USER_ID"

echo ""
echo "==> Placing orders..."

place_order() {
  curl -s -X POST "$BASE/orderapi/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER_TOKEN" \
    -H "Idempotency-Key: seed-$(date +%s%N)-$RANDOM" \
    -d "$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2
}

O1=$(place_order "{\"userId\":$USER_ID,\"items\":[{\"productId\":$P1,\"quantity\":1}]}")
echo "    Order $O1: 1x iPhone 15 Pro"

sleep 1

O2=$(place_order "{\"userId\":$USER_ID,\"items\":[{\"productId\":$P3,\"quantity\":1},{\"productId\":$P6,\"quantity\":2}]}")
echo "    Order $O2: 1x S24 Ultra + 2x Pixel 8"

sleep 1

O3=$(place_order "{\"userId\":$USER_ID,\"items\":[{\"productId\":$P4,\"quantity\":1}]}")
echo "    Order $O3: 1x Samsung Galaxy S24"

echo ""
echo "Done. Log in at http://localhost:5173"
echo "  admin:     admin / adminpass"
echo "  warehouse: warehouse / warehousepass"
echo "  supplier:  supplier / supplierpass"
echo "  testuser:  testuser / testpass123"
