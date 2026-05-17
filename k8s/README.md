# Local Kubernetes With kind

This directory contains the local Kubernetes runtime for ScalableRest.

The goal is to run the local stack through Kubernetes primitives:

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

The helper scripts wrap that flow:

```bash
./scripts/kind-up.sh
./scripts/kind-load-images.sh
./scripts/k8s-deploy.sh
./scripts/k8s-smoke.sh
```

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

The application ingress routes public paths to `api-gateway`; downstream service routing stays inside the gateway. The app ingress is rendered by the `helm/scalablerest` chart.

```bash
helm upgrade --install scalablerest helm/scalablerest \
  --namespace scalablerest \
  --values helm/scalablerest/values-local.yaml
```

## Secrets

Do not commit real secret values.

Copy `k8s/base/secrets.example.yaml` to a local-only file or create the secret directly with `kubectl`.

## Dependencies

The local cluster uses Helm for infrastructure dependencies. The release names below are intentional because the app ConfigMap points to their Kubernetes Service names:

```text
mysql
redis
kafka
```

Install MySQL:

```bash
kubectl apply -f k8s/infra/mysql/initdb-configmap.yaml
helm upgrade --install mysql bitnami/mysql \
  --namespace scalablerest \
  --values k8s/infra/mysql/values.yaml \
  --set auth.rootPassword='<local-root-password>'
```

Install Redis:

```bash
helm upgrade --install redis bitnami/redis \
  --namespace scalablerest \
  --values k8s/infra/redis/values.yaml
```

Install Kafka:

```bash
helm upgrade --install kafka bitnami/kafka \
  --namespace scalablerest \
  --values k8s/infra/kafka/values.yaml
```

Expected in-cluster dependency endpoints:

```text
jdbc:mysql://mysql:3306/authdb
redis-master:6379
kafka:9092
```

## Applications

Apply the app workloads after the dependency pods are ready and the local images have been loaded into kind:

```bash
helm upgrade --install scalablerest helm/scalablerest \
  --namespace scalablerest \
  --values helm/scalablerest/values-local.yaml \
  --wait
```

The app Deployments use these local image tags:

```text
scalablerest/api-gateway:local
scalablerest/auth-service:local
scalablerest/product-service:local
scalablerest/inventory-service:local
scalablerest/order-service:local
scalablerest/notification-service:local
```
