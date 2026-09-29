output "repository_urls" {
  description = "Per-service repository URLs, keyed by service name."
  value       = { for k, v in aws_ecr_repository.this : k => v.repository_url }
}

output "registry_prefix" {
  description = <<-EOT
    Registry host plus shared namespace, with no trailing slash and no service
    name — e.g. 111122223333.dkr.ecr.us-east-1.amazonaws.com/mobi. Consumers
    append "/<service>:<tag>". Derived from a real repository URL so it cannot
    drift from what was actually provisioned.
  EOT
  value       = dirname(aws_ecr_repository.this["api-gateway"].repository_url)
}
