# ---------------------------------------------------------------------------
# Portability contract. See envs/aws-staging/outputs.tf for the annotated
# reference copy and docs/PORTABILITY.md for the contract definition.
#
# Every env directory — on every cloud — MUST emit these eleven outputs under
# exactly these names.
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
  description = "JDBC query string including the leading \"?\", or empty."
  value       = ""
}

output "redis_endpoint" {
  description = "Redis hostname. No port."
  value       = module.elasticache.endpoint
}

output "redis_port" {
  description = "Redis port."
  value       = module.elasticache.port
}

output "redis_ssl_enabled" {
  description = "Whether the Redis client must negotiate TLS, as \"true\"/\"false\"."
  value       = "false"
}

output "kafka_bootstrap" {
  description = "Kafka bootstrap servers as comma-separated host:port pairs."
  value       = module.msk.bootstrap_brokers
}

output "kafka_security_protocol" {
  description = "PLAINTEXT, SSL, SASL_PLAINTEXT, or SASL_SSL."
  value       = "PLAINTEXT"
}

output "registry_prefix" {
  description = "Container registry host + namespace. No trailing slash."
  value       = module.ecr.registry_prefix
}

output "mail_host" {
  description = <<-EOT
    SMTP relay hostname for notification-service. Port 587 with STARTTLS and
    authentication is assumed on every cloud (values-cloud.yaml); the user name and
    password go into the Secret as MAIL_USERNAME / MAIL_PASSWORD.
  EOT
  value       = module.ses.smtp_host
}

output "mail_from" {
  description = "From header for every email, e.g. \"Mobi <no-reply@mobi.example.com>\"."
  value       = module.ses.from
}

# --- Beyond the contract: AWS-specific extras -------------------------------

output "ecr_repository_urls" {
  description = "Per-service ECR repository URLs, for image build/push in CI."
  value       = module.ecr.repository_urls
}

output "ses_smtp_username" {
  description = "MAIL_USERNAME for the Secret: terraform output -raw ses_smtp_username"
  value       = module.ses.smtp_username
  sensitive   = true
}

output "ses_smtp_password" {
  description = "MAIL_PASSWORD for the Secret: terraform output -raw ses_smtp_password"
  value       = module.ses.smtp_password
  sensitive   = true
}

output "ses_dns_records" {
  description = "DNS records SES needs; already created when route53_zone_id is set."
  value       = module.ses.dns_records
}
