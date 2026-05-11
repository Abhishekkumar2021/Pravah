#!/usr/bin/env sh
# Idempotent-ish setup for local docker-compose (ADR-007 database secrets + PKI intro for ADR-008).
# Run from this exercise directory, e.g.
#   cd .../Pravah/playground/05-vault && ./vault/scripts/init-vault.sh
#
# Wait phase uses curl. Vault commands use your local Vault CLI if installed,
# otherwise they run the CLI inside the compose `vault` container.
set -eu

export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:-root}"

# Normalize no trailing slash for URLs we append paths to
BASE="${VAULT_ADDR%/}"

# standbyok/perfstandbyok map cluster standby codes to 200 so we don't spin on 429/473 forever.
# Plain /sys/health returns 503 when sealed OR still initializing — dev mode should move to 200 quickly.
HEALTH_URL="${BASE}/v1/sys/health?standbyok=true&perfstandbyok=true"

echo "Waiting for Vault HTTP at ${BASE} ..."
echo "(Tip: if this hangs, run: docker logs 05-vault-vault-1)"
sleep 2
i=0
last="000"
while true; do
  if command -v curl >/dev/null 2>&1; then
    last=$(curl -sS --connect-timeout 3 --max-time 8 -o /dev/null -w "%{http_code}" "${HEALTH_URL}" 2>/dev/null || echo "000")
    # 200 = active unsealed (or standby with standbyok); 429/472/473 = Vault answering in HA layouts
    if [ "$last" = "200" ] || [ "$last" = "429" ] || [ "$last" = "472" ] || [ "$last" = "473" ]; then
      break
    fi
    # Listener up but not ready yet (common briefly after container start)
    if [ "$last" = "503" ] && [ $((i % 15)) -eq 0 ] && [ "$i" -gt 0 ]; then
      echo "... still waiting (last HTTP ${last} — sealed/init or warming up)"
    fi
    # TCP down / connection refused
    if [ "$last" = "000" ] && [ $((i % 15)) -eq 0 ] && [ "$i" -gt 0 ]; then
      echo "... still waiting (no TCP response yet — is port ${BASE##*:} published?)"
    fi
  elif command -v vault >/dev/null 2>&1; then
    if vault status 2>/dev/null; then
      break
    fi
  else
    echo "Neither curl nor vault found in PATH. Install curl, or start Docker and wait until ${BASE} responds."
    exit 1
  fi
  i=$((i + 1))
  if [ "$i" -gt 120 ]; then
    echo "Timed out waiting for Vault at ${BASE} (last HTTP code: ${last})."
    echo "Check: docker compose ps && docker logs 05-vault-vault-1"
    exit 1
  fi
  sleep 1
done
echo "Vault responded with HTTP ${last} — continuing."

if command -v vault >/dev/null 2>&1; then
  echo "Using local vault CLI."
  vault_cli() {
    vault "$@"
  }
elif command -v docker >/dev/null 2>&1 && docker compose ps vault >/dev/null 2>&1; then
  echo "Local vault CLI not found; using vault CLI inside the Docker container."
  vault_cli() {
    docker compose exec -T \
      -e VAULT_ADDR="http://127.0.0.1:8200" \
      -e VAULT_TOKEN="${VAULT_TOKEN}" \
      vault vault "$@"
  }
else
  echo "Vault is up, but neither local vault CLI nor docker compose fallback is available."
  echo "Install: brew tap hashicorp/tap && brew install hashicorp/tap/vault"
  exit 1
fi

vault_cli secrets enable -path=database database 2>/dev/null || echo "database engine already enabled"
vault_cli secrets enable pki 2>/dev/null || echo "pki engine already enabled"

vault_cli secrets tune -max-lease-ttl=87600h pki

vault_cli write -format=json pki/root/generate/internal \
  common_name="Pravah Playground Root" \
  ttl=87600h >/dev/null

vault_cli write pki/config/urls \
  issuing_certificates="${BASE}/v1/pki/ca" \
  crl_distribution_points="${BASE}/v1/pki/crl" || true

vault_cli write pki/roles/playground \
  allowed_domains="local,playground.local" \
  allow_subdomains=true \
  max_ttl="72h" \
  ttl="24h"

vault_cli write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="playground" \
  connection_url="postgresql://postgres:postgres@postgres:5432/postgres?sslmode=disable"

vault_cli write database/roles/playground \
  db_name=postgresql \
  creation_statements='CREATE ROLE "{{name}}" WITH LOGIN PASSWORD '\''{{password}}'\'' VALID UNTIL '\''{{expiration}}'\''; GRANT CONNECT ON DATABASE postgres TO "{{name}}";' \
  revocation_statements='DROP ROLE IF EXISTS "{{name}}";' \
  default_ttl="1h" \
  max_ttl="24h"

echo "OK — try: vault read database/creds/playground"
echo "PKI: vault write pki/issue/playground common_name=svc.playground.local ttl=1h"
