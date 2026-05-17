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

