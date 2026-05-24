#!/usr/bin/env bash
# Validate observability Helm values and dashboard JSON (pathway #11).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OBS="$ROOT/deploy/observability"

echo "Checking observability YAML files..."
for f in "$OBS/kube-prometheus-stack-values.yaml" "$OBS/jaeger-values.yaml"; do
  [[ -f "$f" ]] || { echo "MISSING $f" >&2; exit 1; }
  echo "OK  $f"
done

echo "Checking Grafana dashboard JSON..."
python3 -c "import json; json.load(open('$OBS/grafana/dashboards/pravah-platform-overview.json'))"
echo "OK  pravah-platform-overview.json"

echo "Checking Prometheus alert rules..."
[[ -f "$OBS/prometheus-rules/pravah-platform.yaml" ]] && echo "OK  prometheus-rules/pravah-platform.yaml"

echo "Checking pravah-platform observability overlay..."
helm template pravah "$ROOT/deploy/helm/pravah-platform" \
  -f "$ROOT/deploy/helm/pravah-platform/values.yaml" \
  -f "$ROOT/deploy/helm/pravah-platform/values-local.yaml" \
  -f "$ROOT/deploy/helm/pravah-platform/values-observability.yaml" \
  --namespace pravah >/dev/null
echo "OK  helm template with values-observability.yaml"

echo "OK — observability manifests valid"
