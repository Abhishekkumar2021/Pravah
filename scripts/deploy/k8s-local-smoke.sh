#!/usr/bin/env bash
# Post-install smoke test for local Helm deploy (kind / minikube).
# Usage: ./scripts/deploy/k8s-local-smoke.sh [namespace]
#
# Prerequisites: helm install complete (k8s-local-install.sh), kubectl configured.
# Ports must match deploy/helm/pravah-platform/values.yaml service defaults.
set -euo pipefail

NAMESPACE="${1:-pravah}"
RELEASE="${HELM_RELEASE:-pravah}"
PREFIX="${HELM_FULLNAME:-}"
TIMEOUT="${SMOKE_TIMEOUT:-600s}"

PLATFORM_COMPONENTS=(
  gateway
  tenant-service
  pipeline-service
  execution-service
  scheduler-service
  notification-service
  connect-service
  runner-service
)

BUNDLED_COMPONENTS=(
  postgres
  kafka
  redis
  minio
  mailhog
  vault
)

log() {
  echo "[k8s-smoke] $*"
}

fail() {
  echo "[k8s-smoke] ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

resolve_prefix() {
  if [[ -n "$PREFIX" ]]; then
    return
  fi
  PREFIX="pravah"
  if command -v helm >/dev/null 2>&1 && helm status "$RELEASE" -n "$NAMESPACE" >/dev/null 2>&1; then
    if command -v jq >/dev/null 2>&1; then
      local override
      override="$(helm get values "$RELEASE" -n "$NAMESPACE" -o json | jq -r '.fullnameOverride // empty')"
      [[ -n "$override" ]] && PREFIX="$override"
    fi
  fi
}

component_exists() {
  local component="$1"
  kubectl -n "$NAMESPACE" get pods -l "app.kubernetes.io/component=${component}" --no-headers 2>/dev/null | grep -q .
}

wait_component_ready() {
  local component="$1"
  log "Waiting for ${component} pods (timeout ${TIMEOUT})..."
  kubectl -n "$NAMESPACE" wait --for=condition=ready pod \
    -l "app.kubernetes.io/component=${component}" \
    --timeout="$TIMEOUT"
}

require_cmd kubectl
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 \
  || fail "Namespace '${NAMESPACE}' not found. Run ./scripts/deploy/k8s-local-install.sh first."

resolve_prefix
log "Namespace: ${NAMESPACE}  release: ${RELEASE}  prefix: ${PREFIX}"

for component in "${PLATFORM_COMPONENTS[@]}"; do
  component_exists "$component" \
    || fail "No pods for required component '${component}'. Is the Helm release installed?"
  wait_component_ready "$component"
done

VAULT_REQUIRED=0
for component in "${BUNDLED_COMPONENTS[@]}"; do
  if component_exists "$component"; then
    wait_component_ready "$component"
    [[ "$component" == "vault" ]] && VAULT_REQUIRED=1
  else
    log "Skipping optional component ${component} (not deployed)"
  fi
done

if [[ "$VAULT_REQUIRED" == "1" ]]; then
  if kubectl -n "$NAMESPACE" get job -l app.kubernetes.io/component=vault-transit-init --no-headers 2>/dev/null | grep -q .; then
    log "Waiting for Vault Transit init job..."
    kubectl -n "$NAMESPACE" wait --for=condition=complete job -l app.kubernetes.io/component=vault-transit-init --timeout=120s \
      || fail "Vault Transit init job did not complete"
  fi
  if kubectl -n "$NAMESPACE" get job -l app.kubernetes.io/component=vault-pki-init --no-headers 2>/dev/null | grep -q .; then
    log "Waiting for Vault PKI init job..."
    kubectl -n "$NAMESPACE" wait --for=condition=complete job -l app.kubernetes.io/component=vault-pki-init --timeout=120s \
      || fail "Vault PKI init job did not complete"
  fi
fi

SMOKE_POD="pravah-smoke-$$"
log "Running in-cluster HTTP checks (pod ${SMOKE_POD})..."

cleanup_smoke_pod() {
  kubectl -n "$NAMESPACE" delete pod "$SMOKE_POD" --ignore-not-found --wait=false >/dev/null 2>&1 || true
}
trap cleanup_smoke_pod EXIT

kubectl -n "$NAMESPACE" run "$SMOKE_POD" \
  --restart=Never \
  --image=curlimages/curl:8.5.0 \
  --env="PREFIX=${PREFIX}" \
  --env="VAULT_REQUIRED=${VAULT_REQUIRED}" \
  --command -- sh -ec '
    set -euo pipefail
    check_health() {
      url="$1"
      echo "GET ${url}"
      body="$(curl -sf "${url}")"
      echo "${body}" | grep -q "\"status\":\"UP\"" || {
        echo "Expected status UP from ${url}, got: ${body}" >&2
        exit 1
      }
    }
    check_health "http://${PREFIX}-gateway:8080/actuator/health"
    check_health "http://${PREFIX}-tenant-service:8082/actuator/health"
    check_health "http://${PREFIX}-pipeline-service:8083/actuator/health"
    check_health "http://${PREFIX}-execution-service:8084/actuator/health"
    check_health "http://${PREFIX}-scheduler-service:8085/actuator/health"
    check_health "http://${PREFIX}-notification-service:8088/actuator/health"
    check_health "http://${PREFIX}-connect-service:8091/actuator/health"
    check_health "http://${PREFIX}-runner-service:8086/actuator/health"
    curl -sf "http://${PREFIX}-tenant-service:8082/.well-known/jwks.json" | grep -q "\"keys\""
    if [ "${VAULT_REQUIRED}" = "1" ]; then
      curl -sf "http://${PREFIX}-vault:8200/v1/sys/health?standbyok=true" >/dev/null
      echo "Vault health OK"
    fi
    echo "All smoke checks passed"
  '

if ! kubectl -n "$NAMESPACE" wait --for=jsonpath='{.status.phase}'=Succeeded "pod/${SMOKE_POD}" --timeout=180s; then
  log "Smoke pod failed. Logs:"
  kubectl -n "$NAMESPACE" logs "$SMOKE_POD" 2>&1 || true
  fail "In-cluster HTTP checks failed (see logs above)"
fi

kubectl -n "$NAMESPACE" logs "$SMOKE_POD"
trap - EXIT
cleanup_smoke_pod

log "Smoke test passed."
