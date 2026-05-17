#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${KIND_CLUSTER_NAME:-scalablerest}"
ENV_FILE="${ENV_FILE:-.env.local}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd kind

COMPOSE_ENV_ARGS=()
if [[ -f "$ENV_FILE" ]]; then
  COMPOSE_ENV_ARGS=(--env-file "$ENV_FILE")
fi

docker compose "${COMPOSE_ENV_ARGS[@]}" build

images=(
  scalablerest/api-gateway:local
  scalablerest/auth-service:local
  scalablerest/product-service:local
  scalablerest/inventory-service:local
  scalablerest/order-service:local
  scalablerest/notification-service:local
)

for image in "${images[@]}"; do
  kind load docker-image "$image" --name "$CLUSTER_NAME"
done

echo "Loaded ${#images[@]} local images into kind cluster '$CLUSTER_NAME'"

