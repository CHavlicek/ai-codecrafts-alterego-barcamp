variable "region" {
  description = "AWS region for EC2, S3, and all regional resources."
  type        = string
  default     = "eu-north-1"
}

variable "project" {
  description = "Short project tag used as a name prefix."
  type        = string
  default     = "aiavatar"
}

variable "environment" {
  description = "Environment name used as a name suffix."
  type        = string
  default     = "prod"
}

variable "instance_type" {
  description = "EC2 instance type for the backend."
  type        = string
  default     = "t3.small"
}

variable "backend_jar_s3_key" {
  description = "S3 key inside the artifacts bucket where the backend bootJar lives."
  type        = string
  default     = "backend/app.jar"
}

# 016: image-generation provider chosen by Spring profile (FR-1602). Default
# stays on `gemini` to preserve current prod behaviour. Switching to `falai`
# requires (a) populating the FAL_AI_API_KEY SSM param and (b) a terraform
# apply (the value gets baked into /opt/aiavatar/refresh.sh's heredoc, so a
# redeploy / refresh.sh re-run picks it up).
#
# Activating both `gemini` AND `falai` is forbidden by ProviderProfileGuard
# (016 FR-1605) — set this to a single profile name.
variable "active_profile" {
  description = "Spring profile chosen for the backend. Must be one of {default, gemini, falai}."
  type        = string
  default     = "gemini"
  validation {
    condition     = contains(["default", "gemini", "falai"], var.active_profile)
    error_message = "active_profile must be one of: default, gemini, falai."
  }
}

# 027: SMTP credentials for the outbound-email seam. These are SSM parameter
# ARNs the backend EC2 reads at boot / refresh time via its IAM role; the
# resolved values land in /etc/aiavatar.env as SPRING_MAIL_USERNAME and
# SPRING_MAIL_PASSWORD. The defaults point at the existing shared mailbox
# under the ai-closing-letter project's namespace in this account; override
# per-environment if a different mailbox is desired. EmailConfigured's
# 4-input AND-gate (FR-2706) requires both to be non-blank at startup,
# otherwise POST /api/v1/alter-egos/email replies with the typed 503
# "not-configured" problem detail.
variable "smtp_username_ssm_arn" {
  description = "SSM parameter ARN holding the SMTP mailbox username (the full email address that authenticates to PrivateEmail)."
  type        = string
  default     = "arn:aws:ssm:eu-north-1:691205628696:parameter/ai-closing-letter/prod/email/smtp/username"
}

variable "smtp_password_ssm_arn" {
  description = "SSM parameter ARN holding the SMTP mailbox password (SecureString)."
  type        = string
  default     = "arn:aws:ssm:eu-north-1:691205628696:parameter/ai-closing-letter/prod/email/smtp/password"
}

variable "email_from_override" {
  description = "Optional explicit From / sender header. Blank → /etc/aiavatar.env sets AIAVATAR_EMAIL_FROM = SPRING_MAIL_USERNAME at refresh.sh time (PrivateEmail enforces From-alignment with the authenticated mailbox per 027 quickstart §4)."
  type        = string
  default     = ""
}

# 016 FR-1614a: end-to-end wall-clock cap (ms) the backend spends on a single
# fal.ai exchange. The spec default is 30_000 ms; in prod fal.ai's
# `nano-banana-pro/edit` averages 30–60 s and the spec-default falls back too
# eagerly. CloudFront's origin_read_timeout is set to 60 s in `frontend.tf`,
# so this MUST stay BELOW 60 s with margin (recommended ≤ 55_000 ms) — going
# higher means CF 504s the upstream while the backend is still legitimately
# waiting on fal.ai, and the user gets the FE-side SVG fallback. Going past
# 55_000 ms requires opening an AWS Support quota-increase ticket for the
# CloudFront origin-read-timeout (max 180 s) AND raising it in `frontend.tf`.
variable "fal_ai_end_to_end_timeout_ms" {
  description = "Backend's end-to-end fal.ai timeout (ms). MUST be < CloudFront origin_read_timeout (60 s) with a 5+ s margin."
  type        = number
  default     = 55000
  validation {
    condition     = var.fal_ai_end_to_end_timeout_ms >= 5000 && var.fal_ai_end_to_end_timeout_ms <= 55000
    error_message = "fal_ai_end_to_end_timeout_ms must be in [5000, 55000] to fit under CloudFront's 60 s origin_read_timeout."
  }
}
