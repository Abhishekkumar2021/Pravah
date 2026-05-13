#!/bin/bash
set -e

echo "=== Triggering KEDA Scaling ==="

# Delete previous job if exists
kubectl delete job message-producer -n pravah-demo 2>/dev/null || true

# Create producer job
kubectl apply -f "$(dirname "$0")/../manifests/producer-job.yaml"

echo ""
echo "Producer job started. Watch the scaling:"
echo ""
echo "  kubectl get pods -n pravah-demo -w"
echo ""
echo "Or check HPA status:"
echo ""
echo "  kubectl get hpa -n pravah-demo"
