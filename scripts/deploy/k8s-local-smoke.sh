#!/usr/bin/env bash
# Post-install smoke test for local Helm deploy (kind / minikube).
# Usage: ./scripts/deploy/k8s-local-smoke.sh [namespace]
#
# Prerequisites: helm install complete (k8s-local-install.sh), kubectl configured.
set -euo pipefail

NAMESPACE="${1:-pravah}"
PREFIX="${HELM_FULLNAME:-pravah}"
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

log "Namespace: ${NAMESPACE}  prefix: ${PREFIX}"

for component in "${PLATFORM_COMPONENTS[@]}"; do
  wait_component_ready "$component"
done

for component in "${BUNDLED_COMPONENTS[@]}"; do
  if component_exists "$component"; then
    wait_component_ready "$component"
  else
    log "Skipping optional component ${component} (not deployed)"
  fi
done

SMOKE_POD="pravah-smoke-$$"
log "Running in-cluster HTTP checks..."

kubectl -n "$NAMESPACE" run "$SMOKE_POD" --rm -i --restart=Never \
  --image=curlimages/curl:8.5.0 \
  -- sh -ec "
    set -e
    check() {
      url=\$1
      echo \"GET \$url\"
      curl -sf \"\$url\" >/dev/null
    }
    check http://${PREFIX}-gateway:8080/actuator/health
    check http://${PREFIX}-tenant-service:8082/actuator/health
    check http://${PREFIX}-pipeline-service:8083/actuator/health
    check http://${PREFIX}-execution-service:8084/actuator/health
    check http://${PREFIX}-scheduler-service:8085/actuator/health
    check http://${PREFIX}-notification-service:8088/actuator/health
    check http://${PREFIX}-connect-service:8091/actuator/health
    check http://${PREFIX}-runner-service:8086/actuator/health
    curl -sf http://${PREFIX}-tenant-service:8082/.well-known/jwks.json | grep -q '\"keys\"'
    if curl -sf http://${PREFIX}-vault:8200/v1/sys/health?standbyok=true >/dev/null 2>&1; then
      echo 'Vault health OK'
    fi
    echo 'All smoke checks passed'
  "

log "Smoke test passed."
