output "smtp_host" {
  description = "Regional SES SMTP endpoint. Port 587 with STARTTLS."
  value       = local.smtp_host
}

output "from" {
  description = "From header for every email, e.g. \"Mobi <no-reply@mobi.example.com>\"."
  value       = "${var.from_display_name} <${local.from_address}>"
}

output "smtp_username" {
  description = "SMTP user name (the IAM access key ID). Goes into the Secret as MAIL_USERNAME."
  value       = aws_iam_access_key.smtp.id
  sensitive   = true
}

output "smtp_password" {
  description = "SMTP password derived for this region. Goes into the Secret as MAIL_PASSWORD."
  value       = aws_iam_access_key.smtp.ses_smtp_password_v4
  sensitive   = true
}

output "dns_records" {
  description = "Records SES needs. Created automatically with route53_zone_id; otherwise add them by hand."
  value = concat(
    [for t in local.dkim_tokens : {
      name  = "${t}._domainkey.${var.domain}"
      type  = "CNAME"
      value = "${t}.dkim.amazonses.com"
    }],
    [
      {
        name  = local.mail_from_domain
        type  = "MX"
        value = "10 feedback-smtp.${data.aws_region.current.name}.amazonses.com"
      },
      {
        name  = local.mail_from_domain
        type  = "TXT"
        value = "v=spf1 include:amazonses.com ~all"
      },
      {
        name  = "_dmarc.${var.domain}"
        type  = "TXT"
        value = "v=DMARC1; p=none;"
      },
    ],
  )
}
