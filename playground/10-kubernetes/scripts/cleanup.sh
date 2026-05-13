#!/bin/bash

echo "=== Cleaning up Kubernetes resources ==="

kubectl delete namespace pravah-demo --ignore-not-found

echo ""
echo "To delete the Kind cluster entirely:"
echo "  kind delete cluster --name pravah-playground"
