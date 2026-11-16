#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${KIND_CLUSTER_NAME:-mobi}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd kind

images=(
  mobi/api-gateway:local
  mobi/auth-service:local
  mobi/product-service:local
  mobi/inventory-service:local
  mobi/order-service:local
  mobi/payment-service:local
  mobi/procurement-service:local
  mobi/notification-service:local
)

contexts=(
  api-gateway
  auth-service
  product-service
  inventory-service
  order-service
  payment-service
  procurement-service
  notification-service
)

for index in "${!images[@]}"; do
  docker build -t "${images[$index]}" "./${contexts[$index]}"
done

for image in "${images[@]}"; do
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done

echo "Loaded ${#images[@]} local images into kind cluster '$CLUSTER_NAME'"
