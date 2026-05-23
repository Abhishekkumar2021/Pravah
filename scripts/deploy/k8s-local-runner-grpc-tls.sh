#!/usr/bin/env bash
# Create/update Kubernetes Secret for runner gRPC mTLS (after generate-runner-grpc-tls.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAMESPACE="${1:-pravah}"
SECRET_NAME="${RUNNER_GRPC_TLS_SECRET:-pravah-runner-grpc-tls}"
CERT_DIR="${RUNNER_GRPC_TLS_DIR:-$ROOT/deploy/certs/runner-grpc}"

for f in tls.crt tls.key ca.crt; do
  [[ -f "${CERT_DIR}/${f}" ]] || {
    echo "Missing ${CERT_DIR}/${f}. Run ./scripts/deploy/generate-runner-grpc-tls.sh first." >&2
    exit 1
  }
done

kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 \
  || { echo "Namespace ${NAMESPACE} not found" >&2; exit 1; }

kubectl -n "$NAMESPACE" create secret generic "$SECRET_NAME" \
  --from-file=tls.crt="${CERT_DIR}/tls.crt" \
  --from-file=tls.key="${CERT_DIR}/tls.key" \
  --from-file=ca.crt="${CERT_DIR}/ca.crt" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Secret ${SECRET_NAME} applied in namespace ${NAMESPACE}."
echo "Enable in Helm: runnerGrpcTls.enabled=true runnerGrpcTls.existingSecret=${SECRET_NAME}"
