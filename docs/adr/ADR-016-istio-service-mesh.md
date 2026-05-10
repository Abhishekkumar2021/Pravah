# ADR-016: Istio Service Mesh

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

ADR-008 established mTLS for service-to-service communication using `cert-manager` and application-level TLS configuration. At the time, a service mesh was explicitly deferred: "the operational overhead of a service mesh at early scale is high."

Pravah's architecture has since grown to 11 services. The manual mTLS approach has the following limitations at this scale:

- **Each service owns its TLS configuration** — every new service must implement its own Netty/gRPC TLS setup, load certificates from Vault Agent, and configure trust anchors. This is boilerplate that scales linearly with service count and is prone to misconfiguration.
- **No L7 traffic policy** — NetworkPolicy (ADR-010) operates at L3/L4 (IP + port). It cannot enforce "the Execution Service may only call the Pipeline Service's `GetPipeline` method, not `DeletePipeline`." Istio's `AuthorizationPolicy` enforces at the HTTP method and gRPC method level.
- **No traffic management** — canary routing, circuit breaking, and retry policies must be configured in application code (Resilience4j). At 11 services, this is inconsistent and hard to audit.
- **No automatic telemetry** — each service must instrument its own outbound call latency metrics and traces. Istio's Envoy sidecar automatically captures latency, error rate, and throughput for every service-to-service call.
- **Certificate rotation coordination** — with `cert-manager`, each service's certificate rotation requires a pod restart or dynamic file reload. Istio handles certificate rotation transparently via SPIFFE/SPIRE without any application involvement.

The scale Pravah has reached (11 services, gRPC + HTTP/2, multi-protocol routing, cross-service auth at method granularity) is exactly the scale a service mesh is designed for.

---

## Decision

Adopt **Istio** as the service mesh for all internal Pravah service-to-service communication.

**What Istio replaces:**

- Application-level TLS configuration in each service → **Istio Envoy sidecar handles mTLS transparently**
- Manual certificate loading from Vault Agent for internal services → **Istio's SPIFFE-based identity, certs auto-rotated by `istiod`**
- Resilience4j circuit breakers for inter-service calls → **Istio `DestinationRule` outlier detection**
- Manual Prometheus instrumentation for inter-service latency → **Envoy sidecar auto-exports RED metrics**

**What Istio does NOT replace:**

- Vault Agent for application secrets (DB credentials, JWT keys, API keys) — Vault manages business secrets
- cert-manager for external-facing TLS (Ingress, runner gRPC endpoint) — runner certificates are issued by Vault PKI, not Istio's CA
- mTLS on the runner ↔ cloud gRPC channel — runners are external to the mesh; they use Vault-issued certificates directly

**Mesh configuration:**

```yaml
apiVersion: install.istio.io/v1alpha1
kind: IstioOperator
metadata:
  name: pravah-istio
spec:
  profile: default
  meshConfig:
    # Enforce mTLS for all services in the mesh
    peerAuthentication:
      mtls:
        mode: STRICT
    # Enable access logging for all services
    accessLogFile: /dev/stdout
    # Enable distributed tracing via OpenTelemetry Collector
    enableTracing: true
    defaultConfig:
      tracing:
        zipkin:
          address: otel-collector.monitoring.svc:9411
  components:
    pilot:
      k8s:
        resources:
          requests: { cpu: "500m", memory: "2Gi" }
          limits:   { cpu: "1",    memory: "4Gi" }
```

**PeerAuthentication — enforce STRICT mTLS across the namespace:**

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: pravah-default
  namespace: pravah
spec:
  mtls:
    mode: STRICT   # Reject any plaintext inter-service traffic
```

**AuthorizationPolicy — method-level access control (L7):**

```yaml
# Only Execution Service can call Runner Service
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: runner-service-authz
  namespace: pravah
spec:
  selector:
    matchLabels:
      app: runner-service
  rules:
    - from:
        - source:
            principals:
              - "cluster.local/ns/pravah/sa/execution-service"
      to:
        - operation:
            methods: ["POST"]
            paths: ["/pravah.runner.v1.RunnerService/*"]
```

**DestinationRule — circuit breaking and load balancing:**

```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: execution-service
spec:
  host: execution-service.pravah.svc.cluster.local
  trafficPolicy:
    connectionPool:
      http:
        http2MaxRequests: 1000
        maxRequestsPerConnection: 100
    outlierDetection:
      consecutiveGatewayErrors: 5
      interval: 10s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
```

**Sidecar injection:**

```yaml
# Label the namespace for automatic sidecar injection
apiVersion: v1
kind: Namespace
metadata:
  name: pravah
  labels:
    istio-injection: enabled
```

All pods in the `pravah` namespace automatically get an Envoy sidecar injected at pod creation. No application code changes required.

**Telemetry integration:**

Istio exports metrics in Prometheus format from every Envoy sidecar. This automatically gives Pravah:
- `istio_requests_total` — request count with source, destination, response code labels
- `istio_request_duration_milliseconds` — latency histogram per service pair
- `istio_tcp_connections_opened_total` — TCP connection tracking

These metrics appear in Grafana alongside application-level metrics without any additional instrumentation.

---

## Consequences

### Positive

- **Zero application code for mTLS** — every new service gets mTLS for free at pod creation. No TLS boilerplate, no certificate loading, no trust anchor configuration. The mesh enforces it.
- **Method-level authorization** — `AuthorizationPolicy` enforces which service can call which gRPC method. A compromised Notification Service cannot call Execution Service APIs even if it is inside the mesh.
- **Automatic RED metrics** — Rate, Errors, Duration per service pair captured automatically by Envoy. The inter-service call graph is always visible in Grafana without additional instrumentation.
- **Traffic management without code changes** — circuit breaking, retries, and timeout policies are mesh configuration, not application code. Changing a timeout requires a YAML update, not a service deployment.
- **Consistent certificate rotation** — `istiod` rotates SPIFFE certificates for all services every 24 hours. No pod restarts, no coordination, completely transparent.
- **Distributed tracing propagation** — Envoy automatically propagates trace context (B3 / W3C TraceContext headers) on every inter-service call. Services only need to forward headers — no manual propagation code.

### Negative

- **Envoy sidecar resource overhead** — every pod runs an Envoy sidecar consuming approximately 50m CPU and 64Mi memory at idle. At 50 pods, this is 2.5 CPU cores and 3.2Gi memory permanently allocated to mesh overhead.
- **Increased pod startup time** — Envoy must be ready before the application container can receive traffic. Startup time increases by ~2 seconds per pod.
- **Debugging complexity** — network issues now involve Envoy proxy logs in addition to application logs. A misconfigured `AuthorizationPolicy` that blocks traffic looks like a connection refused from the application's perspective.
- **`istiod` is a critical control plane component** — if `istiod` is unavailable, certificate rotation stops (existing certs remain valid for their remaining lifetime, typically 24 hours) and new pods cannot receive certificates. `istiod` must be highly available (2+ replicas).
- **Learning curve** — `VirtualService`, `DestinationRule`, `AuthorizationPolicy`, `PeerAuthentication`, and `Gateway` resources require understanding. Misconfiguration is common at first.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `istiod` unavailable — new pods cannot join mesh | `istiod` runs 2 replicas with PodDisruptionBudget; existing pods continue with cached certs |
| Misconfigured `AuthorizationPolicy` blocks legitimate traffic | Staging environment validates all policies before production; `istioctl analyze` in CI |
| Envoy sidecar injection skipped on a pod (missing namespace label) | CI validates all `pravah` namespace pods have Envoy sidecar via `istioctl ps` check |
| Traffic management config creates unintended routing | Argo CD preview shows mesh config diff before apply; mesh config is version-controlled |

---

## Alternatives Considered

### cert-manager Only (Maintained from ADR-008)

Keep the current approach: application-level TLS, cert-manager for issuance, manual trust anchor configuration.

Rejected for current scale because:
- 11 services × manual TLS config = 11 places where misconfiguration can silently disable mTLS
- No L7 authorization enforcement possible
- No automatic RED metrics for inter-service calls
- `AuthorizationPolicy` at method granularity is not achievable without a proxy

### Linkerd

Linkerd is a lighter-weight service mesh — no Envoy, uses a purpose-built Rust proxy (`linkerd2-proxy`). Lower resource overhead (~10m CPU per pod vs ~50m for Envoy).

Considered. Istio chosen because:
- Istio's `AuthorizationPolicy` has richer L7 rules (path, method, header matching) than Linkerd's current authorization policy
- Istio's traffic management (`VirtualService`, `DestinationRule`) is more powerful for the canary/progressive delivery use case (Argo Rollouts + Istio integration is well-tested)
- Istio has broader ecosystem integration (Kiali for mesh visualization, Jaeger integration)
- The operational overhead difference is acceptable at Pravah's scale
