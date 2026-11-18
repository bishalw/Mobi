#!/usr/bin/env bash
#
# Installs Envoy Gateway, the Gateway API controller that replaced ingress-nginx, and
# the `eg` GatewayClass the Mobi chart's Gateway refers to.
#
# Run once per cluster: scripts/kind-up.sh calls it for kind, and on EKS / GKE / AKS
# run it once before the first scripts/deploy.sh. The Helm chart bundles the Gateway
# API CRDs, so nothing else needs installing first.
#
# Why Envoy Gateway: the community ingress-nginx controller was retired in March 2026
# and Kubernetes recommends moving to Gateway API. Envoy Gateway is the CNCF Envoy
# project's own implementation, is fully Gateway API conformant, runs the same way on
# every cloud, and covers what Mobi needs at the edge (rate limits, retries,
# timeouts) as first-class policy resources instead of annotations.

set -euo pipefail

ENVOY_GATEWAY_VERSION="${ENVOY_GATEWAY_VERSION:-v1.9.3}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

command -v helm >/dev/null 2>&1 || { echo "missing required command: helm" >&2; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "missing required command: kubectl" >&2; exit 1; }

echo "==> installing Envoy Gateway $ENVOY_GATEWAY_VERSION"
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version "$ENVOY_GATEWAY_VERSION" \
  --namespace envoy-gateway-system \
  --create-namespace \
  --wait \
  --timeout 5m

kubectl wait --namespace envoy-gateway-system \
  --for=condition=Available deployment/envoy-gateway --timeout=5m

kubectl apply -f "$REPO_ROOT/k8s/infra/envoy-gateway/gatewayclass.yaml"
kubectl wait --for=condition=Accepted gatewayclass/eg --timeout=2m

echo "Envoy Gateway is ready (GatewayClass 'eg')"
