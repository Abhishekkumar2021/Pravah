#!/usr/bin/env bash
# Build Pravah service images for local Kubernetes (kind / minikube).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND="$ROOT/backend"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-pravah}"

SERVICES=(gateway tenant-service pipeline-service execution-service scheduler-service)

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

echo "Building JARs and Docker images (tag: local)..."

if command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "Building inside minikube docker-env..."
  eval "$(minikube docker-env)"
  build_images local
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
elif command -v kind >/dev/null 2>&1 && kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  build_images local
  echo "Loading images into kind cluster '$CLUSTER_NAME'..."
  for svc in "${SERVICES[@]}"; do
    kind load docker-image "pravah-${svc}:local" --name "$CLUSTER_NAME"
  done
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
else
  build_images local
  echo "Images built on host. Load into your cluster (kind load / minikube docker-env) before helm install."
fi
