# Pravah observability stack (pathway #11)

Prometheus metrics, Grafana dashboards, and Jaeger distributed tracing for the Pravah platform (T-09.07 / US-09.14).

## What ships

| Component | Purpose |
|-----------|---------|
| **kube-prometheus-stack** | Prometheus + Grafana + Alertmanager + ServiceMonitor CRDs |
| **Jaeger all-in-one** | OTLP trace ingestion (HTTP `:4318`, gRPC `:4317`) |
| **ServiceMonitors** | Scrape `/actuator/prometheus` on all 8 platform services |
| **Grafana dashboard** | `Pravah Platform Overview` — rate limits, outbox DLT, JVM, circuit breakers |
| **Alert rules (example)** | `grafana/provisioning/alerting/pravah-platform.yml` |

Platform services expose Micrometer metrics via Spring Boot Actuator. When `observability.tracing.enabled=true`, traces export to Jaeger via OTLP.

## Quick install (Kubernetes)

```bash
# 1. Install monitoring stack (once per cluster)
./scripts/deploy/k8s-local-observability.sh

# 2. Upgrade Pravah with observability overlay
helm upgrade --install pravah deploy/helm/pravah-platform \
  -f deploy/helm/pravah-platform/values.yaml \
  -f deploy/helm/pravah-platform/values-local.yaml \
  -f deploy/helm/pravah-platform/values-observability.yaml \
  --namespace pravah --create-namespace

# 3. Access UIs
kubectl -n monitoring port-forward svc/prometheus-grafana 3000:80
kubectl -n monitoring port-forward svc/jaeger-query 16686:16686
```

Grafana: `http://localhost:3000` — user `admin` / password `admin` (change in production).

## Docker Compose (bare metal local)

Jaeger is included in `backend/docker-compose.yml`. To enable tracing on Java services:

```bash
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
export MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces
make local-services
```

Jaeger UI: `http://localhost:16686`

## Production

1. Install kube-prometheus-stack in a dedicated `monitoring` namespace (same values file; tighten Grafana admin password).
2. Install Jaeger or use a managed tracing backend (Tempo, AWS X-Ray via OTel Collector).
3. Deploy Pravah with `values-prod.yaml` + `values-observability.yaml` (set `observability.tracing.endpoint` to your collector).
4. Import or sync alert rules from `grafana/provisioning/alerting/pravah-platform.yml`.

## Validation

```bash
make validate-observability
```

## Key metrics

| Metric | Description |
|--------|-------------|
| `pravah_ratelimit_requests_total` | Rate limit outcomes (`allowed`, `denied`, `fail_open`) |
| `pravah_outbox_dead_lettered_total` | Outbox messages moved to dead letter |
| `resilience4j_circuitbreaker_state` | Circuit breaker state per backend |
| `http_server_requests_seconds_*` | HTTP latency and throughput |
| `jvm_memory_used_bytes` | JVM heap/non-heap |

**Prometheus alert rules:** `deploy/observability/prometheus-rules/pravah-platform.yaml` (fail-open, outbox DLT). Applied by `k8s-local-observability.sh`.

See [Deployment Guide](../../docs/deployment/DEPLOYMENT_GUIDE.md) for full local and cloud instructions.
