# ScalableRest

Spring Boot microservices demo using Spring Cloud Gateway, MySQL, explicit service URLs, and Docker Compose.

## Architecture

| Service | Port | Description |
| --- | ---: | --- |
| `api-gateway` | `8080` | Gateway entry point for clients |
| `auth-service` | `8082` | Authentication service |
| `product-service` | `8083` | Product API service |
| `mysql` | internal `3306` | MySQL server with separate auth/product databases |

Runtime flow:

```text
Client
  -> api-gateway
  -> auth-service / product-service

api-gateway routes to services through environment-configured URLs.
services load config from local Spring Boot properties and Docker Compose environment variables.
auth-service and product-service use separate MySQL databases.
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

JWT_SECRET=replace-with-at-least-32-character-secret
JWT_EXPIRATION_MS=3600000
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
```

Gateway checks:

```bash
curl -i -X OPTIONS http://localhost:8080/auth/register
curl -i -X OPTIONS http://localhost:8080/productapi/products
```

Expected behavior:

- Actuator health endpoints return `{"status":"UP"}`.
- Auth route is reachable through the gateway.
- Product route may return `403` without authentication because it is protected.

## Service routing

The gateway routes through explicit, environment-overridable service URLs:

```properties
spring.cloud.gateway.server.webflux.routes[0].uri=${AUTH_SERVICE_URL:http://localhost:8082}
spring.cloud.gateway.server.webflux.routes[1].uri=${PRODUCT_SERVICE_URL:http://localhost:8083}
```

Docker Compose sets these to the internal Compose DNS names and container ports:

```text
AUTH_SERVICE_URL=http://auth-service:8082
PRODUCT_SERVICE_URL=http://product-service:8083
```

For future Kubernetes deployment, use the same style with Kubernetes Service DNS names.

## Configuration

Service configuration lives in each service's `src/main/resources/application.properties` and is overridden by environment variables.

Secrets and environment-specific values are injected through Docker Compose:

```properties
spring.datasource.password=${AUTH_DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

For local Docker Compose, MySQL is one container with separate service-owned databases and users:

```text
mysql
├── authdb     owned by auth_user
└── productdb  owned by product_user
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
