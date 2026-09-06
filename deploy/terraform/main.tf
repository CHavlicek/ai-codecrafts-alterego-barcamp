locals {
  name = "${var.project}-${var.environment}"
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# CloudFront-managed prefix list — the only source we admit on the EC2 SG.
data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "artifacts" {
  bucket        = "${local.name}-artifacts-${random_id.bucket_suffix.hex}"
  force_destroy = false
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Placeholder value — rotate via `aws ssm put-parameter --overwrite`.
# ignore_changes keeps operator rotations from being reverted on next apply.
resource "aws_ssm_parameter" "gemini_api_key" {
  name        = "/${var.project}/${var.environment}/GEMINI_API_KEY"
  description = "Gemini API key consumed by the backend at startup (SPRING_PROFILES_ACTIVE=gemini)."
  type        = "SecureString"
  value       = "set-me-via-aws-ssm-put-parameter"

  lifecycle {
    ignore_changes = [value]
  }
}

# 016: fal.ai API key. Mirrored after gemini_api_key — the instance reads
# it on boot and on every refresh.sh run, regardless of which provider
# profile is active. When SPRING_PROFILES_ACTIVE=falai the FalAiImageGenerator
# consumes it; when SPRING_PROFILES_ACTIVE=gemini it sits unused (FR-1604).
resource "aws_ssm_parameter" "fal_ai_api_key" {
  name        = "/${var.project}/${var.environment}/FAL_AI_API_KEY"
  description = "fal.ai API key consumed by the backend at startup (SPRING_PROFILES_ACTIVE=falai)."
  type        = "SecureString"
  value       = "set-me-via-aws-ssm-put-parameter"

  lifecycle {
    ignore_changes = [value]
  }
}

# 016 FR-1614a tuning knob — non-secret, but lives in SSM so operators can
# rotate it via the AWS console without a Terraform apply. The Terraform
# default seeds the initial value; subsequent rotations via `aws ssm
# put-parameter --overwrite` are NOT reverted on re-apply (ignore_changes).
# Refresh.sh re-reads it on every run, so a console rotation + manual
# refresh.sh trigger picks up the new value without a redeploy.
resource "aws_ssm_parameter" "fal_ai_end_to_end_timeout_ms" {
  name        = "/${var.project}/${var.environment}/FAL_AI_END_TO_END_TIMEOUT_MS"
  description = "Backend's end-to-end fal.ai timeout (ms). Must stay < CF origin_read_timeout (60 s) with margin."
  type        = "String"
  value       = tostring(var.fal_ai_end_to_end_timeout_ms)

  lifecycle {
    ignore_changes = [value]
  }
}
