#!/usr/bin/env bash
# Seed a demo KV secret for local pipeline connections (Vault dev mode enables secret/ automatically).
set -euo pipefail

export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:-${VAULT_DEV_ROOT_TOKEN:-dev-root-token}}"

DEMO_PASSWORD="${PRAVAH_DEMO_DB_PASSWORD:-pravah-local-db-password}"

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

# Dev server pre-mounts KV v2 at secret/; PUT is idempotent for demo data.
curl -sf -X POST \
  -H "X-Vault-Token: ${VAULT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"data\":{\"password\":\"${DEMO_PASSWORD}\"}}" \
  "${VAULT_ADDR}/v1/secret/data/pravah/demo-db" >/dev/null

echo "OK — demo secret at vault:secret/data/pravah/demo-db#password"
echo "Export for pipeline-service:"
echo "  export PRAVAH_VAULT_ENABLED=true"
echo "  export VAULT_ADDR=${VAULT_ADDR}"
echo "  export VAULT_TOKEN=${VAULT_TOKEN}"
