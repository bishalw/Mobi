variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "db_username" {
  type    = string
  default = "mobi_admin"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "mail_domain" {
  description = <<-EOT
    Domain the app's emails come from, verified in SES. Each environment needs its own
    when they share an AWS account and region (an SES identity exists once per region),
    e.g. staging.mobi.example.com for staging and mobi.example.com for production.
  EOT
  type        = string
}

variable "route53_zone_id" {
  description = "Route 53 zone for mail_domain. Null means add module.ses dns_records at your DNS provider by hand."
  type        = string
  default     = null
}
