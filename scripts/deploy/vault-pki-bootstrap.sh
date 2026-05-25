#!/usr/bin/env bash
# Bootstrap Vault PKI for runner mTLS client certificates (ADR-007/008).
# Requires: vault CLI, VAULT_ADDR, VAULT_TOKEN (or dev root token).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"
PKI_MOUNT="${VAULT_PKI_MOUNT:-pki}"
RUNNER_ROLE="${VAULT_RUNNER_PKI_ROLE:-runner}"
SPIFFE_TRUST_DOMAIN="${RUNNER_GRPC_SPIFFE_TRUST_DOMAIN:-pravah.local}"
TTL="${VAULT_RUNNER_PKI_TTL:-168h}"

export VAULT_ADDR VAULT_TOKEN

echo "Waiting for Vault at ${VAULT_ADDR}..."
for _ in $(seq 1 60); do
  if vault status >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
vault status >/dev/null

echo "Enabling PKI mount ${PKI_MOUNT} (if needed)..."
vault secrets enable -path="${PKI_MOUNT}" pki 2>/dev/null || true
vault secrets tune -max-lease-ttl=87600h "${PKI_MOUNT}" >/dev/null

if ! vault read -field=certificate "${PKI_MOUNT}/cert/ca" >/dev/null 2>&1; then
  echo "Generating root CA..."
  vault write -field=certificate "${PKI_MOUNT}/root/generate/internal" \
    common_name="Pravah Runner PKI Root" ttl=87600h >/dev/null
fi

echo "Configuring runner PKI role ${RUNNER_ROLE}..."
vault write "${PKI_MOUNT}/roles/${RUNNER_ROLE}" \
  allowed_uri_sans="spiffe://${SPIFFE_TRUST_DOMAIN}/tenant/*,spiffe://${SPIFFE_TRUST_DOMAIN}/runner/*" \
  allow_any_name=true \
  allow_subdomains=true \
  key_type=rsa \
  key_bits=4096 \
  ttl="${TTL}" \
  max_ttl="${TTL}" \
  require_cn=false \
  use_csr_sans=true \
  use_csr_common_name=true

CA_PEM="${ROOT}/deploy/certs/runner-grpc/vault-pki-ca.crt"
mkdir -p "$(dirname "${CA_PEM}")"
vault read -field=certificate "${PKI_MOUNT}/cert/ca" > "${CA_PEM}"

echo ""
echo "OK — Vault PKI ready for runner client certificates"
echo "  Issue path: ${PKI_MOUNT}/issue/${RUNNER_ROLE}"
echo "  CA PEM:     ${CA_PEM} (use as runnerGrpcTls client CA / agent trust)"
echo ""
echo "Enable on runner-service:"
echo "  PRAVAH_VAULT_ENABLED=true PRAVAH_RUNNER_PKI_ENABLED=true"
echo "  PRAVAH_RUNNER_GRPC_TLS_CLIENT_CA=${CA_PEM}"
