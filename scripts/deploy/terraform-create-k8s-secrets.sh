#!/usr/bin/env bash
# Create core Kubernetes secrets from Terraform outputs (pathway #10).
#
# Usage:
#   aws eks update-kubeconfig ...   # first
#   ./scripts/deploy/terraform-create-k8s-secrets.sh [namespace]
#
# Env:
#   TF_DIR              Terraform root (default: deploy/terraform/aws/reference)
#   INTERNAL_SERVICE_SECRET  Override (generated if unset)
#   RUNNER_BOOTSTRAP_SECRET  Override (generated if unset)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TF_DIR="${TF_DIR:-$ROOT/deploy/terraform/aws/reference}"
NAMESPACE="${1:-pravah}"

log() { echo "[terraform-k8s-secrets] $*"; }
fail() { echo "[terraform-k8s-secrets] ERROR: $*" >&2; exit 1; }

command -v kubectl >/dev/null 2>&1 || fail "kubectl is required"
command -v terraform >/dev/null 2>&1 || fail "terraform is required"

cd "$TF_DIR"

POSTGRES_USER="$(terraform output -raw postgres_username)"
POSTGRES_PASS="$(terraform output -raw postgres_password)"
REDIS_PASS="$(terraform output -raw redis_password)"
INTERNAL_SECRET="${INTERNAL_SERVICE_SECRET:-$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)}"
RUNNER_SECRET="${RUNNER_BOOTSTRAP_SECRET:-$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)}"

log "Creating namespace ${NAMESPACE} (if missing)..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

log "Applying secrets in ${NAMESPACE}..."
kubectl -n "$NAMESPACE" create secret generic pravah-postgres-credentials \
  --from-literal=username="$POSTGRES_USER" \
  --from-literal=password="$POSTGRES_PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic pravah-redis-credentials \
  --from-literal=password="$REDIS_PASS" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n "$NAMESPACE" create secret generic pravah-credentials \
  --from-literal=postgres-username="$POSTGRES_USER" \
  --from-literal=postgres-password="$POSTGRES_PASS" \
  --from-literal=internal-service-secret="$INTERNAL_SECRET" \
  --from-literal=runner-bootstrap-secret="$RUNNER_SECRET" \
  --dry-run=client -o yaml | kubectl apply -f -

log "OK — postgres, redis, and pravah-credentials secrets applied."
log "Note: S3 uses IRSA when externalArtifact.irsa.enabled=true (no static artifact secret)."
