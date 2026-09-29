#!/usr/bin/env bash
#
# Deprecated. Superseded by scripts/deploy.sh, which drives local and every cloud
# environment from one code path. Kept so existing docs and muscle memory keep
# working; forwards to the local target.

set -euo pipefail

echo "note: scripts/k8s-deploy.sh is deprecated — use 'scripts/deploy.sh local'" >&2

exec "$(dirname "${BASH_SOURCE[0]}")/deploy.sh" local
