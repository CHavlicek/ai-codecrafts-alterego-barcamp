#!/usr/bin/env bash
# Build the backend bootJar, upload to S3, trigger in-place refresh via SSM.
#
# Usage:
#   ./deploy-backend.sh                # full: build + upload + restart
#   ./deploy-backend.sh --skip-build   # skip build/upload, just restart the
#                                      # service (uses whatever jar is in S3)
set -euo pipefail

SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help)
      sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown flag: $arg" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/deploy/terraform"

JAR_S3_URI=$(terraform -chdir="$TF_DIR" output -raw backend_jar_s3_uri)
INSTANCE_ID=$(terraform -chdir="$TF_DIR" output -raw backend_instance_id)
REGION=$(terraform -chdir="$TF_DIR" output -raw region)

if [ "$SKIP_BUILD" -eq 0 ]; then
  cd "$REPO_ROOT/backend"
  if [ -x ./gradlew ]; then
    ./gradlew bootJar --no-daemon
  else
    gradle bootJar --no-daemon
  fi

  JAR=$(ls "$REPO_ROOT/backend/build/libs/"*.jar | grep -v -- '-plain\.jar$' | head -n1)
  if [ -z "$JAR" ]; then
    echo "No bootJar found in backend/build/libs/" >&2
    exit 1
  fi

  echo "==> Uploading $(basename "$JAR") -> $JAR_S3_URI"
  aws s3 cp "$JAR" "$JAR_S3_URI" --region "$REGION"
else
  echo "==> --skip-build: reusing $JAR_S3_URI"
fi

# SSM agent may not be Online for ~minutes after a fresh launch (or after a
# reboot). Poll up to 5 min before giving up so the first deploy works without
# manual retries.
echo "==> Waiting for SSM agent on $INSTANCE_ID"
PING=""
for _ in $(seq 1 60); do
  PING=$(aws ssm describe-instance-information \
    --filters "Key=InstanceIds,Values=$INSTANCE_ID" \
    --region "$REGION" \
    --query 'InstanceInformationList[0].PingStatus' \
    --output text 2>/dev/null || true)
  if [ "$PING" = "Online" ]; then break; fi
  sleep 5
done
if [ "$PING" != "Online" ]; then
  echo "SSM agent didn't reach Online within 5 minutes (last status: ${PING:-unknown})." >&2
  echo "Check: aws ec2 get-console-output --instance-ids $INSTANCE_ID --region $REGION --latest" >&2
  exit 1
fi

echo "==> Triggering /opt/aiavatar/refresh.sh"
CMD_ID=$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "aiavatar backend refresh" \
  --parameters 'commands=["/opt/aiavatar/refresh.sh"]' \
  --region "$REGION" \
  --query 'Command.CommandId' \
  --output text)

echo "==> Waiting for command $CMD_ID"
aws ssm wait command-executed \
  --command-id "$CMD_ID" \
  --instance-id "$INSTANCE_ID" \
  --region "$REGION"

aws ssm get-command-invocation \
  --command-id "$CMD_ID" \
  --instance-id "$INSTANCE_ID" \
  --region "$REGION" \
  --query '{Status:Status,StdErr:StandardErrorContent}' --output table

echo "==> Done."
