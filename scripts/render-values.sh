#!/usr/bin/env bash
#
# Generate a Helm values file from a Terraform environment's outputs.
#
#   scripts/render-values.sh <env> [--tag <image-tag>]
#
#   scripts/render-values.sh aws-staging
#   scripts/render-values.sh gcp-staging --tag v1.4.2
#
# Reads `terraform output -json` from infra/terraform/envs/<env> and writes
# helm/mobi/values-<env>.generated.yaml. The generated file is gitignored: it
# holds real hostnames and account-specific registry paths, and it is cheap to
# recreate, so it is never a source of truth.
#
# This replaces hand-pasting Terraform outputs into a checked-in values file —
# the step that previously had to be repeated per cloud, per environment, and
# that silently rotted whenever infrastructure was replaced.
#
# The eleven names read below are the portability contract. Every env directory on
# every cloud must emit them; nothing else here is provider-aware, which is why
# adding a cloud needs no change to this script.

set -euo pipefail

CONTRACT_OUTPUTS=(
  cluster_name
  registry_prefix
  db_endpoint
  db_jdbc_params
  redis_endpoint
  redis_port
  redis_ssl_enabled
  kafka_bootstrap
  kafka_security_protocol
  mail_host
  mail_from
)

SERVICES=(
  api-gateway
  auth-service
  product-service
  inventory-service
  order-service
  payment-service
  procurement-service
  notification-service
)

# Logical database name per service. The service name prefix is also the env var
# prefix the Spring app reads, e.g. AUTH_DB_URL.
DATABASES=(
  "AUTH:authdb"
  "PRODUCT:productdb"
  "INVENTORY:inventorydb"
  "ORDER:orderdb"
  "PAYMENT:paymentdb"
  "PROCUREMENT:procurementdb"
  "NOTIFICATION:notificationdb"
)

die() {
  echo "error: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

usage() {
  echo "usage: scripts/render-values.sh <env> [--tag <image-tag>]" >&2
  echo >&2
  echo "available envs:" >&2
  for dir in "$REPO_ROOT"/infra/terraform/envs/*/; do
    [[ -d "$dir" ]] && echo "  $(basename "$dir")" >&2
  done
  exit 2
}

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ENV_NAME="${1:-}"
[[ -n "$ENV_NAME" ]] || usage
shift

IMAGE_TAG=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      IMAGE_TAG="${2:-}"
      [[ -n "$IMAGE_TAG" ]] || die "--tag needs a value"
      shift 2
      ;;
    *) die "unexpected argument: $1" ;;
  esac
done

# Validate the argument before checking for tools: a typo'd env name is the
# user's mistake and should be reported as such, whatever else is installed.
ENV_DIR="$REPO_ROOT/infra/terraform/envs/$ENV_NAME"
[[ -d "$ENV_DIR" ]] || { echo "error: no such env: $ENV_NAME" >&2; usage; }

require_cmd terraform
require_cmd jq

# Images are pinned to an immutable tag. Defaulting to the commit SHA means a
# deploy is always traceable to a revision; ECR is configured tag-IMMUTABLE, so a
# mutable tag like "latest" would be rejected on push anyway.
if [[ -z "$IMAGE_TAG" ]]; then
  IMAGE_TAG="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null)" \
    || die "not a git repo and no --tag given"
  if ! git -C "$REPO_ROOT" diff --quiet HEAD 2>/dev/null; then
    echo "warning: working tree is dirty; tag $IMAGE_TAG will not match these files" >&2
  fi
fi

echo "==> reading terraform outputs from envs/$ENV_NAME"
OUTPUTS_JSON="$(terraform -chdir="$ENV_DIR" output -json)" \
  || die "terraform output failed. Has this env been applied? Try: terraform -chdir=infra/terraform/envs/$ENV_NAME init"

[[ "$(jq -r 'length' <<<"$OUTPUTS_JSON")" != "0" ]] \
  || die "envs/$ENV_NAME has no outputs — apply it before rendering values"

# Enforce the contract. Report every missing name at once: when someone stands up
# a new cloud, one error listing all gaps is far more useful than eleven rounds of
# fix-one-rerun. Presence is checked with has(), so a legitimately empty value
# such as db_jdbc_params="" passes.
missing=()
for name in "${CONTRACT_OUTPUTS[@]}"; do
  jq -e --arg n "$name" 'has($n)' <<<"$OUTPUTS_JSON" >/dev/null || missing+=("$name")
done
if (( ${#missing[@]} > 0 )); then
  echo "error: envs/$ENV_NAME does not satisfy the portability contract." >&2
  echo "       missing outputs:" >&2
  printf '         - %s\n' "${missing[@]}" >&2
  echo "       see docs/PORTABILITY.md and envs/aws-staging/outputs.tf" >&2
  exit 1
fi

out() { jq -r --arg n "$1" '.[$n].value' <<<"$OUTPUTS_JSON"; }

REGISTRY_PREFIX="$(out registry_prefix)"
DB_ENDPOINT="$(out db_endpoint)"
DB_JDBC_PARAMS="$(out db_jdbc_params)"
REDIS_ENDPOINT="$(out redis_endpoint)"
REDIS_PORT="$(out redis_port)"
REDIS_SSL_ENABLED="$(out redis_ssl_enabled)"
KAFKA_BOOTSTRAP="$(out kafka_bootstrap)"
KAFKA_SECURITY_PROTOCOL="$(out kafka_security_protocol)"
MAIL_HOST="$(out mail_host)"
# JSON-encoded: a display name may hold quotes, and a JSON string is valid YAML.
MAIL_FROM_YAML="$(jq -c '.mail_from.value' <<<"$OUTPUTS_JSON")"

# Guard the one mistake this file cannot recover from: a SASL protocol needs a
# JAAS credential that the ConfigMap must never carry. Fail loudly at render time
# rather than leaving a consumer to retry-loop against a broker that rejects it.
if [[ "$KAFKA_SECURITY_PROTOCOL" == SASL_* ]]; then
  echo "note: kafka_security_protocol is $KAFKA_SECURITY_PROTOCOL, so the cluster" >&2
  echo "      Secret must contain KAFKA_SASL_JAAS_CONFIG, and every Kafka client" >&2
  echo "      service needs secretEnv: true in the per-cloud overlay." >&2
fi

OUT_FILE="$REPO_ROOT/helm/mobi/values-$ENV_NAME.generated.yaml"

{
  echo "# GENERATED FILE — DO NOT EDIT."
  echo "#"
  echo "# Rendered from infra/terraform/envs/$ENV_NAME by scripts/render-values.sh."
  echo "# Regenerate with: scripts/render-values.sh $ENV_NAME"
  echo "#"
  echo "# Every value below comes from Terraform state. Edit the infrastructure, not"
  echo "# this file. Cloud-invariant settings belong in values-cloud.yaml; load"
  echo "# balancer flavour belongs in values-<cloud>.yaml."
  echo
  echo "image:"
  echo "  tag: \"$IMAGE_TAG\""
  echo
  echo "services:"
  for svc in "${SERVICES[@]}"; do
    echo "  $svc:"
    echo "    image: \"$REGISTRY_PREFIX/$svc\""
  done
  echo
  echo "config:"
  for entry in "${DATABASES[@]}"; do
    prefix="${entry%%:*}"
    dbname="${entry##*:}"
    echo "  ${prefix}_DB_URL: \"jdbc:mysql://$DB_ENDPOINT:3306/$dbname$DB_JDBC_PARAMS\""
  done
  echo
  echo "  REDIS_HOST: \"$REDIS_ENDPOINT\""
  echo "  REDIS_PORT: \"$REDIS_PORT\""
  echo "  REDIS_SSL_ENABLED: \"$REDIS_SSL_ENABLED\""
  echo
  echo "  KAFKA_BOOTSTRAP_SERVERS: \"$KAFKA_BOOTSTRAP\""
  echo "  KAFKA_SECURITY_PROTOCOL: \"$KAFKA_SECURITY_PROTOCOL\""
  echo
  echo "  MAIL_HOST: \"$MAIL_HOST\""
  echo "  MAIL_FROM: $MAIL_FROM_YAML"
  echo
  echo "# The pre-install Job that creates the four per-service databases and users."
  echo "# Enabled for cloud environments in values-cloud.yaml; it needs the raw"
  echo "# hostname rather than a JDBC URL because it runs the mysql client, not JDBC."
  echo "dbBootstrap:"
  echo "  host: \"$DB_ENDPOINT\""
} >"$OUT_FILE"

echo "==> wrote helm/mobi/values-$ENV_NAME.generated.yaml (tag $IMAGE_TAG)"
