#!/usr/bin/env bash
# Bootstrap Vault PKI in a running cluster and patch runner gRPC TLS secret with PKI CA.
# Usage: ./scripts/deploy/k8s-local-vault-pki.sh [namespace]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAMESPACE="${1:-pravah}"
SECRET_NAME="${RUNNER_GRPC_TLS_SECRET:-pravah-runner-grpc-tls}"
CERT_DIR="${RUNNER_GRPC_TLS_DIR:-$ROOT/deploy/certs/runner-grpc}"
SPIFFE_TRUST_DOMAIN="${RUNNER_GRPC_SPIFFE_TRUST_DOMAIN:-pravah.local}"
PKI_TTL="${VAULT_RUNNER_PKI_TTL:-168h}"

VAULT_POD="$(kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/component=vault -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
[[ -n "$VAULT_POD" ]] || { echo "No Vault pod in namespace ${NAMESPACE}" >&2; exit 1; }

CREDS_SECRET="$(kubectl -n "$NAMESPACE" get secrets -o name | grep -E 'pravah.*' | head -1 | sed 's|secret/||' || true)"
[[ -n "$CREDS_SECRET" ]] || CREDS_SECRET="pravah"
VAULT_TOKEN="$(kubectl -n "$NAMESPACE" get secret "$CREDS_SECRET" -o jsonpath='{.data.vault-dev-root-token}' | base64 -d)"

echo "Bootstrapping Vault PKI in pod ${VAULT_POD}..."
kubectl -n "$NAMESPACE" exec "$VAULT_POD" -- sh -ec "
  set -euo pipefail
  export VAULT_ADDR=http://127.0.0.1:8200
  export VAULT_TOKEN='${VAULT_TOKEN}'
  vault secrets enable -path=pki pki 2>/dev/null || true
  vault secrets tune -max-lease-ttl=87600h pki >/dev/null
  if ! vault read -field=certificate pki/cert/ca >/dev/null 2>&1; then
    vault write -field=certificate pki/root/generate/internal common_name='Pravah Runner PKI Root' ttl=87600h >/dev/null
  fi
  vault write pki/roles/runner \
    allowed_uri_sans='spiffe://${SPIFFE_TRUST_DOMAIN}/tenant/*,spiffe://${SPIFFE_TRUST_DOMAIN}/runner/*' \
    allow_any_name=true allow_subdomains=true key_type=rsa key_bits=4096 \
    ttl='${PKI_TTL}' max_ttl='${PKI_TTL}' require_cn=false use_csr_sans=true use_csr_common_name=true
"

mkdir -p "$CERT_DIR"
kubectl -n "$NAMESPACE" exec "$VAULT_POD" -- sh -ec \
  "export VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN='${VAULT_TOKEN}'; vault read -field=certificate pki/cert/ca" \
  > "${CERT_DIR}/ca.crt"

if [[ ! -f "${CERT_DIR}/tls.crt" ]]; then
  echo "Server TLS certs missing; run ./scripts/deploy/generate-runner-grpc-tls.sh first." >&2
  exit 1
fi

kubectl -n "$NAMESPACE" create secret generic "$SECRET_NAME" \
  --from-file=tls.crt="${CERT_DIR}/tls.crt" \
  --from-file=tls.key="${CERT_DIR}/tls.key" \
  --from-file=ca.crt="${CERT_DIR}/ca.crt" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "OK — Vault PKI bootstrapped; ${SECRET_NAME} updated with PKI CA as ca.crt"
