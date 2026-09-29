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
  engine_version          = "8.0"
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
  backup_retention_period = 7

  tags = { Name = var.name }
}
