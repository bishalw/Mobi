#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.local}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export SMOKE_BASE_URL="${SMOKE_BASE_URL:-http://localhost}"
export SMOKE_ADMIN_USER="${SMOKE_ADMIN_USER:-${ADMIN_BOOTSTRAP_USERNAME:-admin}}"
export SMOKE_ADMIN_PASS="${SMOKE_ADMIN_PASS:-${ADMIN_BOOTSTRAP_PASSWORD:-adminpass}}"

./scripts/smoke.sh

echo ""
echo "Recent notification-service logs:"
kubectl logs deployment/notification-service --namespace "${K8S_NAMESPACE:-mobi}" --tail=120

echo ""
echo "Emails sent during the run are in Mailpit:"
echo "  kubectl -n ${K8S_NAMESPACE:-mobi} port-forward svc/mailpit 8025:8025   then http://localhost:8025"

