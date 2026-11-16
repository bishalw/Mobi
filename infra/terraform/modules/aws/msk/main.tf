resource "aws_security_group" "this" {
  name   = "${var.name}-msk-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 9092
    to_port         = 9092
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

# MSK's default configuration has auto.create.topics.enable=false, and no service
# declares its topics, so without this nothing could publish. Topics are created on first
# use with these defaults (one partition keeps each aggregate's events in order, as
# locally; replication follows the broker count).
resource "aws_msk_configuration" "this" {
  name           = "${var.name}-config"
  kafka_versions = ["3.5.1"]

  server_properties = <<-PROPERTIES
    auto.create.topics.enable=true
    num.partitions=1
    default.replication.factor=${min(var.broker_count, 3)}
    min.insync.replicas=${var.broker_count >= 3 ? 2 : 1}
  PROPERTIES
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = "3.5.1"
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.this.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_storage_gb
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
      in_cluster    = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }
}
