#!/usr/bin/env bash
set -e

BASE="http://localhost:8080"

echo "==> Logging in as admin..."
ADMIN_TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"adminpass"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ADMIN_TOKEN" ]; then
  echo "ERROR: Could not get admin token. Is the stack running?"
  exit 1
fi
echo "    Got admin token."

echo ""
echo "==> Creating products..."

create_product() {
  curl -s -X POST "$BASE/productapi/products" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2
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

create_inventory() {
  curl -s -X POST "$BASE/inventoryapi/products" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "$1" > /dev/null
}

create_inventory "{\"productId\":$P1,\"availableQuantity\":42,\"warehouseLocation\":\"Shelf A1\"}"
create_inventory "{\"productId\":$P2,\"availableQuantity\":18,\"warehouseLocation\":\"Shelf A2\"}"
create_inventory "{\"productId\":$P3,\"availableQuantity\":35,\"warehouseLocation\":\"Shelf B1\"}"
create_inventory "{\"productId\":$P4,\"availableQuantity\":60,\"warehouseLocation\":\"Shelf B2\"}"
create_inventory "{\"productId\":$P5,\"availableQuantity\":25,\"warehouseLocation\":\"Shelf C1\"}"
create_inventory "{\"productId\":$P6,\"availableQuantity\":4,\"warehouseLocation\":\"Shelf C2\"}"
create_inventory "{\"productId\":$P7,\"availableQuantity\":0,\"warehouseLocation\":\"Shelf D1\"}"
create_inventory "{\"productId\":$P8,\"availableQuantity\":12,\"warehouseLocation\":\"Shelf D2\"}"
echo "    Inventory created for all products."

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
echo "  admin:    admin / adminpass"
echo "  testuser: testuser / testpass123"
