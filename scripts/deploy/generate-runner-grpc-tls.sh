#!/usr/bin/env bash
# Generate a local CA, server cert, and client cert for runner gRPC mTLS (ADR-005/008).
# Output: deploy/certs/runner-grpc/ (gitignored)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="${RUNNER_GRPC_TLS_DIR:-$ROOT/deploy/certs/runner-grpc}"
DAYS_CA=3650
DAYS_LEAF=825
CN="${RUNNER_GRPC_TLS_CN:-pravah-runner-grpc}"
NAMESPACE="${RUNNER_GRPC_TLS_NAMESPACE:-pravah}"
FULLNAME="${RUNNER_GRPC_TLS_FULLNAME:-pravah}"
RUNNER_ID="${RUNNER_ID:-}"
TENANT_ID="${TENANT_ID:-}"
SPIFFE_TRUST_DOMAIN="${RUNNER_GRPC_SPIFFE_TRUST_DOMAIN:-pravah.local}"
CLIENT_CN="${RUNNER_ID:-runner-local-dev}"
if [[ -n "$RUNNER_ID" && -n "$TENANT_ID" ]]; then
  SPIFFE_URI="spiffe://${SPIFFE_TRUST_DOMAIN}/tenant/${TENANT_ID}/runner/${RUNNER_ID}"
elif [[ -n "$RUNNER_ID" ]]; then
  SPIFFE_URI="spiffe://${SPIFFE_TRUST_DOMAIN}/runner/${RUNNER_ID}"
else
  SPIFFE_URI="spiffe://${SPIFFE_TRUST_DOMAIN}/runner/local-dev"
fi

mkdir -p "$OUT_DIR"
cd "$OUT_DIR"

if [[ -f ca.key && -f server.crt && -f client.crt ]]; then
  echo "Certs already exist in ${OUT_DIR} (delete to regenerate)"
  exit 0
fi

echo "Generating CA..."
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days "$DAYS_CA" \
  -subj "/O=Pravah Local/CN=Pravah Runner gRPC CA" -out ca.crt

cat > server-openssl.cnf <<EOF
[req]
default_bits = 4096
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
CN = ${CN}

[req_ext]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = ${FULLNAME}-runner-service
DNS.3 = ${FULLNAME}-runner-service.${NAMESPACE}
DNS.4 = ${FULLNAME}-runner-service.${NAMESPACE}.svc
DNS.5 = ${FULLNAME}-runner-service.${NAMESPACE}.svc.cluster.local
DNS.6 = ${FULLNAME}-runner-service-grpc
DNS.7 = ${FULLNAME}-runner-service-grpc.${NAMESPACE}.svc.cluster.local
EOF

echo "Generating server certificate..."
openssl genrsa -out server.key 4096
openssl req -new -key server.key -out server.csr -config server-openssl.cnf
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days "$DAYS_LEAF" -sha256 -extensions req_ext -extfile server-openssl.cnf

cat > client-openssl.cnf <<EOF
[req]
default_bits = 4096
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = req_ext

[dn]
CN = ${CLIENT_CN}

[req_ext]
subjectAltName = @alt_names

[alt_names]
URI.1 = ${SPIFFE_URI}
EOF

echo "Generating client certificate (runner agent mTLS, SPIFFE=${SPIFFE_URI})..."
openssl genrsa -out client.key 4096
openssl req -new -key client.key -out client.csr -config client-openssl.cnf
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client.crt -days "$DAYS_LEAF" -sha256 -extensions req_ext -extfile client-openssl.cnf

# Kubernetes secret keys expected by Helm (runnerGrpcTls.*)
cp server.crt tls.crt
cp server.key tls.key

rm -f server.csr client.csr server-openssl.cnf client-openssl.cnf ca.srl

echo ""
echo "OK — certificates written to ${OUT_DIR}"
echo "  tls.crt / tls.key  — mount on runner-service (server)"
echo "  ca.crt             — client CA for mTLS"
echo "  client.crt / client.key — configure on runner agent (SPIFFE URI in SAN)"
echo ""
echo "Regenerate client cert for a registered runner:"
echo "  RUNNER_ID=<uuid> TENANT_ID=<uuid> ./scripts/deploy/generate-runner-grpc-tls.sh"
echo ""
echo "Install into cluster:"
echo "  ./scripts/deploy/k8s-local-runner-grpc-tls.sh ${NAMESPACE}"
