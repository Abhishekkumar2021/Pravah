#!/usr/bin/env bash
# Install Prometheus + Grafana + Jaeger for Pravah (pathway #11).
#
# Prerequisites: kubectl, helm 3.12+, cluster with enough memory (kind: 8GB+ recommended)
#
# Usage (from repo root):
#   ./scripts/deploy/k8s-local-observability.sh
#
# Then re-install or upgrade pravah with observability overlay:
#   helm upgrade --install pravah deploy/helm/pravah-platform \
#     -f deploy/helm/pravah-platform/values.yaml \
#     -f deploy/helm/pravah-platform/values-local.yaml \
#     -f deploy/helm/pravah-platform/values-observability.yaml \
#     --namespace pravah --create-namespace
#
# Access (port-forward):
#   kubectl -n monitoring port-forward svc/prometheus-grafana 3000:80
#   kubectl -n monitoring port-forward svc/jaeger-query 16686:16686
#   kubectl -n monitoring port-forward svc/prometheus-kube-prometheus-prometheus 9090:9090
#
# Grafana: http://localhost:3000  user=admin password=admin (change in prod)
# Jaeger:  http://localhost:16686
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OBS="$ROOT/deploy/observability"
NAMESPACE="${OBSERVABILITY_NAMESPACE:-monitoring}"

command -v helm >/dev/null 2>&1 || { echo "ERROR: helm not found" >&2; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl not found" >&2; exit 1; }

echo "==> Creating namespace $NAMESPACE"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "==> Loading Grafana dashboards ConfigMap"
kubectl -n "$NAMESPACE" create configmap pravah-grafana-dashboards \
  --from-file="$OBS/grafana/dashboards/" \
  --dry-run=client -o yaml \
  | kubectl label --local -f - grafana_dashboard=1 -o yaml \
  | kubectl apply -f -

echo "==> Adding Helm repos"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts 2>/dev/null || true
helm repo update

echo "==> Installing kube-prometheus-stack"
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace "$NAMESPACE" \
  --version "${KUBE_PROMETHEUS_STACK_VERSION:-65.1.1}" \
  -f "$OBS/kube-prometheus-stack-values.yaml" \
  --wait --timeout 10m

echo "==> Installing Jaeger (all-in-one + OTLP)"
helm upgrade --install jaeger jaegertracing/jaeger \
  --namespace "$NAMESPACE" \
  --version "${JAEGER_CHART_VERSION:-3.3.2}" \
  -f "$OBS/jaeger-values.yaml" \
  --wait --timeout 5m

echo "==> Applying Prometheus alert rules"
kubectl apply -f "$OBS/prometheus-rules/pravah-platform.yaml"

echo ""
echo "Observability stack installed in namespace: $NAMESPACE"
echo ""
echo "Next steps:"
echo "  1. Upgrade pravah with values-observability.yaml (see header in this script)"
echo "  2. Port-forward Grafana:  kubectl -n $NAMESPACE port-forward svc/prometheus-grafana 3000:80"
echo "  3. Port-forward Jaeger:   kubectl -n $NAMESPACE port-forward svc/jaeger-query 16686:16686"
echo "  4. Open Grafana dashboard 'Pravah Platform Overview'"
