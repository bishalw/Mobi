#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.local}"
NAMESPACE="${K8S_NAMESPACE:-mobi}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_env() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment value: $name" >&2
    echo "Set it in $ENV_FILE or export it before running this script." >&2
    exit 1
  fi
}

require_cmd kubectl
require_cmd helm

INTERNAL_SERVICE_SECRET="${INTERNAL_SERVICE_SECRET:-local-internal-secret}"
ADMIN_BOOTSTRAP_USERNAME="${ADMIN_BOOTSTRAP_USERNAME:-admin}"
ADMIN_BOOTSTRAP_PASSWORD="${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}"

required_env=(
  MYSQL_ROOT_PASSWORD
  AUTH_DB_PASSWORD
  PRODUCT_DB_PASSWORD
  INVENTORY_DB_PASSWORD
  ORDER_DB_PASSWORD
  JWT_SECRET
  INTERNAL_SERVICE_SECRET
  ADMIN_BOOTSTRAP_USERNAME
  ADMIN_BOOTSTRAP_PASSWORD
)

for name in "${required_env[@]}"; do
  require_env "$name"
done

kubectl apply -f k8s/base/namespace.yaml

kubectl create secret generic mobi-secrets \
  --namespace "$NAMESPACE" \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  --from-literal=AUTH_DB_PASSWORD="$AUTH_DB_PASSWORD" \
  --from-literal=PRODUCT_DB_PASSWORD="$PRODUCT_DB_PASSWORD" \
  --from-literal=INVENTORY_DB_PASSWORD="$INVENTORY_DB_PASSWORD" \
  --from-literal=ORDER_DB_PASSWORD="$ORDER_DB_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=INTERNAL_SERVICE_SECRET="$INTERNAL_SERVICE_SECRET" \
  --from-literal=ADMIN_BOOTSTRAP_USERNAME="$ADMIN_BOOTSTRAP_USERNAME" \
  --from-literal=ADMIN_BOOTSTRAP_PASSWORD="$ADMIN_BOOTSTRAP_PASSWORD" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

kubectl apply -f k8s/infra/mysql/initdb-configmap.yaml

helm upgrade --install mysql bitnami/mysql \
  --namespace "$NAMESPACE" \
  --values k8s/infra/mysql/values.yaml \
  --set-string auth.rootPassword="$MYSQL_ROOT_PASSWORD" \
  --wait \
  --timeout 10m

helm upgrade --install redis bitnami/redis \
  --namespace "$NAMESPACE" \
  --values k8s/infra/redis/values.yaml \
  --wait \
  --timeout 10m

helm upgrade --install kafka bitnami/kafka \
  --namespace "$NAMESPACE" \
  --values k8s/infra/kafka/values.yaml \
  --wait \
  --timeout 10m

if ! helm status mobi --namespace "$NAMESPACE" >/dev/null 2>&1; then
  kubectl delete deployment,service --namespace "$NAMESPACE" -l app.kubernetes.io/part-of=mobi --ignore-not-found
  kubectl delete ingress api-gateway --namespace "$NAMESPACE" --ignore-not-found
  kubectl delete configmap mobi-config --namespace "$NAMESPACE" --ignore-not-found
fi

helm upgrade --install mobi helm/mobi \
  --namespace "$NAMESPACE" \
  --values helm/mobi/values-local.yaml \
  --wait \
  --timeout 5m

kubectl rollout status deployment/auth-service --namespace "$NAMESPACE" --timeout 5m
kubectl rollout status deployment/product-service --namespace "$NAMESPACE" --timeout 5m
kubectl rollout status deployment/inventory-service --namespace "$NAMESPACE" --timeout 5m
kubectl rollout status deployment/order-service --namespace "$NAMESPACE" --timeout 5m
kubectl rollout status deployment/notification-service --namespace "$NAMESPACE" --timeout 5m
kubectl rollout status deployment/api-gateway --namespace "$NAMESPACE" --timeout 5m

echo "Mobi Kubernetes deployment is ready"
