# Amazon SES for notification-service, reached over SMTP.
#
# SMTP rather than the SES API keeps the service free of any cloud SDK (docs/PORTABILITY.md):
# the app only sees MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD, the same settings it uses
# with Mailpit locally or SendGrid / Postmark on another cloud.
#
# A new SES account starts in the sandbox: it only delivers to verified addresses, at
# 200 messages a day. That is the right state for staging. Production needs a one-off
# production-access request to AWS (see infra/terraform/README.md); Terraform cannot
# do that step.

data "aws_region" "current" {}

locals {
  mail_from_domain = "${var.mail_from_subdomain}.${var.domain}"
  from_address     = "${var.from_local_part}@${var.domain}"
  manage_dns       = var.route53_zone_id != null
  dkim_tokens      = aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens
  smtp_host        = "email-smtp.${data.aws_region.current.name}.amazonaws.com"
}

# Every message goes through this set. Bounces and complaints land on SES's suppression
# list, so a dead address is not retried on the next order; that is what keeps the bounce
# rate under the threshold at which AWS pauses sending.
resource "aws_sesv2_configuration_set" "this" {
  configuration_set_name = var.name

  delivery_options {
    tls_policy = "REQUIRE"
  }

  reputation_options {
    reputation_metrics_enabled = true
  }

  sending_options {
    sending_enabled = true
  }

  suppression_options {
    suppressed_reasons = ["BOUNCE", "COMPLAINT"]
  }
}

resource "aws_sesv2_email_identity" "domain" {
  email_identity         = var.domain
  configuration_set_name = aws_sesv2_configuration_set.this.configuration_set_name
  # Easy DKIM, RSA 2048: SES generates the keys and publishes three CNAME tokens.
}

resource "aws_sesv2_email_identity_mail_from_attributes" "domain" {
  email_identity         = aws_sesv2_email_identity.domain.email_identity
  mail_from_domain       = local.mail_from_domain
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"
}

# --- DNS (only with a Route 53 zone) ------------------------------------------

resource "aws_route53_record" "dkim" {
  count   = local.manage_dns ? 3 : 0
  zone_id = var.route53_zone_id
  name    = "${local.dkim_tokens[count.index]}._domainkey.${var.domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${local.dkim_tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_route53_record" "mail_from_mx" {
  count   = local.manage_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = local.mail_from_domain
  type    = "MX"
  ttl     = 1800
  records = ["10 feedback-smtp.${data.aws_region.current.name}.amazonses.com"]
}

resource "aws_route53_record" "mail_from_spf" {
  count   = local.manage_dns ? 1 : 0
  zone_id = var.route53_zone_id
  name    = local.mail_from_domain
  type    = "TXT"
  ttl     = 1800
  records = ["v=spf1 include:amazonses.com ~all"]
}

# Report-only. Tighten to quarantine once the reports show only SES sending as the domain.
resource "aws_route53_record" "dmarc" {
  count   = local.manage_dns && var.create_dmarc_record ? 1 : 0
  zone_id = var.route53_zone_id
  name    = "_dmarc.${var.domain}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=DMARC1; p=none;"]
}

# --- SMTP credentials -----------------------------------------------------------
#
# SES SMTP authenticates with an IAM access key, the password being derived from the
# secret key (ses_smtp_password_v4). The user may only send, only as this domain and only
# through this configuration set. The key lives in Terraform state (the S3 backend), so
# treat that bucket as a secret store; rotate the key with `terraform apply -replace=module.ses.aws_iam_access_key.smtp`.

resource "aws_iam_user" "smtp" {
  name = "${var.name}-ses-smtp"
}

resource "aws_iam_user_policy" "smtp" {
  name = "send-as-${replace(var.domain, ".", "-")}"
  user = aws_iam_user.smtp.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["ses:SendRawEmail"]
      Resource = [
        aws_sesv2_email_identity.domain.arn,
        aws_sesv2_configuration_set.this.arn,
      ]
    }]
  })
}

resource "aws_iam_access_key" "smtp" {
  user = aws_iam_user.smtp.name
}
