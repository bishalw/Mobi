# AWS Deployment Notes

The local kind setup is intentionally shaped like EKS:

```text
ingress -> api-gateway -> internal Kubernetes Services
```

The app chart should remain the main deployment surface for both local and AWS:

```bash
helm upgrade --install mobi helm/mobi \
  --namespace mobi \
  --values helm/mobi/values-aws.yaml
```

## AWS target mapping

| Local | AWS |
| --- | --- |
| kind | EKS |
| local Docker images | ECR images |
| ingress-nginx | AWS Load Balancer Controller |
| in-cluster MySQL | RDS MySQL |
| in-cluster Redis | ElastiCache Redis |
| in-cluster Kafka | MSK |
| Kubernetes Secret from `.env.local` | AWS Secrets Manager through External Secrets Operator |

## App image flow

Build each service image, tag it with an immutable version such as a Git SHA, and push it to ECR:

```text
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/api-gateway:<git-sha>
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/auth-service:<git-sha>
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/product-service:<git-sha>
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/inventory-service:<git-sha>
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/order-service:<git-sha>
<aws-account-id>.dkr.ecr.<region>.amazonaws.com/mobi/notification-service:<git-sha>
```

Then set `image.tag` in `helm/mobi/values-aws.yaml`.

## Config and secrets

Non-sensitive runtime values belong in Helm values and render into the app ConfigMap:

```text
AUTH_DB_URL
PRODUCT_DB_URL
INVENTORY_DB_URL
ORDER_DB_URL
REDIS_HOST
KAFKA_BOOTSTRAP_SERVERS
```

Sensitive values should stay in a Kubernetes Secret named `mobi-secrets`:

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

On AWS, use External Secrets Operator to sync those keys from AWS Secrets Manager into the same Kubernetes Secret name. The pod manifests do not need to change.

## Switching from in-cluster MySQL to RDS

The app services read database connections entirely from env vars — no hardcoded connection strings in the Java code. Swapping to RDS is a config-only change.

### What to provision on AWS

- One RDS MySQL instance (recommended: `db.t3.micro` for staging, `db.t3.medium`+ for production)
- Four databases on that instance, one per service:

```text
authdb
productdb
inventorydb
orderdb
```

- One DB user per service with access scoped to its own database only
- The RDS instance should be in a private subnet, accessible only from the EKS node security group

### What to change in values-aws.yaml

Replace the local DB URLs with RDS endpoint URLs:

```yaml
config:
  AUTH_DB_URL: jdbc:mysql://<rds-endpoint>:3306/authdb
  PRODUCT_DB_URL: jdbc:mysql://<rds-endpoint>:3306/productdb
  INVENTORY_DB_URL: jdbc:mysql://<rds-endpoint>:3306/inventorydb
  ORDER_DB_URL: jdbc:mysql://<rds-endpoint>:3306/orderdb
```

Disable the in-cluster MySQL dependency:

```yaml
mysql:
  enabled: false
```

Store the four DB passwords in AWS Secrets Manager and sync them into `mobi-secrets` via External Secrets Operator — the pod specs do not need to change.

### What does not change

- All Java service code
- The Helm chart structure
- The Kubernetes Secret key names (`AUTH_DB_PASSWORD`, etc.)
- The ConfigMap key names (`AUTH_DB_URL`, etc.)

## Ingress

Local uses nginx:

```yaml
ingress:
  className: nginx
```

AWS uses ALB:

```yaml
ingress:
  className: alb
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
```

The app should expose only `api-gateway`; service-to-service traffic stays inside Kubernetes Services.

## Networking and VPC

All AWS resources should live in the same VPC:

- EKS cluster in private subnets (nodes not publicly reachable)
- RDS, ElastiCache, and MSK in private subnets
- ALB in public subnets (the only public entry point)
- Security groups: EKS nodes can reach RDS/ElastiCache/MSK; nothing else can

The frontend should be served from **S3 + CloudFront**, not from inside the cluster. Build the React app and upload the `dist/` output to an S3 bucket with CloudFront in front. API calls go to the ALB domain.

## ElastiCache (Redis swap)

Same pattern as RDS — config-only change:

```yaml
config:
  REDIS_HOST: <elasticache-endpoint>

redis:
  enabled: false
```

Redis is used only for order idempotency keys. ElastiCache in-cluster mode (single node) is sufficient unless you need cross-AZ failover.

## MSK (Kafka swap)

```yaml
config:
  KAFKA_BOOTSTRAP_SERVERS: <msk-broker-1>:9092,<msk-broker-2>:9092

kafka:
  enabled: false
```

MSK requires IAM or SASL authentication. If using SASL, each service's `application.properties` will need:

```properties
spring.kafka.properties.security.protocol=SASL_SSL
spring.kafka.properties.sasl.mechanism=AWS_MSK_IAM
```

The MSK IAM auth library must be added to each service's `pom.xml`.

## Resource sizing

Minimum viable EKS setup for production:

| Component | Recommendation |
| --- | --- |
| EKS node group | 2x `t3.medium` (auto-scaling 2–4) |
| RDS | `db.t3.micro` (staging), `db.t3.small`+ (prod) |
| ElastiCache | `cache.t3.micro` single node |
| MSK | `kafka.t3.small` 2-broker cluster |

Set JVM heap explicitly per service to avoid OOMKilled:

```yaml
env:
  JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m"
```

## CI/CD pipeline

On merge to `main`:

1. Run `./mvnw test` for each service
2. Build Docker images tagged with the Git SHA
3. Push to ECR
4. Run `helm upgrade` against EKS with the new image tags

GitHub Actions is the natural fit. Required secrets:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
AWS_ACCOUNT_ID
KUBECONFIG   # base64-encoded EKS kubeconfig
```

## ADMIN_BOOTSTRAP in production

`ADMIN_BOOTSTRAP_ENABLED` should be `false` after the first deploy. The admin user is created on first startup — leaving bootstrap enabled permanently is a security risk if the secret ever rotates to a weak value.

Set it to `false` in `values-aws.yaml` after confirming the admin account exists:

```yaml
config:
  ADMIN_BOOTSTRAP_ENABLED: "false"
```

## TLS

ALB handles TLS termination. Request a certificate in **AWS Certificate Manager** for your domain and reference it in the ingress annotations:

```yaml
ingress:
  className: alb
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<region>:<account>:certificate/<id>
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
```

Traffic inside the cluster (service-to-service) stays plain HTTP — TLS is only needed at the public boundary.

