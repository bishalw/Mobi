# Portability

Mobi targets AWS EKS today. This document describes how the deployment is
structured so that moving to GKE or AKS is bounded, mechanical work rather than a
rewrite — and exactly what it costs.

## Where cloud coupling is allowed to live

The design rule is: **push per-cloud difference to the edges, and make the edge
generated data rather than hand-edited code.**

| Layer | Cloud-aware? |
| --- | --- |
| Java services | **No.** No cloud SDK in any service. Databases, cache, and broker are reached over MySQL / RESP / Kafka wire protocols, configured entirely by env var. |
| `helm/mobi/templates/` | **No.** Vanilla Deployment, Service, Ingress, ConfigMap. Renders unchanged for kind, EKS, GKE, and AKS. |
| `helm/mobi/values.yaml` | **No.** Structure and local defaults. Ingress *routes* live here — they are application facts. |
| `helm/mobi/values-cloud.yaml` | **No.** Settings that differ between laptop and managed infrastructure but not between clouds. |
| `helm/mobi/values-<cloud>.yaml` | **Yes**, and this is the only place it is checked in. Ingress flavour and cloud CRDs. ~20 lines. |
| `helm/mobi/values-<env>.generated.yaml` | **Yes**, but generated from Terraform and gitignored. Never edited. |
| `infra/terraform/modules/<cloud>/` | **Yes**, entirely. Irreducible. |

Deliberately *not* abstracted: the Terraform modules. A single `modules/cluster`
taking `cloud = "aws"` would become the union of three providers' argument
surfaces and leak anyway. Three honest module trees behind one uniform output
contract is the cheaper trade.

## The output contract

Every environment directory, on every cloud, must emit these nine outputs under
exactly these names. `scripts/render-values.sh` consumes them by name, which is
what makes adding a cloud require no change to the chart or the deploy script.

| Output | Meaning |
| --- | --- |
| `cluster_name` | Kubernetes cluster name, for fetching credentials. |
| `registry_prefix` | Registry host + namespace, no trailing slash. Consumers append `/<service>:<tag>`. |
| `db_endpoint` | MySQL hostname. No scheme, no port. |
| `db_jdbc_params` | Query string appended to every JDBC URL, including the leading `?`, or empty. |
| `redis_endpoint` | Redis hostname. No port. |
| `redis_port` | Redis port. `6379` plaintext, `6380` where TLS is mandatory. |
| `redis_ssl_enabled` | `"true"` / `"false"` string. |
| `kafka_bootstrap` | Comma-separated `host:port` pairs. |
| `kafka_security_protocol` | `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL`. |

`infra/terraform/envs/aws-staging/outputs.tf` is the annotated reference copy.
Envs may emit additional outputs beyond the contract; `ecr_repository_urls` is
one such AWS-specific extra, used for image push.

Rendering fails loudly and lists every missing name at once if an env does not
satisfy the contract, so standing up a new cloud is one error message rather
than nine rounds of fix-one-and-rerun.

## Deploying

```bash
# local kind cluster — also installs MySQL, Redis, and Kafka in-cluster
scripts/deploy.sh local

# any managed environment
terraform -chdir=infra/terraform/envs/aws-staging apply
scripts/render-values.sh aws-staging          # Terraform outputs -> values
scripts/deploy.sh aws-staging
```

Values are layered lowest precedence first:

```
values.yaml  ->  values-local.yaml                                   (local)
values.yaml  ->  values-cloud.yaml  ->  values-<cloud>.yaml  ->  values-<env>.generated.yaml
```

Endpoints, registry paths, and image tags are never hand-edited. The image tag
defaults to the short commit SHA, so a deploy is always traceable to a revision —
and ECR repositories are `IMMUTABLE`, which would reject a moving tag anyway.

## Database bootstrap

A managed MySQL instance provisions exactly one database; the app needs four —
`authdb`, `productdb`, `inventorydb`, `orderdb` — each with its own user. Locally
`k8s/infra/mysql/initdb-configmap.yaml` handles this inside the Bitnami MySQL
bootstrap. That file cannot run against a managed instance, so cloud environments use
a `pre-install,pre-upgrade` Helm hook instead:
`helm/mobi/templates/db-bootstrap-job.yaml`.

Two constraints drove that choice:

- **It must run inside the cluster.** A managed instance is reachable only from the
  node security group, so Terraform running on a laptop or in CI cannot connect to
  issue `CREATE DATABASE`. Provider-specific Terraform resources for this exist but
  need network reachability that the private-subnet design deliberately denies.
- **It must not be provider-specific.** One Helm hook running the `mysql` client works
  identically against RDS, Cloud SQL, and Azure MySQL — nothing to reimplement per
  cloud, which is the same reasoning behind External Secrets Operator.

Neither Hibernate nor Flyway can substitute for it. `ddl-auto` creates tables *inside*
an existing database and Flyway migrates *within* one; neither creates the database.

The Job reads the instance's master credentials from `MYSQL_ADMIN_USERNAME` and
`MYSQL_ADMIN_PASSWORD` in the Secret, and each service's password from
`<PREFIX>_DB_PASSWORD`. It is idempotent — `CREATE ... IF NOT EXISTS` plus
`ALTER USER` — so re-running on every upgrade also makes the Secret authoritative for
passwords: rotate the Secret, redeploy, and the database users follow.
`scripts/deploy.sh` checks for the admin keys before starting the release rather than
letting the hook fail halfway through.

## Adding a cloud

1. Write `infra/terraform/modules/<cloud>/…` and `infra/terraform/envs/<cloud>-staging/`.
   Emit the nine contract outputs. This is the real work; budget 2–4 days.
2. Write `helm/mobi/values-<cloud>.yaml` — ingress flavour and any cloud CRDs.
   Around 20 lines. Use `extraManifests` for CRDs so `templates/` stays vanilla.
3. Populate the `mobi-secrets` Secret in the cluster from that provider's secret
   manager.
4. `scripts/render-values.sh <env> && scripts/deploy.sh <env>`. No changes to the
   chart, the scripts, or the services.

Nothing in step 4 is provider-aware, which is the point of steps 1–3.

## Service mapping

| Concern | AWS | GCP | Azure |
| --- | --- | --- | --- |
| Cluster | EKS | GKE | AKS |
| Registry | ECR | Artifact Registry | ACR |
| MySQL | RDS | Cloud SQL | Database for MySQL Flexible Server |
| Redis | ElastiCache | Memorystore | Cache for Redis |
| Kafka | MSK | Managed Service for Apache Kafka | **none** — Event Hubs Kafka endpoint, or Confluent Cloud |
| Ingress | ALB via AWS LB Controller | GCE ingress | ingress-nginx (recommended) or AGIC |
| Secrets | Secrets Manager | Secret Manager | Key Vault |
| Pod identity | IRSA | Workload Identity | Workload Identity |
| Registry login | `aws ecr get-login-password` | `gcloud auth configure-docker` | `az acr login` |
| Kubeconfig | `aws eks update-kubeconfig` | `gcloud container clusters get-credentials` | `az aks get-credentials` |

## Known per-cloud gotchas

These are the traps that cost real debugging time. Each is handled by the
contract or an overlay, but they explain *why* those knobs exist.

**GKE ignores the health check path.** There is no GCE equivalent of
`alb.ingress.kubernetes.io/healthcheck-path`. Without a `BackendConfig` the load
balancer probes `/`, gets a 404 from api-gateway, and marks every backend
UNHEALTHY — nothing in the pod logs explains it. Handled in `values-gcp.yaml`.

**Azure has no managed Kafka.** The Event Hubs Kafka-compatible endpoint is the
usual substitute and, unlike MSK, refuses unauthenticated connections. That is
the only gap that reaches into application config: the services read
`KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, and
`KAFKA_SASL_JAAS_CONFIG`, defaulting to PLAINTEXT so local and MSK are unaffected.
The JAAS string carries a credential, so it must come from the **Secret**, and
every Kafka client service then needs `secretEnv: true` — `values-azure.yaml`
flips notification-service, which needs no other secret.

**Azure MySQL requires TLS.** Flexible Server rejects unencrypted connections,
which surfaces as an opaque connection failure. Covered by `db_jdbc_params`
emitting `?sslMode=REQUIRED`.

**Azure Cache for Redis disables the plaintext port.** Needs port 6380 and TLS.
Covered by `redis_port` and `redis_ssl_enabled`.

**Default StorageClass names differ** (`gp2`/`gp3`, `standard-rwo`/`premium-rwo`,
`managed-csi`). The in-cluster Bitnami charts under `k8s/infra/` deliberately pin
no `storageClass`, so they inherit each cluster's default. Those charts are local
only; on a cloud these are managed services.

## Open items

- **Secrets are not yet automated on cloud.** `scripts/deploy.sh` requires the
  `mobi-secrets` Secret to already exist for cloud environments and refuses to
  copy `.env.local` into a shared cluster. The intended fix is External Secrets
  Operator, which is the highest-leverage remaining step: `ExternalSecret`
  manifests are identical across providers, and only the `SecretStore` block
  differs (`aws:` / `gcpsm:` / `azurekv:`).
- **No CI.** When adding it, keep the cloud login as the only per-cloud step,
  then an identical `docker buildx build --push $REGISTRY_PREFIX/<service>:$SHA`.
- **Frontend.** `k8s/AWS_DEPLOYMENT_NOTES.md` describes S3 + CloudFront. Since
  `frontend/` already ships a Dockerfile and nginx config, serving it in-cluster
  behind the same ingress removes a per-cloud CDN module entirely.
- **GCP and Azure Terraform modules are not written.** Only the seams exist. The
  `values-gcp.yaml` and `values-azure.yaml` overlays are in place and render
  correctly, but no `envs/gcp-*` or `envs/azure-*` directory has been authored.
