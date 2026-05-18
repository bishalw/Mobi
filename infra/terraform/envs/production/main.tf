terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket = "mobi-terraform-state"
    key    = "production/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}

module "vpc" {
  source = "../../modules/vpc"

  name            = "mobi-production"
  cidr            = "10.1.0.0/16"
  azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  public_subnets  = ["10.1.1.0/24", "10.1.2.0/24", "10.1.3.0/24"]
  private_subnets = ["10.1.11.0/24", "10.1.12.0/24", "10.1.13.0/24"]
}

module "ecr" {
  source = "../../modules/ecr"
}

module "eks" {
  source = "../../modules/eks"

  name               = "mobi-production"
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  node_instance_type = "t3.large"
  node_desired       = 3
  node_min           = 3
  node_max           = 6
}

module "rds" {
  source = "../../modules/rds"

  name                       = "mobi-production"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  instance_class             = "db.t3.small"
  username                   = var.db_username
  password                   = var.db_password
  multi_az                   = true
  skip_final_snapshot        = false
}

module "elasticache" {
  source = "../../modules/elasticache"

  name                       = "mobi-production"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  node_type                  = "cache.t3.small"
}

module "msk" {
  source = "../../modules/msk"

  name                       = "mobi-production"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  broker_count               = 3
  broker_instance_type       = "kafka.m5.large"
}
