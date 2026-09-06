output "cloudfront_domain" {
  description = "Public URL of the app."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "cloudfront_distribution_id" {
  description = "For `aws cloudfront create-invalidation`."
  value       = aws_cloudfront_distribution.main.id
}

output "frontend_bucket" {
  description = "Sync `frontend/dist/` here."
  value       = aws_s3_bucket.frontend.id
}

output "artifacts_bucket" {
  description = "Upload the backend bootJar here."
  value       = aws_s3_bucket.artifacts.id
}

output "backend_jar_s3_uri" {
  description = "Exact S3 URI the instance pulls on boot and refresh."
  value       = "s3://${aws_s3_bucket.artifacts.id}/${var.backend_jar_s3_key}"
}

output "backend_instance_id" {
  description = "For `aws ssm start-session` and `aws ssm send-command`."
  value       = aws_instance.backend.id
}

output "backend_public_dns" {
  description = "Stable EIP DNS CloudFront routes /api/* traffic to."
  value       = aws_eip.backend.public_dns
}

output "gemini_api_key_parameter" {
  description = "SSM parameter name. Set via `aws ssm put-parameter --overwrite --type SecureString`."
  value       = aws_ssm_parameter.gemini_api_key.name
}

output "fal_ai_api_key_parameter" {
  description = "SSM parameter name for the fal.ai key. Set via `aws ssm put-parameter --overwrite --type SecureString`."
  value       = aws_ssm_parameter.fal_ai_api_key.name
}

output "fal_ai_end_to_end_timeout_ms_parameter" {
  description = "SSM parameter name for the fal.ai end-to-end timeout knob (ms). Rotate via `aws ssm put-parameter --overwrite --type String` then re-run deploy-backend.sh --skip-build."
  value       = aws_ssm_parameter.fal_ai_end_to_end_timeout_ms.name
}

output "active_profile" {
  description = "Spring profile baked into the EC2 user-data. Switch by changing var.active_profile and running terraform apply + deploy-backend.sh."
  value       = var.active_profile
}

output "region" {
  description = "AWS region — exposed for deploy scripts."
  value       = var.region
}
