#!/usr/bin/env bash
#
# Deploy Mobi to any environment, local or managed.
#
#   scripts/deploy.sh local           kind: also installs MySQL, Redis, Kafka, Jaeger, Mailpit in-cluster
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
  payment-service
  procurement-service
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

# The chart's Gateway needs the Gateway API CRDs and Envoy Gateway's `eg` class.
if ! kubectl get gatewayclass eg >/dev/null 2>&1; then
  die "GatewayClass 'eg' not found: Envoy Gateway is not installed in this cluster.
       Install it once with: scripts/install-envoy-gateway.sh"
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

  # Token-signing key and service-client secret: generated once and saved to the env
  # file, so redeploys keep the same key and existing sessions stay valid.
  if [[ -z "${JWT_PRIVATE_KEY:-}" ]]; then
    echo "==> generating an RS256 token-signing key into $ENV_FILE"
    JWT_PRIVATE_KEY="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null \
      | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n')"
    printf '\nJWT_PRIVATE_KEY=%s\n' "$JWT_PRIVATE_KEY" >> "$ENV_FILE"
  fi
  # Added with procurement-service; generated so an existing .env.local keeps working.
  if [[ -z "${PROCUREMENT_DB_PASSWORD:-}" ]]; then
    PROCUREMENT_DB_PASSWORD="$(openssl rand -hex 16)"
    printf 'PROCUREMENT_DB_PASSWORD=%s\n' "$PROCUREMENT_DB_PASSWORD" >> "$ENV_FILE"
  fi
  if [[ -z "${ORDER_SERVICE_CLIENT_SECRET:-}" ]]; then
    ORDER_SERVICE_CLIENT_SECRET="$(openssl rand -hex 24)"
    printf 'ORDER_SERVICE_CLIENT_SECRET=%s\n' "$ORDER_SERVICE_CLIENT_SECRET" >> "$ENV_FILE"
  fi
  ADMIN_BOOTSTRAP_USERNAME="${ADMIN_BOOTSTRAP_USERNAME:-admin}"
  ADMIN_BOOTSTRAP_PASSWORD="${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}"
  WAREHOUSE_BOOTSTRAP_USERNAME="${WAREHOUSE_BOOTSTRAP_USERNAME:-warehouse}"
  WAREHOUSE_BOOTSTRAP_PASSWORD="${WAREHOUSE_BOOTSTRAP_PASSWORD:-warehousepass}"
  SUPPLIER_BOOTSTRAP_USERNAME="${SUPPLIER_BOOTSTRAP_USERNAME:-supplier}"
  SUPPLIER_BOOTSTRAP_PASSWORD="${SUPPLIER_BOOTSTRAP_PASSWORD:-supplierpass}"

  # Email goes to the in-cluster Mailpit unless MAIL_HOST is set in the env file. Setting
  # it opts this kind cluster into a real SMTP relay (SES sandbox credentials, a Gmail app
  # password, Mailtrap...), so the emails reach real inboxes. See docs/LOCAL_SETUP.md.
  mail_secret_args=()
  if [[ -n "${MAIL_HOST:-}" ]]; then
    MAIL_PORT="${MAIL_PORT:-587}"
    MAIL_SMTP_AUTH="${MAIL_SMTP_AUTH:-true}"
    MAIL_STARTTLS="${MAIL_STARTTLS:-true}"
    require_env MAIL_FROM
    if [[ "$MAIL_SMTP_AUTH" == true ]]; then
      require_env MAIL_USERNAME
      require_env MAIL_PASSWORD
      mail_secret_args+=(
        --from-literal=MAIL_USERNAME="$MAIL_USERNAME"
        --from-literal=MAIL_PASSWORD="$MAIL_PASSWORD"
      )
    fi

    # Overrides values.yaml's Mailpit settings. A values file rather than --set, which
    # would split MAIL_FROM on commas; double-quoted YAML with \ and " escaped.
    yaml_str() { local v="${1//\\/\\\\}"; printf '"%s"' "${v//\"/\\\"}"; }
    LOCAL_MAIL_VALUES="$(mktemp "${TMPDIR:-/tmp}/mobi-mail-values.XXXXXX")"
    trap 'rm -f "$LOCAL_MAIL_VALUES"' EXIT
    {
      echo "config:"
      echo "  MAIL_HOST: $(yaml_str "$MAIL_HOST")"
      echo "  MAIL_PORT: $(yaml_str "$MAIL_PORT")"
      echo "  MAIL_SMTP_AUTH: $(yaml_str "$MAIL_SMTP_AUTH")"
      echo "  MAIL_STARTTLS: $(yaml_str "$MAIL_STARTTLS")"
      echo "  MAIL_FROM: $(yaml_str "$MAIL_FROM")"
    } >"$LOCAL_MAIL_VALUES"
    VALUES_ARGS+=(--values "$LOCAL_MAIL_VALUES")
    echo "==> email: real SMTP through $MAIL_HOST:$MAIL_PORT (MAIL_HOST is set), not Mailpit"
  fi

  for name in \
    MYSQL_ROOT_PASSWORD \
    AUTH_DB_PASSWORD \
    PRODUCT_DB_PASSWORD \
    INVENTORY_DB_PASSWORD \
    ORDER_DB_PASSWORD \
    PAYMENT_DB_PASSWORD \
    PROCUREMENT_DB_PASSWORD \
    NOTIFICATION_DB_PASSWORD \
    JWT_PRIVATE_KEY \
    ORDER_SERVICE_CLIENT_SECRET \
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
    --from-literal=PAYMENT_DB_PASSWORD="$PAYMENT_DB_PASSWORD" \
    --from-literal=PROCUREMENT_DB_PASSWORD="$PROCUREMENT_DB_PASSWORD" \
    --from-literal=NOTIFICATION_DB_PASSWORD="$NOTIFICATION_DB_PASSWORD" \
    --from-literal=ADMIN_BOOTSTRAP_USERNAME="$ADMIN_BOOTSTRAP_USERNAME" \
    --from-literal=ADMIN_BOOTSTRAP_PASSWORD="$ADMIN_BOOTSTRAP_PASSWORD" \
    --from-literal=WAREHOUSE_BOOTSTRAP_USERNAME="$WAREHOUSE_BOOTSTRAP_USERNAME" \
    --from-literal=WAREHOUSE_BOOTSTRAP_PASSWORD="$WAREHOUSE_BOOTSTRAP_PASSWORD" \
    --from-literal=SUPPLIER_BOOTSTRAP_USERNAME="$SUPPLIER_BOOTSTRAP_USERNAME" \
    --from-literal=SUPPLIER_BOOTSTRAP_PASSWORD="$SUPPLIER_BOOTSTRAP_PASSWORD" \
    ${mail_secret_args[@]+"${mail_secret_args[@]}"} \
    --dry-run=client \
    -o yaml | kubectl apply -f -

  # Separate Secret, mounted key by key: only auth-service sees the signing key, and
  # only auth-service and order-service see order-service's client secret.
  auth_secret_args=(
    --from-literal=JWT_PRIVATE_KEY="$JWT_PRIVATE_KEY"
    --from-literal=ORDER_SERVICE_CLIENT_SECRET="$ORDER_SERVICE_CLIENT_SECRET"
  )
  if [[ -n "${JWT_PREVIOUS_PUBLIC_KEY:-}" ]]; then
    auth_secret_args+=(--from-literal=JWT_PREVIOUS_PUBLIC_KEY="$JWT_PREVIOUS_PUBLIC_KEY")
  fi
  kubectl create secret generic mobi-auth-secrets \
    --namespace "$NAMESPACE" \
    "${auth_secret_args[@]}" \
    --dry-run=client \
    -o yaml | kubectl apply -f -
else
  kubectl get secret mobi-secrets --namespace "$NAMESPACE" >/dev/null 2>&1 || die \
    "secret 'mobi-secrets' not found in namespace $NAMESPACE.
       Cloud environments do not take secrets from .env.local by design.
       Populate it from the provider's secret manager — see docs/PORTABILITY.md.
       Required keys: AUTH_DB_PASSWORD, PRODUCT_DB_PASSWORD, INVENTORY_DB_PASSWORD,
       ORDER_DB_PASSWORD, PAYMENT_DB_PASSWORD, PROCUREMENT_DB_PASSWORD,
       NOTIFICATION_DB_PASSWORD
       Plus MAIL_USERNAME / MAIL_PASSWORD for the SMTP relay (on AWS:
       terraform output -raw ses_smtp_username / ses_smtp_password), and
       STRIPE_SECRET_KEY / TWILIO_* if those providers are switched on.
       Plus MYSQL_ADMIN_USERNAME and MYSQL_ADMIN_PASSWORD — the managed instance's
       master credentials, used by the db-bootstrap Job to create the
       per-service databases and grant each service user access to its own.
       Plus KAFKA_SASL_JAAS_CONFIG when kafka_security_protocol is SASL_*."

  kubectl get secret mobi-auth-secrets --namespace "$NAMESPACE" >/dev/null 2>&1 || die \
    "secret 'mobi-auth-secrets' not found in namespace $NAMESPACE.
       Required keys: JWT_PRIVATE_KEY (RSA, PKCS#8, PEM or base64 DER) and
       ORDER_SERVICE_CLIENT_SECRET. Optional: JWT_PREVIOUS_PUBLIC_KEY during a key
       rotation. Kept apart from mobi-secrets so only auth-service holds the key."

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

  # Clouds have no Mailpit: without the relay credentials every email would retry and
  # end up on the dead-letter topic, which is easy to miss.
  for key in MAIL_USERNAME MAIL_PASSWORD; do
    kubectl get secret mobi-secrets --namespace "$NAMESPACE" \
      -o "jsonpath={.data.$key}" 2>/dev/null | grep -q . || die \
      "secret 'mobi-secrets' is missing key $key.
       notification-service sends through an authenticated SMTP relay on every cloud.
       On AWS: terraform -chdir=infra/terraform/envs/$ENV_NAME output -raw ses_smtp_username
       (MAIL_USERNAME) and ses_smtp_password (MAIL_PASSWORD)."
  done
fi

# --- In-cluster stateful dependencies (local only) --------------------------
#
# On a cloud these are managed services provisioned by Terraform, and their
# endpoints arrive through the generated values.

if [[ "$INSTALL_INCLUSTER_DEPS" == true ]]; then
  echo "==> installing in-cluster MySQL, Redis, Kafka, Jaeger, and Mailpit"
  kubectl apply -f "$REPO_ROOT/k8s/infra/mysql/initdb-configmap.yaml"

  # MySQL 8.4 LTS from the official image (k8s/infra/mysql/mysql.yaml). Clusters from
  # before the switch run the Bitnami chart (MySQL 8.0, frozen bitnamilegacy image) as a
  # Helm release that owns a StatefulSet of the same name. It is removed along with its
  # volume: 8.0 Bitnami files can't be used by the 8.4 image, and local data is
  # throwaway demo data anyway. Re-seed with scripts/seed-phones.sh afterwards.
  # A marker file, not just a variable: if a later step fails after the wipe, the re-run
  # still knows the database is empty and seeds it.
  RESEED_MARKER="$REPO_ROOT/.mobi-reseed-pending"
  if helm status mysql --namespace "$NAMESPACE" >/dev/null 2>&1; then
    echo "==> replacing Bitnami MySQL 8.0 with MySQL 8.4: deleting the old release and its data"
    touch "$RESEED_MARKER"
    helm uninstall mysql --namespace "$NAMESPACE" --wait
    kubectl delete pvc data-mysql-0 --namespace "$NAMESPACE" --ignore-not-found --wait=true
  fi
  [[ -f "$RESEED_MARKER" ]] && MYSQL_WIPED=true
  kubectl apply -f "$REPO_ROOT/k8s/infra/mysql/mysql.yaml"
  kubectl rollout status statefulset/mysql --namespace "$NAMESPACE" --timeout "$HELM_TIMEOUT"

  # The image runs init scripts only when the data volume is first created, so a
  # cluster from before a service was added would never get its database. The script
  # is CREATE ... IF NOT EXISTS throughout, so re-running it on every deploy is safe.
  kubectl get configmap mysql-initdb --namespace "$NAMESPACE" \
    -o jsonpath='{.data.01-create-service-databases\.sh}' \
    | kubectl exec -i --namespace "$NAMESPACE" statefulset/mysql -- bash

  helm upgrade --install redis bitnami/redis \
    --namespace "$NAMESPACE" \
    --values "$REPO_ROOT/k8s/infra/redis/values.yaml" \
    --wait --timeout "$HELM_TIMEOUT"

  helm upgrade --install kafka bitnami/kafka \
    --namespace "$NAMESPACE" \
    --values "$REPO_ROOT/k8s/infra/kafka/values.yaml" \
    --wait --timeout "$HELM_TIMEOUT"

  # Trace UI and email inbox for local development.
  kubectl apply -f "$REPO_ROOT/k8s/infra/jaeger/jaeger.yaml"
  kubectl apply -f "$REPO_ROOT/k8s/infra/mailpit/mailpit.yaml"

  # Adopt resources left behind by the pre-Helm kubectl-apply era, which Helm
  # would otherwise refuse to take ownership of.
  if ! helm status mobi --namespace "$NAMESPACE" >/dev/null 2>&1; then
    kubectl delete deployment,service --namespace "$NAMESPACE" \
      -l app.kubernetes.io/part-of=mobi --ignore-not-found
    kubectl delete configmap mobi-config --namespace "$NAMESPACE" --ignore-not-found
  fi
fi

# The Ingress from before the move to Gateway API. Helm removes it on upgrade when it
# owns it; this covers one created by hand or by the pre-Helm scripts.
kubectl delete ingress api-gateway --namespace "$NAMESPACE" --ignore-not-found

# --- Application (identical on every target) --------------------------------

APP_RELEASE_EXISTED=false
helm status mobi --namespace "$NAMESPACE" >/dev/null 2>&1 && APP_RELEASE_EXISTED=true

echo "==> helm upgrade --install mobi"
helm upgrade --install mobi "$CHART" \
  --namespace "$NAMESPACE" \
  "${VALUES_ARGS[@]}" \
  --wait --timeout "$HELM_TIMEOUT"

# Local images are always tagged :local, so a rebuilt image or a recreated database
# leaves the pod spec unchanged and Helm restarts nothing. Restart them all: pods pick up
# the images just loaded into kind, and run Flyway against the (possibly new) database.
# (A first install has just started fresh pods, so there is nothing to restart.)
if [[ "$ENV_NAME" == "local" && "$APP_RELEASE_EXISTED" == true ]]; then
  for svc in "${SERVICES[@]}"; do
    kubectl rollout restart "deployment/$svc" --namespace "$NAMESPACE"
  done
fi

for svc in "${SERVICES[@]}"; do
  kubectl rollout status "deployment/$svc" --namespace "$NAMESPACE" --timeout "$HELM_TIMEOUT"
done

echo "Mobi deployment to $ENV_NAME is ready"

# The MySQL 8.4 switch above started from an empty database; put the demo data back.
if [[ "${MYSQL_WIPED:-false}" == true ]]; then
  echo "==> re-seeding demo data after the MySQL 8.4 switch"
  if ENV_FILE="$ENV_FILE" "$REPO_ROOT/scripts/seed-phones.sh"; then
    rm -f "$REPO_ROOT/.mobi-reseed-pending"
  else
    echo "warning: seeding failed; run ./scripts/seed-phones.sh (deploy.sh will also retry it next time)"
  fi
fi
if [[ "$ENV_NAME" == "local" ]]; then
  echo
  echo "  API             http://localhost"
  echo "  Traces          kubectl -n $NAMESPACE port-forward svc/jaeger 16686:16686   then http://localhost:16686"
  echo "  Email inbox     kubectl -n $NAMESPACE port-forward svc/mailpit 8025:8025    then http://localhost:8025"
else
  echo "  Edge address:   kubectl -n $NAMESPACE get gateway mobi -o jsonpath='{.status.addresses[0].value}'"
fi
