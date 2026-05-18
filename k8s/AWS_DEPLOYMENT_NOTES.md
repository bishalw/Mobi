# AWS Deployment Notes

Infrastructure is provisioned with Terraform. See [`infra/terraform/`](../infra/terraform/README.md) for the full module structure and usage.

## Architecture

```mermaid
graph TD
    Users["Users"]
    R53["Route 53"]
    CF["CloudFront + S3\nfrontend"]
    ALB["Application Load Balancer\nHTTPS :443"]
    GW["api-gateway\nEKS pod"]
    Auth["auth-service"]
    Product["product-service"]
    Inventory["inventory-service"]
    Order["order-service"]
    Notification["notification-service"]
    RDS[("RDS MySQL\nprivate subnet")]
    Redis[("ElastiCache Redis\nprivate subnet")]
    MSK{{"MSK Kafka\nprivate subnet"}}
    ECR["ECR\nimage registry"]

    Users --> R53
    R53 --> CF
    R53 --> ALB
    ALB --> GW
    GW --> Auth
    GW --> Product
    GW --> Inventory
    GW --> Order

    Auth --> RDS
    Product --> RDS
    Inventory --> RDS
    Order --> RDS
    Order --> Redis

    Auth -->|user-events| MSK
    Order -->|order-events| MSK
    MSK --> Notification

    ECR -.->|pull images| GW
    ECR -.->|pull images| Auth
    ECR -.->|pull images| Order
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
| Kubernetes Secret from `.env.local` | AWS Secrets Manager + External Secrets Operator |

## Deploy sequence

1. **Provision infra** — `terraform apply` in `infra/terraform/envs/staging` or `production`
2. **Push images** — build each service, tag with Git SHA, push to ECR
3. **Deploy app** — fill `helm/mobi/values-aws.yaml` with Terraform outputs, then:

```bash
helm upgrade --install mobi helm/mobi \
  --namespace mobi \
  --values helm/mobi/values-aws.yaml
```

## App-level changes for production

**Ingress** — swap nginx for ALB in `values-aws.yaml`:

```yaml
ingress:
  className: alb
  annotations:
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:<region>:<account>:certificate/<id>
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
```

**Disable in-cluster dependencies** — once Terraform has provisioned RDS, ElastiCache, and MSK:

```yaml
mysql:
  enabled: false
redis:
  enabled: false
kafka:
  enabled: false
```

**JVM heap** — set explicitly to avoid OOMKilled:

```yaml
env:
  JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m"
```

**Admin bootstrap** — disable after first deploy once the admin account exists:

```yaml
config:
  ADMIN_BOOTSTRAP_ENABLED: "false"
```

**Frontend** — serve from S3 + CloudFront, not in-cluster. Build `frontend/` and upload `dist/` to an S3 bucket with CloudFront in front. API calls route to the ALB domain.
