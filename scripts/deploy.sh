#!/usr/bin/env bash
#
# Deploy Mobi to any environment, local or managed.
#
#   scripts/deploy.sh local           kind: also installs MySQL, Redis, Kafka in-cluster
#   scripts/deploy.sh aws-staging     EKS,  using managed RDS / ElastiCache / MSK
#   scripts/deploy.sh gcp-staging     GKE
#   scripts/deploy.sh azure-staging   AKS
#
# One script for every target. The only thing that varies is which values files
# are layered and whether the stateful dependencies are installed in-cluster —
# both derived from the env name, so a new cloud needs no change here.
#
# For cloud envs, run scripts/render-values.sh <env> first: endpoints come from
# Terraform outputs, never from a checked-in file.

set -euo pipefail

SERVICES=(
  auth-service
  product-service
  inventory-service
  order-service
  notification-service
  api-gateway
)

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="$REPO_ROOT/helm/mobi"
NAMESPACE="${K8S_NAMESPACE:-mobi}"
HELM_TIMEOUT="${HELM_TIMEOUT:-10m}"

die() {
  echo "error: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

require_env() {
  if [[ -z "${!1:-}" ]]; then
    die "missing required environment value: $1
       Set it in $ENV_FILE or export it before running this script."
  fi
}

usage() {
  echo "usage: scripts/deploy.sh <env>" >&2
  echo >&2
  echo "  local        kind cluster, with in-cluster MySQL / Redis / Kafka" >&2
  for dir in "$REPO_ROOT"/infra/terraform/envs/*/; do
    [[ -d "$dir" ]] && echo "  $(basename "$dir")" >&2
  done
  exit 2
}

ENV_NAME="${1:-}"
[[ -n "$ENV_NAME" ]] || usage
[[ $# -le 1 ]] || die "unexpected argument: $2"

require_cmd kubectl
require_cmd helm

# --- Resolve the env into a values chain -------------------------------------
#
# Precedence runs left to right, so the generated Terraform values win over the
# per-cloud overlay, which wins over the shared cloud layer, which wins over base.

VALUES_ARGS=(--values "$CHART/values.yaml")

if [[ "$ENV_NAME" == "local" ]]; then
  INSTALL_INCLUSTER_DEPS=true
  VALUES_ARGS+=(--values "$CHART/values-local.yaml")
else
  INSTALL_INCLUSTER_DEPS=false

  CLOUD="${ENV_NAME%%-*}"
  [[ "$CLOUD" != "$ENV_NAME" ]] || die "env must be 'local' or '<cloud>-<tier>', e.g. aws-staging"

  CLOUD_VALUES="$CHART/values-$CLOUD.yaml"
  [[ -f "$CLOUD_VALUES" ]] || die "no overlay for cloud '$CLOUD' at helm/mobi/values-$CLOUD.yaml"

  GENERATED_VALUES="$CHART/values-$ENV_NAME.generated.yaml"
  [[ -f "$GENERATED_VALUES" ]] || die "missing $GENERATED_VALUES
       Generate it from Terraform state first:
         scripts/render-values.sh $ENV_NAME"

  VALUES_ARGS+=(
    --values "$CHART/values-cloud.yaml"
    --values "$CLOUD_VALUES"
    --values "$GENERATED_VALUES"
  )
fi

# --- Check cluster reachability before doing anything ------------------------

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "error: kubectl cannot reach a cluster." >&2
  case "${CLOUD:-local}" in
    aws)   echo "       aws eks update-kubeconfig --name <cluster> --region <region>" >&2 ;;
    gcp)   echo "       gcloud container clusters get-credentials <cluster> --region <region>" >&2 ;;
    azure) echo "       az aks get-credentials --name <cluster> --resource-group <group>" >&2 ;;
    *)     echo "       For local: scripts/kind-up.sh" >&2 ;;
  esac
  echo "       The cluster name is the 'cluster_name' Terraform output." >&2
  exit 1
fi

echo "==> deploying $ENV_NAME to namespace $NAMESPACE"
kubectl apply -f "$REPO_ROOT/k8s/base/namespace.yaml"

# --- Secrets ----------------------------------------------------------------
#
# Local secrets come from .env.local, which is convenient and safe because the
# values are throwaway. Cloud secrets deliberately do NOT: pushing a developer's
# local file into a shared cluster is how test credentials reach production. On a
# cloud the Secret is expected to exist already, populated from that provider's
# secret manager — ideally by External Secrets Operator, whose ExternalSecret
# manifests are identical across clouds with only the SecretStore differing.

if [[ "$INSTALL_INCLUSTER_DEPS" == true ]]; then
  ENV_FILE="${ENV_FILE:-$REPO_ROOT/.env.local}"

  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi

  INTERNAL_SERVICE_SECRET="${INTERNAL_SERVICE_SECRET:-local-internal-secret}"
  ADMIN_BOOTSTRAP_USERNAME="${ADMIN_BOOTSTRAP_USERNAME:-admin}"
  ADMIN_BOOTSTRAP_PASSWORD="${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}"

  for name in \
    MYSQL_ROOT_PASSWORD \
    AUTH_DB_PASSWORD \
    PRODUCT_DB_PASSWORD \
    INVENTORY_DB_PASSWORD \
    ORDER_DB_PASSWORD \
    JWT_SECRET \
    INTERNAL_SERVICE_SECRET \
    ADMIN_BOOTSTRAP_USERNAME \
    ADMIN_BOOTSTRAP_PASSWORD
  do
    require_env "$name"
  done

  kubectl create secret generic mobi-secrets \
    --namespace "$NAMESPACE" \
    --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
    --from-literal=AUTH_DB_PASSWORD="$AUTH_DB_PASSWORD" \
    --from-literal=PRODUCT_DB_PASSWORD="$PRODUCT_DB_PASSWORD" \
    --from-literal=INVENTORY_DB_PASSWORD="$INVENTORY_DB_PASSWORD" \
    --from-literal=ORDER_DB_PASSWORD="$ORDER_DB_PASSWORD" \
    --from-literal=JWT_SECRET="$JWT_SECRET" \
    --from-literal=INTERNAL_SERVICE_SECRET="$INTERNAL_SERVICE_SECRET" \
    --from-literal=ADMIN_BOOTSTRAP_USERNAME="$ADMIN_BOOTSTRAP_USERNAME" \
    --from-literal=ADMIN_BOOTSTRAP_PASSWORD="$ADMIN_BOOTSTRAP_PASSWORD" \
    --dry-run=client \
    -o yaml | kubectl apply -f -
else
  kubectl get secret mobi-secrets --namespace "$NAMESPACE" >/dev/null 2>&1 || die \
    "secret 'mobi-secrets' not found in namespace $NAMESPACE.
       Cloud environments do not take secrets from .env.local by design.
       Populate it from the provider's secret manager — see docs/PORTABILITY.md.
       Required keys: AUTH_DB_PASSWORD, PRODUCT_DB_PASSWORD, INVENTORY_DB_PASSWORD,
       ORDER_DB_PASSWORD, JWT_SECRET, INTERNAL_SERVICE_SECRET
       Plus MYSQL_ADMIN_USERNAME and MYSQL_ADMIN_PASSWORD — the managed instance's
       master credentials, used by the db-bootstrap Job to create the four
       per-service databases and grant each service user access to its own.
       Plus KAFKA_SASL_JAAS_CONFIG when kafka_security_protocol is SASL_*."

  # The bootstrap Job is a pre-install hook, so it runs before the chart's own
  # ConfigMap exists and cannot read these from there. Catch a missing key now
  # rather than letting the hook fail mid-release.
  for key in MYSQL_ADMIN_USERNAME MYSQL_ADMIN_PASSWORD; do
    kubectl get secret mobi-secrets --namespace "$NAMESPACE" \
      -o "jsonpath={.data.$key}" 2>/dev/null | grep -q . || die \
      "secret 'mobi-secrets' is missing key $key.
       The db-bootstrap Job needs the managed instance's master credentials to
       create the per-service databases. Add it, or disable the Job with
       --set dbBootstrap.enabled=false if you provision databases another way."
  done
fi

# --- In-cluster stateful dependencies (local only) --------------------------
#
# On a cloud these are managed services provisioned by Terraform, and their
# endpoints arrive through the generated values.

if [[ "$INSTALL_INCLUSTER_DEPS" == true ]]; then
  echo "==> installing in-cluster MySQL, Redis, and Kafka"
  kubectl apply -f "$REPO_ROOT/k8s/infra/mysql/initdb-configmap.yaml"

  helm upgrade --install mysql bitnami/mysql \
    --namespace "$NAMESPACE" \
    --values "$REPO_ROOT/k8s/infra/mysql/values.yaml" \
    --set-string auth.rootPassword="$MYSQL_ROOT_PASSWORD" \
    --wait --timeout "$HELM_TIMEOUT"

  helm upgrade --install redis bitnami/redis \
    --namespace "$NAMESPACE" \
    --values "$REPO_ROOT/k8s/infra/redis/values.yaml" \
    --wait --timeout "$HELM_TIMEOUT"

  helm upgrade --install kafka bitnami/kafka \
    --namespace "$NAMESPACE" \
    --values "$REPO_ROOT/k8s/infra/kafka/values.yaml" \
    --wait --timeout "$HELM_TIMEOUT"

  # Adopt resources left behind by the pre-Helm kubectl-apply era, which Helm
  # would otherwise refuse to take ownership of.
  if ! helm status mobi --namespace "$NAMESPACE" >/dev/null 2>&1; then
    kubectl delete deployment,service --namespace "$NAMESPACE" \
      -l app.kubernetes.io/part-of=mobi --ignore-not-found
    kubectl delete ingress api-gateway --namespace "$NAMESPACE" --ignore-not-found
    kubectl delete configmap mobi-config --namespace "$NAMESPACE" --ignore-not-found
  fi
fi

# --- Application (identical on every target) --------------------------------

echo "==> helm upgrade --install mobi"
helm upgrade --install mobi "$CHART" \
  --namespace "$NAMESPACE" \
  "${VALUES_ARGS[@]}" \
  --wait --timeout "$HELM_TIMEOUT"

for svc in "${SERVICES[@]}"; do
  kubectl rollout status "deployment/$svc" --namespace "$NAMESPACE" --timeout "$HELM_TIMEOUT"
done

echo "Mobi deployment to $ENV_NAME is ready"
