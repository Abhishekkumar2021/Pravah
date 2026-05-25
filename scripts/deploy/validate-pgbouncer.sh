#!/usr/bin/env bash
# Validate PgBouncer Helm wiring (pathway #12, US-09.16).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHART="$ROOT/deploy/helm/pravah-platform"

log() {
  echo "[validate-pgbouncer] $*"
}

fail() {
  echo "[validate-pgbouncer] ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_cmd helm

contains() {
  [[ "$1" == *"$2"* ]]
}

log "Template local values (PgBouncer enabled)..."
LOCAL_OUT="$(helm template pravah "$CHART" \
  -f "$CHART/values.yaml" \
  -f "$CHART/values-local.yaml" \
  --namespace pravah)"

contains "$LOCAL_OUT" "name: pravah-pgbouncer" || fail "missing pgbouncer Service/Deployment"
contains "$LOCAL_OUT" "image: edoburu/pgbouncer:v1.25.1-p0" || fail "expected published edoburu/pgbouncer image tag"
contains "$LOCAL_OUT" "render-pgbouncer-etc" || fail "missing render-pgbouncer-etc init container"
contains "$LOCAL_OUT" "server_check_query = SELECT 1" || fail "missing server_check_query in pgbouncer.ini"
contains "$LOCAL_OUT" "kind: Deployment" || fail "missing Deployment manifests"
contains "$LOCAL_OUT" "prepareThreshold=0" || fail "expected prepareThreshold=0 in JDBC URLs when PgBouncer enabled"
contains "$LOCAL_OUT" "pool_mode = transaction" || fail "expected transaction poolMode"
contains "$LOCAL_OUT" "max_prepared_statements = 0" || fail "expected max_prepared_statements=0"

log "Template production values (PgBouncer + external RDS)..."
PROD_OUT="$(helm template pravah "$CHART" \
  -f "$CHART/values.yaml" \
  -f "$CHART/values-prod.yaml" \
  --namespace pravah \
  --set image.tag=ci-test-sha \
  --set externalPostgres.host=rds.example.com \
  --set externalKafka.bootstrapServers=msk.example.com:9092 \
  --set externalRedis.host=redis.example.com \
  --set smtp.host=smtp.example.com \
  --set pravah.wsAllowedOrigins=https://app.example.com \
  --set pravah.frontendBaseUrl=https://app.example.com \
  --set pravah.hooksBaseUrl=https://api.example.com/api/v1/hooks \
  --set externalArtifact.endpoint=https://resources.example.com \
  --set externalVault.address=https://vault.example.com \
  --set pravah.internalServiceSecret=ci-secret \
  --set pravah.runnerBootstrapSecret=ci-runner-secret)"

contains "$PROD_OUT" "jdbc:postgresql://" || fail "prod template missing JDBC URLs"
contains "$PROD_OUT" "-pgbouncer:6432/" || fail "prod JDBC should route via pgbouncer"
contains "$PROD_OUT" "host=rds.example.com port=5432" || fail "pgbouncer backend should target external RDS"

log "PgBouncer disabled by default in base values..."
BASE_OUT="$(helm template pravah "$CHART" \
  -f "$CHART/values.yaml" \
  --namespace pravah \
  --set postgres.enabled=false \
  --set kafka.enabled=false \
  --set redis.enabled=false \
  --set minio.enabled=false \
  --set pravah.internalServiceSecret=ci-secret \
  --set externalPostgres.host=rds.example.com \
  --set externalKafka.bootstrapServers=msk.example.com:9092 \
  --set externalRedis.host=redis.example.com \
  --set externalArtifact.endpoint=https://s3.example.com)"

if contains "$BASE_OUT" "pravah-pgbouncer"; then
  fail "pgbouncer resources should not render when pgbouncer.enabled=false"
fi
contains "$BASE_OUT" "jdbc:postgresql://rds.example.com:5432/" || fail "direct RDS JDBC when pgbouncer disabled"

log "OK — PgBouncer Helm manifests valid"
