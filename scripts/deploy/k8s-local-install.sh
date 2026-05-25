#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RELEASE="${HELM_RELEASE:-pravah}"
NAMESPACE="${HELM_NAMESPACE:-pravah}"
CHART="$ROOT/deploy/helm/pravah-platform"

helm upgrade --install "$RELEASE" "$CHART" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  -f "$CHART/values.yaml" \
  -f "$CHART/values-local.yaml" \
  "$@"

echo ""
helm -n "$NAMESPACE" get notes "$RELEASE" 2>/dev/null || true
