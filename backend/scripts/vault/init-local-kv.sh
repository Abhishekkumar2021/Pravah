#!/usr/bin/env bash
# Enable KV v2 at secret/ and seed a demo credential for local pipeline connections.
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

vault secrets enable -path=secret kv-v2 2>/dev/null || echo "secret/ already enabled"

vault kv put secret/pravah/demo-db password="${PRAVAH_DEMO_DB_PASSWORD:-pravah-local-db-password}" >/dev/null

echo "OK — demo secret at vault:secret/data/pravah/demo-db#password"
echo "Export for pipeline-service:"
echo "  export PRAVAH_VAULT_ENABLED=true"
echo "  export VAULT_ADDR=${VAULT_ADDR}"
echo "  export VAULT_TOKEN=${VAULT_TOKEN}"
