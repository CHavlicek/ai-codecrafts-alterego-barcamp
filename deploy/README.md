# AWS deploy — Terraform

Single-region POC (`eu-north-1`) wiring the existing app onto:

- **S3 + CloudFront** for the React bundle (OAC, SPA fallback via 403/404 -> `/index.html`).
- **EC2** (Amazon Linux 2023, `t3.small`) runs the Spring Boot bootJar under `systemd`. No Docker.
- CloudFront routes `/api/*` to EC2 over HTTP. The instance's SG admits only CloudFront's origin-facing managed prefix list — no public SSH, no direct viewer access.
- `GEMINI_API_KEY` and `FAL_AI_API_KEY` live in **SSM Parameter Store** (SecureString). The instance reads both on boot and on every `refresh.sh` run, regardless of which provider profile is active. Provider selection is the `active_profile` Terraform variable (defaults to `gemini`).
- No Route 53 / ACM / ALB / custom domain — uses the default `*.cloudfront.net` hostname. Swap in a real domain later by adding `aliases`, an ACM cert in `us-east-1`, and Route 53 records.

## Layout

```
deploy/
  terraform/
    versions.tf        providers.tf    variables.tf
    main.tf            -- VPC/subnet data, artifacts S3 bucket, Gemini SSM param
    backend.tf         -- IAM, SG, EC2, EIP, user-data
    frontend.tf        -- S3 + CloudFront + OAC
    outputs.tf
    user_data.sh.tftpl -- cloud-init: install Java, install systemd unit, install refresh.sh
  scripts/
    deploy-backend.sh  -- bootJar -> S3 -> SSM send-command refresh
    deploy-frontend.sh -- vite build -> S3 sync -> CloudFront invalidation
```

## Prereqs

- Terraform >= 1.6
- AWS CLI v2, authenticated with rights to create VPC/EC2/S3/CloudFront/IAM/SSM
- Node 20+ and a JDK 21 + Gradle on PATH (the repo intentionally does not commit the Gradle wrapper — see `.gitignore`)

## Provision

```bash
cd deploy/terraform
terraform init
terraform apply
```

~15–20 min end-to-end; CloudFront provisioning is the long tail.

## Set the provider keys

```bash
# Gemini key (default profile — used when var.active_profile=gemini)
aws ssm put-parameter \
  --name "$(terraform -chdir=deploy/terraform output -raw gemini_api_key_parameter)" \
  --value "YOUR-REAL-GEMINI-KEY" \
  --type SecureString --overwrite \
  --region eu-north-1

# fal.ai key (used when var.active_profile=falai)
aws ssm put-parameter \
  --name "$(terraform -chdir=deploy/terraform output -raw fal_ai_api_key_parameter)" \
  --value "YOUR-REAL-FAL-AI-KEY" \
  --type SecureString --overwrite \
  --region eu-north-1
```

## Switching the active provider

The Spring profile baked into the EC2 user-data is controlled by `var.active_profile`. Switching providers requires a Terraform apply (the change is in `/etc/aiavatar.env` written by `refresh.sh`, so a re-run of `refresh.sh` is also needed):

```bash
# Edit deploy/terraform/terraform.tfvars (or pass -var on the CLI):
#   active_profile = "falai"
terraform -chdir=deploy/terraform apply
./deploy/scripts/deploy-backend.sh --skip-build   # re-runs refresh.sh on the instance
```

Activating both `gemini` AND `falai` simultaneously is rejected at startup (016 FR-1605 — `ProviderProfileGuard` throws). The `active_profile` Terraform variable validates against `{default, gemini, falai}` so you can't apply an ambiguous value.

## First deploy

```bash
./deploy/scripts/deploy-backend.sh    # builds, uploads jar, triggers refresh
./deploy/scripts/deploy-frontend.sh   # builds, syncs, invalidates
```

## Open

```bash
open "$(terraform -chdir=deploy/terraform output -raw cloudfront_domain)"
```

## Shell into the instance (no SSH)

```bash
aws ssm start-session \
  --target "$(terraform -chdir=deploy/terraform output -raw backend_instance_id)" \
  --region eu-north-1
```

Service logs:

```bash
journalctl -u aiavatar -f
```

## Teardown

```bash
aws s3 rm "s3://$(terraform -chdir=deploy/terraform output -raw frontend_bucket)" --recursive
aws s3 rm "s3://$(terraform -chdir=deploy/terraform output -raw artifacts_bucket)" --recursive
terraform -chdir=deploy/terraform destroy
```

CloudFront deletion runs ~15 min — it's disabled first, then deleted. Buckets are `force_destroy = false`; empty them first (above) or flip the flag and re-apply before `destroy`.

## fal.ai timeout envelope

The 016 fal.ai integration introduces a longer-than-default request lifecycle (`nano-banana-pro/edit` averages 30–60 s). Three timeouts interact:

| Layer | Knob | Default | Max without quota request |
|---|---|---|---|
| CloudFront origin → EC2 | `origin_read_timeout` (frontend.tf) | **60 s** (set explicitly) | **60 s** |
| Backend end-to-end fal.ai exchange | SSM param `FAL_AI_END_TO_END_TIMEOUT_MS` (FR-1614a) | **55_000 ms** (Terraform default) | n/a |
| Per-step fal.ai HTTP timeouts | `FAL_AI_{SUBMIT,POLL,FETCH}_TIMEOUT_MS` (compile-time defaults) | 8 / 5 / 10 s | n/a |

`FAL_AI_END_TO_END_TIMEOUT_MS` MUST stay **below** CloudFront's `origin_read_timeout` with margin. Setting the backend higher than CF's read timeout means CF gives up while the backend is still legitimately waiting on fal.ai — the user gets the FE-side `FALLBACK / Even when the robots sleep.` SVG instead of a real provider response or the backend's framed fallback.

If you need a longer end-to-end budget than 55 s in prod, open an AWS Support quota-increase ticket for CloudFront's origin-read-timeout (up to 180 s); then bump `origin_read_timeout` in `frontend.tf` AND raise both `var.fal_ai_end_to_end_timeout_ms`'s validation upper bound and the SSM value in lockstep.

### Rotating the timeout without a Terraform apply

```bash
# Set the new value (ms) — must be ≤ 55_000 with the current CF setting.
aws ssm put-parameter \
  --name "$(terraform -chdir=deploy/terraform output -raw fal_ai_end_to_end_timeout_ms_parameter)" \
  --value "55000" \
  --type String --overwrite \
  --region eu-north-1

# Trigger /opt/aiavatar/refresh.sh so the new value lands in /etc/aiavatar.env
# and the systemd service restarts. No JAR rebuild needed.
./deploy/scripts/deploy-backend.sh --skip-build
```

The Terraform resource carries `lifecycle { ignore_changes = [value] }`, so a console rotation is preserved across future `terraform apply` runs (the initial value comes from `var.fal_ai_end_to_end_timeout_ms` at first apply only).

To rotate the **initial-apply default** instead — e.g. you want 50_000 to be the value any new environment starts with — edit `deploy/terraform/terraform.tfvars` (or the variable's default in `variables.tf`) and `terraform apply`. That writes the new value only on first apply per environment; existing environments keep whatever's already in SSM unless you also `aws ssm put-parameter --overwrite`.

## Caveats worth knowing

- **State is local.** Fine solo; move to S3 + DynamoDB backend before a second person touches this.
- **CloudFront -> EC2 is HTTP.** Safe for POC because (a) viewer-side is always HTTPS and (b) the SG admits only the CloudFront prefix list. For production, put an ALB with ACM in front of EC2 and set `origin_protocol_policy = "https-only"`.
- **Spring CORS stays off in prod.** `WebConfig.java` gates CORS on `@Profile("default")`; production runs `SPRING_PROFILES_ACTIVE=gemini`, and CloudFront delivers requests same-origin. Don't enable CORS unless you also stop proxying `/api/*` through CloudFront.
- **No autoscaling, no ALB, no alarms.** POC scope.
- **`.env.local` in the repo root holds a live dev Gemini key.** Rotate it before anything else and keep the prod key only in SSM.
