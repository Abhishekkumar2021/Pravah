#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Pravah Kubernetes + KEDA Playground Setup ==="

# 1. Build the Java app
echo "1. Building Java application..."
cd "$PROJECT_DIR/app"
./gradlew bootJar --no-daemon

# 2. Build Docker image
echo "2. Building Docker image..."
docker build -t pravah-job-consumer:latest .

# 3. Load image into Kind cluster
echo "3. Loading image into Kind cluster..."
kind load docker-image pravah-job-consumer:latest --name pravah-playground

# 4. Apply Kubernetes manifests
echo "4. Applying Kubernetes manifests..."
cd "$PROJECT_DIR/manifests"
kubectl apply -f namespace.yaml
kubectl apply -f kafka.yaml

echo "5. Waiting for Kafka to be ready..."
kubectl wait --for=condition=available deployment/kafka -n pravah-demo --timeout=120s

echo "6. Deploying consumer and KEDA ScaledObject..."
kubectl apply -f consumer-deployment.yaml
kubectl apply -f keda-scaledobject.yaml

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Check deployment status:"
echo "  kubectl get pods -n pravah-demo"
echo ""
echo "Check KEDA ScaledObject:"
echo "  kubectl get scaledobject -n pravah-demo"
echo ""
echo "To trigger scaling, run:"
echo "  kubectl apply -f producer-job.yaml"
echo ""
echo "Watch pods scale:"
echo "  kubectl get pods -n pravah-demo -w"
