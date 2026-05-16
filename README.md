# ScalableRest

Spring Boot microservices demo using Spring Cloud Gateway, MySQL, explicit service URLs, and Docker Compose.

## Architecture

| Service | Port | Description |
| --- | ---: | --- |
| `api-gateway` | `8080` | Gateway entry point for clients |
| `auth-service` | `8082` | Authentication service |
| `product-service` | `8083` | Product API service |
| `inventory-service` | `8084` | Inventory and stock reservation service |
| `order-service` | `8085` | Order creation and checkout orchestration service |
| `redis` | `6379` | Redis for fast temporary state, starting with order idempotency keys |
| `kafka` | `9092` | Kafka broker for asynchronous domain events |
| `mysql` | internal `3306` | MySQL server with separate service-owned databases |

Runtime flow:

```text
Client
  -> api-gateway
  -> auth-service / product-service / inventory-service / order-service

api-gateway routes to services through environment-configured URLs.
services load config from local Spring Boot properties and Docker Compose environment variables.
auth-service, product-service, inventory-service, and order-service use separate MySQL databases.
```

## Requirements

- Docker Desktop
- Docker Compose

Optional for direct service development:

- Java 21
- Maven wrapper included in each service

## Environment setup

Copy the example environment file:

```bash
cp .env.example .env.local
```

Edit `.env.local` and set local values:

```bash
MYSQL_ROOT_PASSWORD=localdevpassword

AUTH_DB_NAME=authdb
AUTH_DB_USERNAME=auth_user
AUTH_DB_PASSWORD=replace-with-local-auth-db-password

PRODUCT_DB_NAME=productdb
PRODUCT_DB_USERNAME=product_user
PRODUCT_DB_PASSWORD=replace-with-local-product-db-password

INVENTORY_DB_NAME=inventorydb
INVENTORY_DB_USERNAME=inventory_user
INVENTORY_DB_PASSWORD=replace-with-local-inventory-db-password

ORDER_DB_NAME=orderdb
ORDER_DB_USERNAME=order_user
ORDER_DB_PASSWORD=replace-with-local-order-db-password

ORDER_IDEMPOTENCY_TTL_SECONDS=86400
ORDER_EVENTS_TOPIC=order-events
USER_EVENTS_TOPIC=user-events
INTERNAL_SERVICE_SECRET=local-internal-secret

JWT_SECRET=replace-with-at-least-32-character-secret
JWT_EXPIRATION_MS=3600000

ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=replace-with-local-admin-password
```

`.env.local` is ignored by git and should never be committed.

## Running with Docker Compose

Start the full stack:

```bash
docker compose --env-file .env.local up --build -d
```

Check container status:

```bash
docker compose --env-file .env.local ps
```

Stop the stack:

```bash
docker compose --env-file .env.local down
```

Stop the stack and remove the MySQL volume:

```bash
docker compose --env-file .env.local down -v
```

## Verification

Health checks:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

Redis check:

```bash
docker compose --env-file .env.local exec redis redis-cli ping
```

Kafka check:

```bash
docker compose --env-file .env.local exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

End-to-end smoke check:

```bash
./scripts/smoke.sh
docker compose --env-file .env.local logs --tail=200 notification-service
```

Gateway checks:

```bash
curl -i -X OPTIONS http://localhost:8080/auth/register
curl -i -X OPTIONS http://localhost:8080/productapi/products
curl -i -X OPTIONS http://localhost:8080/inventoryapi/products
curl -i -X OPTIONS http://localhost:8080/orderapi/orders
```

Expected behavior:

- Actuator health endpoints return `{"status":"UP"}`.
- Auth route is reachable through the gateway.
- Product and inventory read routes are reachable through the gateway.
- The smoke script registers a user, seeds product/inventory as the local admin, creates/confirms/cancels orders, and notification logs show welcome/order events.

## Service routing

The gateway routes through explicit, environment-overridable service URLs:

```properties
spring.cloud.gateway.server.webflux.routes[0].uri=${AUTH_SERVICE_URL:http://localhost:8082}
spring.cloud.gateway.server.webflux.routes[1].uri=${PRODUCT_SERVICE_URL:http://localhost:8083}
spring.cloud.gateway.server.webflux.routes[2].uri=${INVENTORY_SERVICE_URL:http://localhost:8084}
spring.cloud.gateway.server.webflux.routes[3].uri=${ORDER_SERVICE_URL:http://localhost:8085}
```

Docker Compose sets these to the internal Compose DNS names and container ports:

```text
AUTH_SERVICE_URL=http://auth-service:8082
PRODUCT_SERVICE_URL=http://product-service:8083
INVENTORY_SERVICE_URL=http://inventory-service:8084
ORDER_SERVICE_URL=http://order-service:8085
```

For future Kubernetes deployment, use the same style with Kubernetes Service DNS names.

## Configuration

Service configuration lives in each service's `src/main/resources/application.properties` and is overridden by environment variables.

Secrets and environment-specific values are injected through Docker Compose:

```properties
spring.datasource.password=${AUTH_DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

For local Docker smoke runs, `auth-service` can bootstrap one admin user from `ADMIN_BOOTSTRAP_*` variables. The service default is disabled; `docker-compose.yml` enables it with local defaults so the smoke script can seed products and inventory.

For local Docker Compose, MySQL is one container with separate service-owned databases and users:

```text
mysql
├── authdb     owned by auth_user
├── productdb  owned by product_user
├── inventorydb owned by inventory_user
└── orderdb     owned by order_user
```

The database setup is initialized by:

```text
docker/mysql/init/01-create-service-databases.sh
```

## Scaling locally

Docker Compose DNS provides stable service names for local development:

```text
http://auth-service:8082
http://product-service:8083
http://inventory-service:8084
http://order-service:8085
```

If a service is scaled in Docker Compose, Docker DNS may return different container IPs across lookups:

```bash
docker compose --env-file .env.local up --scale auth-service=3 -d
```

This is basic DNS round-robin, not production-grade load balancing. It is acceptable for local testing, but production scaling should be handled by Kubernetes Services or another platform-level load balancer.

For production-grade scaling later, Kubernetes Services should handle traffic across replicas while the application keeps using service URLs such as:

```text
http://product-service:8083
```

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

After order state changes, `order-service` writes asynchronous domain events to an outbox table and a scheduled publisher forwards them to Kafka topic `order-events`. Initial events include:

```text
ORDER_INVENTORY_RESERVED
ORDER_CONFIRMED
ORDER_CANCELLED
```

## Development

The normal local runtime is Docker Compose.

To test an individual service directly, use Java 21:

```bash
cd auth-service
./mvnw test
```

Each runnable service has its own Maven wrapper and Dockerfile.

## Troubleshooting

### Port already in use

Check the process using a port:

```bash
lsof -iTCP:8080 -sTCP:LISTEN
```

Stop Docker containers:

```bash
docker compose --env-file .env.local down
```

### View logs

```bash
docker compose --env-file .env.local logs -f api-gateway
docker compose --env-file .env.local logs -f auth-service
docker compose --env-file .env.local logs -f product-service
```

### Rebuild images

```bash
docker compose --env-file .env.local build --no-cache
docker compose --env-file .env.local up -d
```
