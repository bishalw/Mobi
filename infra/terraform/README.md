# Terraform — Mobi Infrastructure

Provisions the infrastructure Mobi runs on. AWS is the only implemented provider;
the layout is namespaced by cloud so adding GKE or AKS means adding a sibling
directory rather than editing anything here. See
[docs/PORTABILITY.md](../../docs/PORTABILITY.md) for the contract that makes that
work.

## Structure

```
modules/
  aws/
    vpc/            VPC, subnets, NAT gateway, route tables
    eks/            EKS cluster, node group, IAM roles
    rds/            RDS MySQL, subnet group, security group
    elasticache/    ElastiCache Redis, subnet group, security group
    msk/            MSK Kafka cluster, security group
    ecr/            ECR repositories for all six services

envs/
  aws-staging/      2-AZ, t3.medium nodes, db.t3.micro, single-AZ RDS
  aws-production/   3-AZ, t3.large nodes, db.t3.small, multi-AZ RDS
```

Environment directories are named `<cloud>-<tier>`. `scripts/deploy.sh` derives
which per-cloud Helm overlay to apply from that prefix, so the naming is load
bearing.

## Prerequisites

- Terraform >= 1.6
- AWS CLI configured with sufficient permissions
- An S3 bucket named `mobi-terraform-state` for remote state

## Usage

```bash
terraform -chdir=envs/aws-staging init
terraform -chdir=envs/aws-staging plan  -var="db_password=<your-password>"
terraform -chdir=envs/aws-staging apply -var="db_password=<your-password>"
```

Then render the Helm values from the outputs and deploy. Outputs are **not**
pasted into a checked-in values file by hand:

```bash
scripts/render-values.sh aws-staging
scripts/deploy.sh aws-staging
```

## The output contract

Every env directory must emit nine outputs under fixed names —
`cluster_name`, `registry_prefix`, `db_endpoint`, `db_jdbc_params`,
`redis_endpoint`, `redis_port`, `redis_ssl_enabled`, `kafka_bootstrap`,
`kafka_security_protocol`.

`scripts/render-values.sh` reads them by name and refuses to render if any is
missing, listing all gaps at once. `envs/aws-staging/outputs.tf` is the annotated
reference copy. Extra outputs beyond the contract are fine —
`ecr_repository_urls` is one, used for image push.

## Staging vs Production

| | Staging | Production |
| --- | --- | --- |
| AZs | 2 | 3 |
| EKS nodes | 2x t3.medium | 3x t3.large |
| RDS | db.t3.micro, single-AZ | db.t3.small, multi-AZ |
| ElastiCache | cache.t3.micro | cache.t3.small |
| MSK brokers | 2x kafka.t3.small | 3x kafka.m5.large |
| RDS final snapshot | disabled | enabled |
| RDS deletion protection | disabled | enabled |

## State

Remote state is stored in S3. Each environment writes to its own key:

```
mobi-terraform-state/staging/terraform.tfstate
mobi-terraform-state/production/terraform.tfstate
```

These keys predate the `aws-` directory rename and are deliberately left
unchanged: editing a `backend` key detaches Terraform from existing state, and the
next `plan` proposes destroying and recreating everything. If you would rather the
keys match the directory names, change them and run
`terraform init -migrate-state`. A future cloud will not collide with these — its
state belongs in that provider's own backend (GCS, Azure Storage).
