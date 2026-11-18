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
require_cmd kubectl
require_cmd helm

if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  kind create cluster --config k8s/kind/cluster.yaml
else
  echo "kind cluster '$CLUSTER_NAME' already exists"
fi

helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null
helm repo update

# Clusters created before the move to Gateway API still run the retired ingress-nginx
# controller. Remove it so it cannot keep serving stale routes on the same ports.
if helm status ingress-nginx --namespace ingress-nginx >/dev/null 2>&1; then
  echo "==> removing retired ingress-nginx"
  helm uninstall ingress-nginx --namespace ingress-nginx --wait
  kubectl delete namespace ingress-nginx --ignore-not-found
fi

"$(dirname "${BASH_SOURCE[0]}")/install-envoy-gateway.sh"

kubectl apply -f k8s/base/namespace.yaml

echo "kind cluster '$CLUSTER_NAME' is ready"
