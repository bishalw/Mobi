# Terraform — Mobi AWS Infrastructure

Provisions all AWS infrastructure for the Mobi platform.

## Structure

```
modules/
  vpc/            VPC, subnets, NAT gateway, route tables
  eks/            EKS cluster, node group, IAM roles
  rds/            RDS MySQL, subnet group, security group
  elasticache/    ElastiCache Redis, subnet group, security group
  msk/            MSK Kafka cluster, security group
  ecr/            ECR repositories for all six services

envs/
  staging/        2-AZ, t3.medium nodes, db.t3.micro, single-AZ RDS
  production/     3-AZ, t3.large nodes, db.t3.small, multi-AZ RDS
```

## Prerequisites

- Terraform >= 1.6
- AWS CLI configured with sufficient permissions
- An S3 bucket named `mobi-terraform-state` for remote state

## Usage

```bash
cd envs/staging

terraform init
terraform plan -var="db_password=<your-password>"
terraform apply -var="db_password=<your-password>"
```

After apply, wire the outputs into `helm/mobi/values-aws.yaml`:

```bash
terraform output rds_endpoint
terraform output redis_endpoint
terraform output kafka_bootstrap
```

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
