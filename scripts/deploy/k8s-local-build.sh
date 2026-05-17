#!/usr/bin/env bash
# Build Pravah service images for local Kubernetes (kind / minikube).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKEND="$ROOT/backend"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-pravah}"

SERVICES=(gateway tenant-service pipeline-service execution-service scheduler-service)

echo "Building JARs and Docker images (tag: local)..."
cd "$BACKEND"

for svc in "${SERVICES[@]}"; do
  echo "==> $svc"
  ./gradlew ":services:${svc}:bootJar" --no-daemon -q
  docker build \
    -f docker/Dockerfile.service \
    --build-arg "SERVICE_NAME=${svc}" \
    -t "pravah-${svc}:local" \
    .
done

if command -v kind >/dev/null 2>&1 && kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  echo "Loading images into kind cluster '$CLUSTER_NAME'..."
  for svc in "${SERVICES[@]}"; do
    kind load docker-image "pravah-${svc}:local" --name "$CLUSTER_NAME"
  done
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
elif command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "Using minikube docker-env for image build context (rebuild if needed)."
  eval "$(minikube docker-env)"
  for svc in "${SERVICES[@]}"; do
    docker build \
      -f docker/Dockerfile.service \
      --build-arg "SERVICE_NAME=${svc}" \
      -t "pravah-${svc}:local" \
      .
  done
  echo "Done. Install with: ./scripts/deploy/k8s-local-install.sh"
else
  echo "Images built locally. Load into your cluster (kind load / minikube) before helm install."
fi
