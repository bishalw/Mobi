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
    key    = "staging/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}

module "vpc" {
  source = "../../modules/aws/vpc"

  name            = "mobi-staging"
  cidr            = "10.0.0.0/16"
  azs             = ["${var.aws_region}a", "${var.aws_region}b"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.11.0/24", "10.0.12.0/24"]
}

module "ecr" {
  source = "../../modules/aws/ecr"
}

module "eks" {
  source = "../../modules/aws/eks"

  name               = "mobi-staging"
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  node_instance_type = "t3.medium"
  node_desired       = 2
  node_min           = 2
  node_max           = 4
}

module "rds" {
  source = "../../modules/aws/rds"

  name                       = "mobi-staging"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  instance_class             = "db.t3.micro"
  username                   = var.db_username
  password                   = var.db_password
  skip_final_snapshot        = true
}

module "elasticache" {
  source = "../../modules/aws/elasticache"

  name                       = "mobi-staging"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  node_type                  = "cache.t3.micro"
}

module "msk" {
  source = "../../modules/aws/msk"

  name                       = "mobi-staging"
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  broker_count               = 2
  broker_instance_type       = "kafka.t3.small"
}
