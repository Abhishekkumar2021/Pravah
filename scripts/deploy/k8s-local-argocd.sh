#!/usr/bin/env bash
# Install Argo CD and apply Pravah GitOps Applications (pathway #9, ADR-010).
#
# Prerequisites: kubectl context pointing at kind/minikube; images loaded (k8s-local-build.sh).
# Argo CD syncs from Git (develop branch) — unpushed local chart changes are not applied.
#
# Usage: ./scripts/deploy/k8s-local-argocd.sh [namespace]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PRAVAH_NAMESPACE="${1:-pravah}"
ARGOCD_NAMESPACE="${ARGOCD_NAMESPACE:-argocd}"
ARGOCD_VERSION="${ARGOCD_VERSION:-v2.13.3}"
APP_NAME="${ARGOCD_APP_NAME:-pravah-platform-local}"
INSTALL_MANIFEST="https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

log() { echo "[argocd] $*"; }
fail() { echo "[argocd] ERROR: $*" >&2; exit 1; }

command -v kubectl >/dev/null 2>&1 || fail "kubectl is required"

if ! kubectl cluster-info >/dev/null 2>&1; then
  fail "kubectl is not connected to a cluster"
fi

log "Installing Argo CD ${ARGOCD_VERSION} into namespace ${ARGOCD_NAMESPACE}..."
kubectl create namespace "$ARGOCD_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n "$ARGOCD_NAMESPACE" -f "$INSTALL_MANIFEST"

log "Waiting for Argo CD server..."
kubectl wait --for=condition=available deployment/argocd-server \
  -n "$ARGOCD_NAMESPACE" --timeout=300s

log "Applying AppProject and Application manifests..."
kubectl apply -f "$ROOT/deploy/argocd/appproject-pravah.yaml"

TARGET_REVISION="${ARGOCD_TARGET_REVISION:-develop}"
APP_MANIFEST="$ROOT/deploy/argocd/applications/${APP_NAME}.yaml"
export PRAVAH_NAMESPACE TARGET_REVISION
kubectl apply -f <(
  ruby -ryaml - "$APP_MANIFEST" <<'RUBY'
doc = YAML.load_file(ARGV[0])
doc["spec"]["destination"]["namespace"] = ENV.fetch("PRAVAH_NAMESPACE")
doc["spec"]["source"]["targetRevision"] = ENV.fetch("TARGET_REVISION")
print doc.to_yaml
RUBY
)

log "Waiting for Application ${APP_NAME} to sync (timeout 15m)..."
for attempt in $(seq 1 90); do
  if ! kubectl get application "$APP_NAME" -n "$ARGOCD_NAMESPACE" >/dev/null 2>&1; then
    sleep 10
    continue
  fi
  sync_status="$(kubectl get application "$APP_NAME" -n "$ARGOCD_NAMESPACE" \
    -o jsonpath='{.status.sync.status}' 2>/dev/null || true)"
  health_status="$(kubectl get application "$APP_NAME" -n "$ARGOCD_NAMESPACE" \
    -o jsonpath='{.status.health.status}' 2>/dev/null || true)"
  log "  sync=${sync_status:-Pending} health=${health_status:-Unknown} (attempt ${attempt}/90)"
  if [[ "$sync_status" == "Synced" && "$health_status" == "Healthy" ]]; then
    log "Application is Synced and Healthy"
    echo ""
    echo "OK — Argo CD GitOps active for namespace ${PRAVAH_NAMESPACE}"
    echo "  UI: kubectl port-forward svc/argocd-server -n ${ARGOCD_NAMESPACE} 8080:443"
    echo "  Admin password: kubectl -n ${ARGOCD_NAMESPACE} get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo"
    echo "  Smoke: ./scripts/deploy/k8s-local-smoke.sh ${PRAVAH_NAMESPACE}"
    exit 0
  fi
  sleep 10
done

fail "Application ${APP_NAME} did not become Synced/Healthy within timeout"
kubectl get application "$APP_NAME" -n "$ARGOCD_NAMESPACE" -o yaml >&2 || true
exit 1
