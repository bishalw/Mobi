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

kubectl label node "$CLUSTER_NAME-control-plane" ingress-ready=true --overwrite

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null
helm repo add bitnami https://charts.bitnami.com/bitnami >/dev/null
helm repo update

helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --values k8s/infra/ingress-nginx/values.yaml \
  --wait \
  --timeout 5m

kubectl apply -f k8s/base/namespace.yaml

echo "kind cluster '$CLUSTER_NAME' is ready"
