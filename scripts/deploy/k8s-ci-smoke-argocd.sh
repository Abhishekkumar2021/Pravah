#!/usr/bin/env bash
# End-to-end kind smoke via Argo CD GitOps (pathway #9).
# Creates cluster, loads images, installs Argo CD, syncs Application from Git, smoke test.
#
# Env:
#   KIND_CLUSTER_NAME        (default: pravah-ci)
#   HELM_NAMESPACE           (default: pravah-ci)
#   ARGOCD_TARGET_REVISION   Git ref for Application (default: develop; CI sets GITHUB_HEAD_REF)
#   SMOKE_TIMEOUT            (default: 900s)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLUSTER="${KIND_CLUSTER_NAME:-pravah-ci}"
NAMESPACE="${HELM_NAMESPACE:-pravah-ci}"
TARGET_REVISION="${ARGOCD_TARGET_REVISION:-develop}"
export SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-900s}"

log() { echo "[k8s-ci-smoke-argocd] $*"; }
fail() { echo "[k8s-ci-smoke-argocd] ERROR: $*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

collect_debug_logs() {
  mkdir -p "$ROOT/build/k8s-ci-smoke-argocd"
  kubectl -n "$NAMESPACE" get pods -o wide >"$ROOT/build/k8s-ci-smoke-argocd/pods.txt" 2>&1 || true
  kubectl -n argocd get applications >"$ROOT/build/k8s-ci-smoke-argocd/argocd-apps.txt" 2>&1 || true
  kubectl -n argocd describe application pravah-platform-local \
    >"$ROOT/build/k8s-ci-smoke-argocd/argocd-app-describe.txt" 2>&1 || true
}

delete_cluster() {
  if [[ "${SKIP_CLUSTER_DELETE:-0}" == "1" ]]; then
    log "SKIP_CLUSTER_DELETE=1 — leaving kind cluster '${CLUSTER}' running"
    return
  fi
  if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
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
  kind delete cluster --name "$CLUSTER"
fi

KIND_CONFIG="${KIND_CONFIG:-$ROOT/deploy/kind/pravah-ci.yaml}"
log "Creating kind cluster '${CLUSTER}'..."
kind create cluster --name "$CLUSTER" --config "$KIND_CONFIG" --wait 300s

export KIND_CLUSTER_NAME="$CLUSTER"
export HELM_NAMESPACE="$NAMESPACE"
log "Building and loading images..."
"$ROOT/scripts/deploy/k8s-local-build.sh"

log "Installing Argo CD and syncing from Git ref '${TARGET_REVISION}'..."
ARGOCD_NAMESPACE=argocd \
  ARGOCD_APP_NAME=pravah-platform-local \
  ARGOCD_TARGET_REVISION="$TARGET_REVISION" \
  "$ROOT/scripts/deploy/k8s-local-argocd.sh" "$NAMESPACE"

log "Running post-sync smoke test..."
"$ROOT/scripts/deploy/k8s-local-smoke.sh" "$NAMESPACE"

log "Argo CD kind smoke passed."
