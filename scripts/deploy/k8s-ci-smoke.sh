#!/usr/bin/env bash
# End-to-end kind smoke for CI: create cluster, build/load images, helm install, smoke test.
# Usage: ./scripts/deploy/k8s-ci-smoke.sh
#
# Env:
#   KIND_CLUSTER_NAME   (default: pravah-ci)
#   HELM_NAMESPACE      (default: pravah-ci)
#   SMOKE_TIMEOUT       (default: 900s)
#   SKIP_CLUSTER_DELETE (set to 1 to keep cluster on failure for debugging)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLUSTER="${KIND_CLUSTER_NAME:-pravah-ci}"
NAMESPACE="${HELM_NAMESPACE:-pravah-ci}"
KIND_CONFIG="${KIND_CONFIG:-$ROOT/deploy/kind/pravah-ci.yaml}"
export SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-900s}"

log() {
  echo "[k8s-ci-smoke] $*"
}

fail() {
  echo "[k8s-ci-smoke] ERROR: $*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

collect_debug_logs() {
  log "Collecting debug logs..."
  mkdir -p "$ROOT/build/k8s-ci-smoke"
  kubectl -n "$NAMESPACE" get pods -o wide >"$ROOT/build/k8s-ci-smoke/pods.txt" 2>&1 || true
  kubectl -n "$NAMESPACE" get events --sort-by='.lastTimestamp' \
    >"$ROOT/build/k8s-ci-smoke/events.txt" 2>&1 || true
  kubectl -n "$NAMESPACE" describe pods \
    >"$ROOT/build/k8s-ci-smoke/describe-pods.txt" 2>&1 || true
}

delete_cluster() {
  if [[ "${SKIP_CLUSTER_DELETE:-0}" == "1" ]]; then
    log "SKIP_CLUSTER_DELETE=1 — leaving kind cluster '${CLUSTER}' running"
    return
  fi
  if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    log "Deleting kind cluster '${CLUSTER}'..."
    kind delete cluster --name "$CLUSTER"
  fi
}

on_exit() {
  local code=$?
  if [[ "$code" -ne 0 ]]; then
    collect_debug_logs || true
  fi
  delete_cluster || true
  exit "$code"
}

require_cmd kind
require_cmd kubectl
require_cmd helm
require_cmd docker
require_cmd java

trap on_exit EXIT

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  log "Removing existing kind cluster '${CLUSTER}'..."
  kind delete cluster --name "$CLUSTER"
fi

log "Creating kind cluster '${CLUSTER}'..."
kind create cluster --name "$CLUSTER" --config "$KIND_CONFIG" --wait 300s

export KIND_CLUSTER_NAME="$CLUSTER"
export HELM_NAMESPACE="$NAMESPACE"
export HELM_RELEASE="${HELM_RELEASE:-pravah}"

log "Building and loading images..."
"$ROOT/scripts/deploy/k8s-local-build.sh"

CHART="$ROOT/deploy/helm/pravah-platform"
log "Installing Helm release into namespace '${NAMESPACE}'..."
helm upgrade --install "$HELM_RELEASE" "$CHART" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  -f "$CHART/values.yaml" \
  -f "$CHART/values-local.yaml" \
  -f "$CHART/values-ci.yaml"

log "Running post-install smoke test..."
"$ROOT/scripts/deploy/k8s-local-smoke.sh" "$NAMESPACE"

log "kind smoke passed."
