# ScalableRest

Spring Boot microservices demo using Eureka service discovery, Spring Cloud Config, Spring Cloud Gateway, MySQL, and Docker Compose.

## Architecture

| Service | Port | Description |
| --- | ---: | --- |
| `discovery-service` | `8761` | Eureka service registry |
| `config-server` | `8888` | Centralized Spring Cloud Config server |
| `api-gateway` | `8080` | Gateway entry point for clients |
| `auth-service` | `8082` | Authentication service |
| `product-service` | `8083` | Product API service |
| `mysql` | internal `3306` | MySQL server with separate auth/product databases |

Runtime flow:

```text
Client
  -> api-gateway
  -> auth-service / product-service

api-gateway discovers services through Eureka.
services load config from config-server.
config-server reads config from config-repo/.
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

Open the Eureka dashboard:

```text
http://localhost:8761
```

Config server checks:

```bash
curl http://localhost:8888/auth-service/default
curl http://localhost:8888/product-service/default
```

Health checks:

```bash
curl http://localhost:8761/actuator/health
curl http://localhost:8888/actuator/health
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

- Eureka dashboard loads on port `8761`.
- Config server endpoints return `200`.
- Actuator health endpoints return `{"status":"UP"}`.
- Auth route is reachable through the gateway.
- Product route may return `403` without authentication because it is protected.

## Service discovery

The gateway routes through Eureka using service IDs:

```properties
lb://AUTH-SERVICE
lb://PRODUCT-SERVICE
```

Services use an environment-overridable Eureka URL:

```properties
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
```

Docker Compose sets this to:

```text
http://discovery-service:8761/eureka/
```

## Configuration

Service configuration lives in:

```text
config-repo/
├── api-gateway.properties
├── auth-service.properties
└── product-service.properties
```

These files are safe to commit because secrets are injected through environment variables:

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

The config server location is environment-overridable:

```properties
spring.cloud.config.server.native.search-locations=${CONFIG_REPO_LOCATION:file:../config-repo}
```

Docker Compose mounts `config-repo/` into the config server container and sets:

```text
CONFIG_REPO_LOCATION=file:/config-repo
```

## Scaling locally

Because the gateway discovers service instances through Eureka, `product-service` can be scaled locally:

```bash
docker compose --env-file .env.local up --scale product-service=3 -d
```

Then check Eureka:

```text
http://localhost:8761
```

You should see multiple `PRODUCT-SERVICE` instances.

## Development

The normal local runtime is Docker Compose.

To test an individual service directly, use Java 21:

```bash
cd auth-service
./mvnw test
```

Each service has its own Maven wrapper and Dockerfile.

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
