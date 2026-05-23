#!/usr/bin/env bash
# Bootstrap Vault Transit in a running cluster (tenant secret encryption, pathway #8).
# Usage: ./scripts/deploy/k8s-local-vault-transit.sh [namespace]
set -euo pipefail

NAMESPACE="${1:-pravah}"
TRANSIT_MOUNT="${VAULT_TRANSIT_MOUNT:-transit}"

VAULT_POD="$(kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/component=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
[[ -n "$VAULT_POD" ]] || { echo "No Vault pod in namespace ${NAMESPACE}" >&2; exit 1; }

CREDS_SECRET="$(kubectl -n "$NAMESPACE" get secrets -o name | grep -E 'pravah.*' | head -1 | sed 's|secret/||' || true)"
[[ -n "$CREDS_SECRET" ]] || CREDS_SECRET="pravah"
VAULT_TOKEN="$(kubectl -n "$NAMESPACE" get secret "$CREDS_SECRET" -o jsonpath='{.data.vault-dev-root-token}' | base64 -d)"

echo "Enabling Vault Transit at ${TRANSIT_MOUNT}/ in pod ${VAULT_POD}..."
kubectl -n "$NAMESPACE" exec "$VAULT_POD" -- sh -ec "
  set -euo pipefail
  export VAULT_ADDR=http://127.0.0.1:8200
  export VAULT_TOKEN='${VAULT_TOKEN}'
  vault secrets enable -path='${TRANSIT_MOUNT}' transit 2>/dev/null || true
"

echo ""
echo "OK — Vault Transit ready. Per-tenant keys (tenant-{uuid}) are created on first secret write."
