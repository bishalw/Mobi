# Local Kubernetes With kind

This directory contains the local Kubernetes runtime for ScalableRest.

The goal is to mirror the working Docker Compose topology while using Kubernetes primitives:

```text
localhost / scalablerest.local
  -> ingress-nginx
  -> api-gateway
  -> auth-service / product-service / inventory-service / order-service

auth-service and order-service publish Kafka events.
notification-service consumes Kafka events.
service-owned databases run inside one local MySQL dependency.
order-service uses Redis for idempotency keys.
```

## Required tools

- Docker Desktop
- kind
- kubectl
- Helm

## Planned flow

1. Create the kind cluster with `k8s/kind/cluster.yaml`.
2. Install ingress-nginx.
3. Apply the namespace, ConfigMap, and local Secret.
4. Install MySQL, Redis, and Kafka with Helm.
5. Build and load the local service images into kind.
6. Apply the app Deployments, Services, and Ingress.
7. Run the smoke test through the ingress endpoint.

## Local host

The ingress can be reached through `localhost` once the kind cluster maps host port `80` to the ingress controller.

For a named local host, add this entry to `/etc/hosts`:

```text
127.0.0.1 scalablerest.local
```

## Ingress

Install ingress-nginx with the local values file:

```bash
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --values k8s/infra/ingress-nginx/values.yaml
```

The application ingress routes public paths to `api-gateway`; downstream service routing stays inside the gateway.

```bash
kubectl apply -f k8s/ingress/api-gateway-ingress.yaml
```

## Secrets

Do not commit real secret values.

Copy `k8s/base/secrets.example.yaml` to a local-only file or create the secret directly with `kubectl`.
