# ---------------------------------------------------------------------------
# Portability contract.
#
# Every env directory — on every cloud — MUST emit these nine outputs under
# exactly these names. scripts/render-values.sh consumes them by name to
# generate the Helm values, so adding a cloud becomes "write the modules, emit
# the contract" with no changes to the chart or the deploy script.
#
# See docs/PORTABILITY.md for the full contract definition.
# ---------------------------------------------------------------------------

output "cluster_name" {
  description = "Kubernetes cluster name, used to fetch credentials."
  value       = module.eks.cluster_name
}

output "db_endpoint" {
  description = "MySQL hostname. No scheme, no port."
  value       = module.rds.endpoint
}

output "db_jdbc_params" {
  description = <<-EOT
    Query string appended to every JDBC URL, including the leading "?", or an
    empty string when none is needed. RDS accepts unencrypted connections, so
    this is empty here; Azure MySQL Flexible Server needs "?sslMode=REQUIRED".
  EOT
  value       = ""
}

output "redis_endpoint" {
  description = "Redis hostname. No port."
  value       = module.elasticache.endpoint
}

output "redis_port" {
  description = "Redis port. 6379 unencrypted; TLS-only services use 6380."
  value       = module.elasticache.port
}

output "redis_ssl_enabled" {
  description = <<-EOT
    Whether the Redis client must negotiate TLS, as a "true"/"false" string.
    ElastiCache here accepts plaintext in-VPC. Azure Cache for Redis disables
    the plaintext port by default, so an Azure env must emit "true" and port 6380.
  EOT
  value       = "false"
}

output "kafka_bootstrap" {
  description = "Kafka bootstrap servers as comma-separated host:port pairs."
  value       = module.msk.bootstrap_brokers
}

output "kafka_security_protocol" {
  description = <<-EOT
    Kafka client security protocol: PLAINTEXT, SSL, SASL_PLAINTEXT, or SASL_SSL.
    This MSK cluster terminates client connections as PLAINTEXT inside the VPC
    (see modules/aws/msk encryption_in_transit). Any SASL_* value additionally
    requires KAFKA_SASL_MECHANISM in the ConfigMap and KAFKA_SASL_JAAS_CONFIG in
    the Secret, since the JAAS string carries a credential.
  EOT
  value       = "PLAINTEXT"
}

output "registry_prefix" {
  description = "Container registry host + namespace. No trailing slash."
  value       = module.ecr.registry_prefix
}

# --- Beyond the contract: AWS-specific extras -------------------------------

output "ecr_repository_urls" {
  description = "Per-service ECR repository URLs, for image build/push in CI."
  value       = module.ecr.repository_urls
}
