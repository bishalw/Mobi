# Mobi

Spring Boot microservices demo using Spring Cloud Gateway, MySQL, Redis, Kafka, Kubernetes, kind, and Helm.

## Architecture

| Service | Port | Description |
| --- | ---: | --- |
| `api-gateway` | `8080` | Gateway entry point for clients |
| `auth-service` | `8082` | Authentication service |
| `product-service` | `8083` | Product API service |
| `inventory-service` | `8084` | Inventory and stock reservation service |
| `order-service` | `8085` | Order creation and checkout orchestration service |
| `notification-service` | `8086` | Kafka consumer for user and order events |
| `redis` | `6379` | Redis for order idempotency keys |
| `kafka` | `9092` | Kafka broker for asynchronous domain events |
| `mysql` | internal `3306` | MySQL server with separate service-owned databases |

Runtime flow:

```text
Client
  -> ingress-nginx
  -> api-gateway
  -> auth-service / product-service / inventory-service / order-service

auth-service and order-service publish Kafka events.
notification-service consumes Kafka events.
auth-service, product-service, inventory-service, and order-service use separate MySQL databases.
order-service uses Redis for idempotency keys.
```

## Requirements

- Docker Desktop
- kind
- kubectl
- Helm

Optional for direct service development:

- Java 21
- Maven wrapper included in each service

## Environment Setup

Copy the example environment file:

```bash
cp .env.example .env.local
```

Edit `.env.local` and set local values:

```bash
MYSQL_ROOT_PASSWORD=localdevpassword

AUTH_DB_PASSWORD=replace-with-local-auth-db-password
PRODUCT_DB_PASSWORD=replace-with-local-product-db-password
INVENTORY_DB_PASSWORD=replace-with-local-inventory-db-password
ORDER_DB_PASSWORD=replace-with-local-order-db-password

JWT_SECRET=replace-with-at-least-32-character-secret
INTERNAL_SERVICE_SECRET=local-internal-secret

ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=replace-with-local-admin-password
```

`.env.local` is ignored by git and should never be committed.

## Running Locally With Kubernetes

Start the kind cluster and install ingress-nginx:

```bash
./scripts/kind-up.sh
```

Build service images and load them into kind:

```bash
./scripts/kind-load-images.sh
```

Deploy MySQL, Redis, Kafka, and the app chart:

```bash
./scripts/k8s-deploy.sh
```

Run the end-to-end smoke test through ingress:

```bash
./scripts/k8s-smoke.sh
```

The app is exposed locally at:

```text
http://localhost
```

## Local Kubernetes Shape

```text
Docker Desktop
  -> kind cluster
    -> ingress-nginx
      -> api-gateway
        -> auth-service
        -> product-service
        -> inventory-service
        -> order-service

    -> notification-service
    -> MySQL
    -> Redis
    -> Kafka
```

The app services are deployed through:

```text
helm/mobi
```

Local values:

```text
helm/mobi/values-local.yaml
```

AWS skeleton values:

```text
helm/mobi/values-aws.yaml
```

## Verification

Gateway health:

```bash
curl http://localhost/actuator/health
curl http://localhost/actuator/health/readiness
curl http://localhost/actuator/health/liveness
```

Cluster status:

```bash
kubectl get pods -n mobi
helm list -n mobi
```

Expected behavior:

- All app and dependency pods are `Running`.
- The `mobi` Helm release is `deployed`.
- The smoke script registers a user, seeds product/inventory as the local admin, creates/confirms/cancels orders, and notification logs show Kafka consumer activity.

## Service Routing

The gateway routes through explicit, environment-overridable service URLs:

```properties
spring.cloud.gateway.server.webflux.routes[0].uri=${AUTH_SERVICE_URL:http://localhost:8082}
spring.cloud.gateway.server.webflux.routes[1].uri=${PRODUCT_SERVICE_URL:http://localhost:8083}
spring.cloud.gateway.server.webflux.routes[2].uri=${INVENTORY_SERVICE_URL:http://localhost:8084}
spring.cloud.gateway.server.webflux.routes[3].uri=${ORDER_SERVICE_URL:http://localhost:8085}
```

Kubernetes sets these to internal Service DNS names:

```text
AUTH_SERVICE_URL=http://auth-service:8082
PRODUCT_SERVICE_URL=http://product-service:8083
INVENTORY_SERVICE_URL=http://inventory-service:8084
ORDER_SERVICE_URL=http://order-service:8085
```

## Configuration

Service configuration lives in each service's `src/main/resources/application.properties` and is overridden by environment variables.

Non-sensitive Kubernetes config is rendered by the app Helm chart into a ConfigMap:

```text
AUTH_DB_URL
PRODUCT_DB_URL
INVENTORY_DB_URL
ORDER_DB_URL
REDIS_HOST
KAFKA_BOOTSTRAP_SERVERS
```

Sensitive values are created from `.env.local` as the `mobi-secrets` Kubernetes Secret:

```text
AUTH_DB_PASSWORD
PRODUCT_DB_PASSWORD
INVENTORY_DB_PASSWORD
ORDER_DB_PASSWORD
JWT_SECRET
INTERNAL_SERVICE_SECRET
ADMIN_BOOTSTRAP_USERNAME
ADMIN_BOOTSTRAP_PASSWORD
```

For local smoke runs, `auth-service` can bootstrap one admin user from `ADMIN_BOOTSTRAP_*` variables.

## Product API

`product-service` uses REST-style product routes:

```text
GET    /productapi/products
GET    /productapi/products/{id}
POST   /productapi/products
PUT    /productapi/products/{id}
DELETE /productapi/products/{id}
```

Product stock is intentionally not stored on the product record. Stock belongs to `inventory-service`.
Product mutations require an `ADMIN` token; product reads remain public.

## Order API

`order-service` owns order state and uses synchronous HTTP clients to validate products and reserve inventory:

```text
POST   /orderapi/orders
GET    /orderapi/orders
GET    /orderapi/orders/{id}
GET    /orderapi/users/{userId}/orders
POST   /orderapi/orders/{id}/cancel
POST   /orderapi/orders/{id}/confirm
```

Initial checkout flow:

```text
order-service
  -> product-service: fetch active product and price snapshot
  -> inventory-service: reserve stock
  -> orderdb: save order as INVENTORY_RESERVED or CANCELLED
```

Order creation supports an optional `Idempotency-Key` request header. Reusing the same key for a previously completed order returns the original order response instead of creating a duplicate order.
Order reads and commands are restricted to the order owner or an `ADMIN`; listing all orders is admin-only.
Order confirmation is idempotent after an order reaches `CONFIRMED`.

After order state changes, `order-service` writes asynchronous domain events to an outbox table and a scheduled publisher forwards them to Kafka topic `order-events`.

## Development

To test an individual service directly, use Java 21:

```bash
cd auth-service
./mvnw test
```

Each runnable service has its own Maven wrapper and Dockerfile.

## Troubleshooting

### Port already in use

kind maps local ports `80` and `443` to the ingress controller. Check the process using a port:

```bash
lsof -iTCP:80 -sTCP:LISTEN
```

### View logs

```bash
kubectl logs -n mobi deployment/api-gateway -f
kubectl logs -n mobi deployment/auth-service -f
kubectl logs -n mobi deployment/product-service -f
```

### Rebuild and redeploy

```bash
./scripts/kind-load-images.sh
./scripts/k8s-deploy.sh
```

### Reset the local cluster

```bash
kind delete cluster --name mobi
./scripts/kind-up.sh
./scripts/kind-load-images.sh
./scripts/k8s-deploy.sh
```
