resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-rds-subnet-group"
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "this" {
  name   = "${var.name}-rds-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "this" {
  identifier              = var.name
  engine                  = "mysql"
  # 8.4 is MySQL's long-term-support line (8.0 reached end of life in April 2026). Kept on
  # the same major version as the local cluster (k8s/infra/mysql/mysql.yaml). RDS picks
  # the newest 8.4 minor and applies minor upgrades in the maintenance window.
  engine_version              = "8.4"
  allow_major_version_upgrade = true
  auto_minor_version_upgrade  = true
  instance_class          = var.instance_class
  allocated_storage       = var.allocated_storage
  # No initial database. The app needs four (authdb, productdb, inventorydb,
  # orderdb) and aws_db_instance only creates one, so they are all created by the
  # db-bootstrap Helm hook instead — see helm/mobi/templates/db-bootstrap-job.yaml.
  #
  # It cannot be done from Terraform: this instance is only reachable from the node
  # security group, so a plan running outside the VPC cannot connect. And note that
  # Hibernate could never have done it either — ddl-auto creates tables inside an
  # existing database, never the database itself.
  db_name                 = null
  username                = var.username
  password                = var.password
  db_subnet_group_name    = aws_db_subnet_group.this.name
  vpc_security_group_ids  = [aws_security_group.this.id]
  skip_final_snapshot     = var.skip_final_snapshot
  deletion_protection     = !var.skip_final_snapshot
  multi_az                = var.multi_az
  # Also what makes a major upgrade recoverable: with backups on, RDS keeps point-in-time
  # recovery across the upgrade (and blue/green needs binary logs, which this enables).
  backup_retention_period = 7
  copy_tags_to_snapshot   = true
  maintenance_window      = var.maintenance_window

  # Engine upgrades (such as 8.0 -> 8.4) go through an RDS Blue/Green deployment: a copy
  # on the new version is built and kept in sync while the current instance serves
  # traffic, then switched over in about a minute, instead of the instance being down for
  # the whole in-place upgrade. Runbook: infra/terraform/README.md, "MySQL upgrades".
  blue_green_update {
    enabled = true
  }

  tags = { Name = var.name }
}
