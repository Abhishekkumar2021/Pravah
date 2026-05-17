#!/usr/bin/env bash
# Build Pravah service images for local Kubernetes (kind / minikube).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND="$ROOT/backend"

SERVICES=(gateway tenant-service pipeline-service execution-service scheduler-service)

detect_kind_cluster() {
  if [[ -n "${KIND_CLUSTER_NAME:-}" ]]; then
    echo "$KIND_CLUSTER_NAME"
    return 0
  fi
  local ctx
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  if [[ "$ctx" =~ ^kind-(.+)$ ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi
  if kind get clusters 2>/dev/null | grep -qx kind; then
    echo kind
    return 0
  fi
  return 1
}

build_images() {
  local tag=$1
  cd "$BACKEND"
  for svc in "${SERVICES[@]}"; do
    echo "==> $svc"
    ./gradlew ":services:${svc}:bootJar" --no-daemon -q
    docker build \
      -f docker/Dockerfile.service \
      --build-arg "SERVICE_NAME=${svc}" \
      -t "pravah-${svc}:${tag}" \
      .
  done
}

load_into_kind() {
  local cluster=$1
  echo "Loading images into kind cluster '$cluster'..."
  for svc in "${SERVICES[@]}"; do
    kind load docker-image "pravah-${svc}:local" --name "$cluster"
  done
}

echo "Building JARs and Docker images (tag: local)..."

if command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "Building inside minikube docker-env..."
  eval "$(minikube docker-env)"
  build_images local
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
elif kind_cluster="$(detect_kind_cluster)"; then
  build_images local
  load_into_kind "$kind_cluster"
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
else
  build_images local
  echo "Images built on host."
  echo "Load into kind:  kind load docker-image pravah-gateway:local --name <cluster>"
  echo "Or set KIND_CLUSTER_NAME and re-run this script."
fi
