variable "name" {
  description = "Prefix for the IAM user and configuration set, e.g. mobi-staging."
  type        = string
}

variable "domain" {
  description = "Domain the emails are sent from, e.g. mobi.example.com. Verified with DKIM."
  type        = string
}

variable "from_local_part" {
  description = "Mailbox part of the From address. The full address is <from_local_part>@<domain>."
  type        = string
  default     = "no-reply"
}

variable "from_display_name" {
  description = "Display name shown next to the From address."
  type        = string
  default     = "Mobi"
}

variable "mail_from_subdomain" {
  description = <<-EOT
    Custom MAIL FROM (bounce) subdomain, so SPF aligns with the From domain for DMARC.
    "mail" gives mail.<domain>.
  EOT
  type        = string
  default     = "mail"
}

variable "route53_zone_id" {
  description = <<-EOT
    Hosted zone for var.domain. When set, the DKIM, MAIL FROM and DMARC records are created
    there. When null, nothing is written to DNS: add the records from the dns_records
    output at your DNS provider by hand.
  EOT
  type        = string
  default     = null
}

variable "create_dmarc_record" {
  description = "Create _dmarc.<domain> (p=none, report-only). Turn off if the domain already has one."
  type        = bool
  default     = true
}
