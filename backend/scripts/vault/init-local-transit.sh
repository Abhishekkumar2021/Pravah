#!/usr/bin/env bash
# Enable Vault Transit engine for tenant secret encryption (ADR-007, pathway #8).
set -euo pipefail

export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:-${VAULT_DEV_ROOT_TOKEN:-dev-root-token}}"

echo "Waiting for Vault at ${VAULT_ADDR}..."
for i in $(seq 1 60); do
  if curl -sf "${VAULT_ADDR}/v1/sys/health?standbyok=true" >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if [[ "$i" -eq 60 ]]; then
    echo "Vault not ready" >&2
    exit 1
  fi
done

# Idempotent: ignore if transit mount already exists.
if ! curl -sf -H "X-Vault-Token: ${VAULT_TOKEN}" "${VAULT_ADDR}/v1/sys/mounts/transit" >/dev/null 2>&1; then
  curl -sf -X POST \
    -H "X-Vault-Token: ${VAULT_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"type":"transit"}' \
    "${VAULT_ADDR}/v1/sys/mounts/transit" >/dev/null
fi

echo "OK — Transit engine enabled at transit/"
echo "Per-tenant keys are created on first secret write (tenant-{uuid})."
echo "Export for pipeline-service:"
echo "  export PRAVAH_VAULT_ENABLED=true"
echo "  export VAULT_ADDR=${VAULT_ADDR}"
echo "  export VAULT_TOKEN=${VAULT_TOKEN}"
