#!/usr/bin/env bash
# Build the Vite bundle, sync to S3 with correct cache headers, invalidate index.html.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/deploy/terraform"

cd "$REPO_ROOT/frontend"
npm ci
npm run build

BUCKET=$(terraform -chdir="$TF_DIR" output -raw frontend_bucket)
DIST_ID=$(terraform -chdir="$TF_DIR" output -raw cloudfront_distribution_id)
REGION=$(terraform -chdir="$TF_DIR" output -raw region)

echo "==> Syncing hashed assets (1y immutable cache)"
aws s3 sync "$REPO_ROOT/frontend/dist/" "s3://$BUCKET/" \
  --delete \
  --exclude "index.html" \
  --cache-control "public, max-age=31536000, immutable" \
  --region "$REGION"

echo "==> Uploading index.html (no-cache)"
aws s3 cp "$REPO_ROOT/frontend/dist/index.html" "s3://$BUCKET/index.html" \
  --cache-control "no-cache" \
  --content-type "text/html" \
  --region "$REGION"

echo "==> Invalidating /index.html in CloudFront $DIST_ID"
aws cloudfront create-invalidation \
  --distribution-id "$DIST_ID" \
  --paths "/index.html" \
  --output text >/dev/null

echo "==> Done."
