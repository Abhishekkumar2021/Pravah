# Pravah — High-Level Architecture

> This document is the single reference for Pravah's complete architecture: service map, data flows, infrastructure, and the decisions that shaped them. Each section links to the relevant ADR for the full decision record.

---

## What Pravah Does

Pravah is a distributed pipeline execution platform. It enables engineering teams to define data and CI pipelines as code, schedule them (cron, event-driven, or manual), and execute them on a fleet of runners that can live in cloud or on-premise infrastructure.

The three core functions are:
1. **Pipeline management** — define, version, and configure pipelines and their steps
2. **Scheduling** — trigger pipelines on schedules, events, or API calls
3. **Execution** — break pipelines into jobs, assign jobs to runners, track state to completion

---

## Architecture Overview

```
╔═══════════════════════════════════════════════════════════════════════╗
║                       PUBLIC INTERNET                                ║
╚═══════════════════════════════════╤═══════════════════════════════════╝
                                    │ HTTPS (JWT-authenticated)
                         ┌──────────▼──────────┐
                         │      API Gateway     │
                         │   Rate Limit · Auth  │
                         │   Route · mTLS out   │
                         └──┬────────┬──────────┘
                            │        │
              ┌─────────────┘        └─────────────┐
              │ mTLS gRPC/HTTP                      │ mTLS HTTP
   ┌──────────▼──────────┐             ┌────────────▼──────────┐
   │   Pipeline Service   │             │    Tenant Service      │
   │  CRUD · Versions    │             │  Config · API Keys    │
   └──────────┬──────────┘             └───────────────────────┘
              │ Kafka (pipeline.triggered)
   ┌──────────▼──────────┐
   │  Scheduler Service   │  ← cron eval, distributed lock, leader election
   └──────────┬──────────┘
              │ Kafka (job.created, job.assigned)
   ┌──────────▼──────────────────────────────────────┐
   │              Execution Service                   │
   │  Job state machine · Outbox · Saga coordinator  │
   │  KEDA-scaled on Kafka consumer lag              │
   └──────────┬───────────────────────────────────────┘
              │ Kafka (runners.reserved, job.dispatched)
   ┌──────────▼──────────┐
   │   Runner Service     │  ← Fleet management, capacity, heartbeat
   └──────────┬──────────┘
              │ gRPC bidirectional stream (mTLS, runner-initiated)
╔═════════════╪══════════════════════════════════════════════════╗
║  CUSTOMER INFRASTRUCTURE                                        ║
║             │                                                   ║
║  ┌──────────▼──────────┐  ┌──────────────────┐                ║
║  │    Runner Binary     │  │   Runner Binary   │  ...           ║
║  │  Executes jobs in   │  │                  │                 ║
║  │  customer env       │  │                  │                 ║
║  └─────────────────────┘  └──────────────────┘                ║
╚═════════════════════════════════════════════════════════════════╝
```

---

## Services

### API Gateway

The single entry point for all external traffic. It does not contain business logic.

**Responsibilities:**
- JWT validation (RS256, JWKS endpoint) for user requests
- API key validation (SHA-256 hash lookup) for programmatic access
- Per-tenant rate limiting via Redis token bucket (ADR-012)
- Request routing to downstream services over mTLS
- HTTP security headers (CSP, HSTS, X-Frame-Options)

**Key decisions:** [ADR-001](../adr/ADR-001-microservices-architecture.md), [ADR-008](../adr/ADR-008-mtls-service-to-service.md), [ADR-009](../adr/ADR-009-jwt-oauth2-authentication.md), [ADR-012](../adr/ADR-012-redis-caching-locking.md)

---

### Pipeline Service

Owns the canonical definition of what a pipeline is and what its steps do.

**Responsibilities:**
- CRUD operations on pipeline definitions
- Pipeline versioning (immutable versions; new definition = new version)
- Step configuration (step type, parameters, timeout, retry policy)
- Trigger configuration (cron expressions, event subscriptions, webhook endpoints)

**Database:** `pipeline_db` — tables: `pipelines`, `pipeline_versions`, `pipeline_steps`, `triggers`

**Key decisions:** [ADR-003](../adr/ADR-003-postgresql-database-per-service.md), [ADR-006](../adr/ADR-006-pool-multi-tenancy-model.md), [ADR-013](../adr/ADR-013-postgresql-rls-tenant-isolation.md)

---

### Tenant Service

Owns all tenant configuration and identity.

**Responsibilities:**
- Tenant registration and lifecycle
- API key management (issuance, hashing, revocation)
- Per-tenant configuration (SLO targets, rate limits, runner labels, feature flags)
- RBAC role assignments per user per tenant

**Database:** `tenant_db` — tables: `tenants`, `users`, `api_keys`, `tenant_config`, `role_assignments`

**Caching:** Tenant config cached in Redis (10-minute TTL), invalidated on update. Every API request reads tenant config from cache to enforce rate limits and feature flags.

---

### Scheduler Service

Determines when pipelines should run. Has the strongest correctness requirements of any service.

**Responsibilities:**
- Evaluating cron expressions to determine the next trigger time
- Processing event-based triggers (webhook, Kafka event subscriptions)
- Distributed locking to ensure exactly-one trigger per pipeline per schedule window
- Publishing `pipeline.triggered` events to Kafka

**Leader election:** Multiple Scheduler instances run for HA. A distributed Redis lock ensures that only one instance evaluates and triggers for each pipeline ID at a time. If the lock holder crashes, the lock expires and another instance takes over within 30 seconds.

**Database:** `scheduler_db` — tables: `trigger_configs`, `trigger_history`, `scheduled_runs`

**Key decisions:** [ADR-002](../adr/ADR-002-kafka-messaging-backbone.md), [ADR-011](../adr/ADR-011-saga-choreography.md), [ADR-012](../adr/ADR-012-redis-caching-locking.md)

---

### Execution Service

The core of Pravah. Manages the full lifecycle of every job.

**Responsibilities:**
- Translating a `pipeline.triggered` event into an `Execution` record and child `Job` records
- Maintaining the job state machine (`PENDING` → `ASSIGNED` → `RUNNING` → `SUCCEEDED` / `FAILED`)
- Coordinating the pipeline execution saga (choreography via Kafka events)
- Publishing all state transitions via the Outbox Pattern to guarantee event delivery
- Saga compensation: cleaning up when a step fails (e.g., runner reservation fails)

**Database:** `execution_db` — tables: `executions`, `jobs`, `job_attempts`, `outbox_events`

**Scaling:** KEDA scales on `pravah.job.assigned` Kafka consumer lag. Target: <100 messages lag per pod. Range: 3–50 replicas. ([ADR-015](../adr/ADR-015-keda-event-driven-autoscaling.md))

**Job state machine:**

```
PENDING ──────► ASSIGNED ──────► RUNNING ──────► SUCCEEDED
   │                │                │
   │                │                └──────────► FAILED
   │                │
   │                └─────────────────────────── CANCELLED
   │
   └─── (no runner available) ──────────────────► FAILED
```

**Key decisions:** [ADR-004](../adr/ADR-004-outbox-pattern-event-publishing.md), [ADR-011](../adr/ADR-011-saga-choreography.md), [ADR-013](../adr/ADR-013-postgresql-rls-tenant-isolation.md), [ADR-015](../adr/ADR-015-keda-event-driven-autoscaling.md)

---

### Runner Service

Manages the fleet of runner binaries that execute jobs.

**Responsibilities:**
- Accepting runner registrations (via the gRPC `Connect()` stream)
- Tracking runner capacity and heartbeats
- Assigning jobs to runners based on capacity and label matching
- Monitoring runner health; marking disconnected runners
- Streaming assignment results back to the Execution Service

**Runner authentication:** Runners authenticate via mTLS client certificates. The `RunnerIdentityInterceptor` extracts runner identity from the certificate — not from the message payload. This prevents a compromised runner from impersonating another.

**Database:** `runner_db` — tables: `runners`, `runner_assignments`, `heartbeat_log`

**Key decisions:** [ADR-005](../adr/ADR-005-grpc-runner-communication.md), [ADR-008](../adr/ADR-008-mtls-service-to-service.md)

---

## Data Flows

### Pipeline Triggered → Jobs Executing

```
User / Cron
    │
    │ 1. POST /api/v1/pipelines/{id}/trigger
    ▼           (or cron fires in Scheduler)
API Gateway
    │
    │ 2. Validate JWT, check rate limit
    ▼
Scheduler Service
    │
    │ 3. Acquire distributed lock for pipeline_id
    │ 4. Validate trigger conditions
    │ 5. Publish → pravah.pipeline.triggered
    ▼
Execution Service (Kafka consumer)
    │
    │ 6. Create Execution record (status: INITIALIZING)
    │ 7. Fetch pipeline definition from Pipeline Service
    │ 8. Create Job records for each step (status: PENDING)
    │ 9. Write outbox event → pravah.jobs.created
    │    (same transaction as DB write)
    ▼
Runner Service (Kafka consumer)
    │
    │ 10. Check runner capacity and label constraints
    │ 11. Reserve runner slots
    │ 12. Publish → pravah.runners.reserved
    ▼
Execution Service (Kafka consumer)
    │
    │ 13. Transition jobs: PENDING → ASSIGNED
    │ 14. Publish → pravah.jobs.assigned
    ▼
Runner Service (Kafka consumer)
    │
    │ 15. Send JobAssignment over gRPC stream to runner
    ▼
Runner (customer infrastructure)
    │
    │ 16. Acknowledge assignment
    │ 17. Execute job steps
    │ 18. Stream log lines back via gRPC
    │ 19. Publish job result via gRPC stream
    ▼
Runner Service
    │
    │ 20. Publish → pravah.job.completed
    ▼
Execution Service (Kafka consumer)
    │
    │ 21. Transition job: RUNNING → SUCCEEDED/FAILED
    │ 22. Check if all pipeline jobs complete
    │ 23. Transition Execution: RUNNING → COMPLETED/FAILED
    │ 24. Publish → pravah.execution.completed
```

---

## Infrastructure Map

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (pravah namespace)                │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Application Tier                                               │   │
│  │                                                                 │   │
│  │  API Gateway (2 replicas, HPA)                                 │   │
│  │  Pipeline Service (2 replicas)                                 │   │
│  │  Tenant Service (2 replicas)                                   │   │
│  │  Scheduler Service (3 replicas, distributed leader election)   │   │
│  │  Execution Service (3-50 replicas, KEDA on Kafka lag)          │   │
│  │  Runner Service (3 replicas)                                   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Data Tier                                                      │   │
│  │                                                                 │   │
│  │  PostgreSQL (Primary + 2 Replicas, per service)                │   │
│  │  PgBouncer (sidecar per service, transaction mode)             │   │
│  │  Redis Sentinel (1 primary + 2 replicas + 3 sentinels)         │   │
│  │  Kafka (3 brokers, 3× replication)                             │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Infrastructure Tier                                            │   │
│  │                                                                 │   │
│  │  HashiCorp Vault (3-node Raft HA, auto-unseal via KMS)         │   │
│  │  cert-manager (certificates for all services)                  │   │
│  │  Argo CD (GitOps reconciliation)                               │   │
│  │  KEDA operator                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  Observability Tier (monitoring namespace)                      │   │
│  │                                                                 │   │
│  │  Prometheus + Alertmanager                                      │   │
│  │  Grafana                                                       │   │
│  │  OpenTelemetry Collector (tail-based sampling)                 │   │
│  │  Jaeger (trace storage)                                        │   │
│  │  Filebeat → Logstash → Elasticsearch → Kibana                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

```
External request
    │
    │ TLS termination at Ingress
    ▼
API Gateway
    ├── JWT validation (RS256, JWKS)       ← user token
    ├── API key validation (SHA-256 hash)  ← programmatic access
    ├── Rate limiting (Redis token bucket, per-tenant)
    └── Routes over mTLS to internal services

Internal service-to-service
    └── mTLS with cert-manager certificates (SPIFFE URIs)
        All services present client certificates.
        Identity verified cryptographically, not by IP.

Runner ↔ Cloud
    └── mTLS client certificate on gRPC stream
        Runner identity extracted from certificate SAN.
        Certificate issued by Vault PKI, 7-day validity.

Data at rest
    ├── PostgreSQL: RLS enforces tenant isolation at DB layer
    ├── Vault Transit: per-tenant encryption keys for sensitive fields
    └── Dynamic DB credentials: 1-hour TTL, auto-revoked

Secrets
    └── Vault Agent sidecar: secrets injected as files on tmpfs
        No environment variables. No static passwords.
        Dynamic PostgreSQL credentials per pod startup.
```

---

## Multi-Tenancy Architecture

```
All tenants share:
    - Kubernetes cluster
    - Kafka cluster (topics shared, tenant_id in payload)
    - PostgreSQL cluster (tables shared, tenant_id column)
    - Redis (key-namespaced by tenant_id)

Isolation enforced at:
    Layer 1: API Gateway extracts tenant_id from JWT
    Layer 2: Application code — findByIdAndTenantId() everywhere
    Layer 3: PostgreSQL RLS — FORCE ROW LEVEL SECURITY on all tables
    Layer 4: Kafka consumers validate tenant context on every message
    Layer 5: Per-tenant Vault Transit encryption keys

Enterprise upgrade:
    High-compliance tenants → dedicated silo deployment
    (separate cluster, separate databases, separate Kafka)
    Triggered by contract requirement, not code change.
```

---

## Observability Architecture

```
Metrics (Prometheus pull model):
    Each service exposes /actuator/prometheus
    Prometheus scrapes every 15 seconds
    Recording rules pre-compute SLIs
    Grafana dashboards: executive, service, runner fleet, Kafka
    Alertmanager: 3-tier routing (page / ticket / dashboard)

Traces (OpenTelemetry, tail-based sampling):
    All services export 100% of spans to OTel Collector
    Collector buffers spans per trace_id (30-second window)
    Sampling decision: errors=100%, slow>2s=100%, healthy=1%
    Sampled traces → Jaeger → Elasticsearch
    trace_id propagated: HTTP headers, gRPC metadata, Kafka headers

Logs (structured JSON, ELK):
    All services log JSON with mandatory fields:
      timestamp, level, service, trace_id, tenant_id, message
    Filebeat ships logs → Logstash → Elasticsearch → Kibana
    Retention: hot (7 days), warm (30 days), archive (90 days)

Correlation:
    trace_id appears in logs AND traces AND metrics (exemplars)
    Any log line can be correlated to its full distributed trace.
```

---

## Deployment Architecture

```
Git (source of truth)
    │
    │  Helm charts + values per environment
    ▼
Argo CD (GitOps reconciliation)
    │
    │  Detects drift, applies changes, reports health
    ▼
Kubernetes (workload management)
    │
    ├── Rolling updates (maxUnavailable=0, maxSurge=1)
    ├── Canary deployments via Argo Rollouts (5% → 20% → 100%)
    │   with automated Prometheus analysis gates
    ├── PodDisruptionBudgets (minAvailable=2 per service)
    └── PriorityClasses (critical services preempt non-critical)

Autoscaling:
    KEDA → Execution Service (Kafka lag, 3–50 replicas)
    HPA  → API Gateway (CPU, 2–10 replicas)
    VPA  → All services (recommendation mode, right-size requests)
```

---

## ADR Cross-Reference

| Decision Area | ADR |
|---------------|-----|
| Service decomposition | [ADR-001: Microservices Architecture](../adr/ADR-001-microservices-architecture.md) |
| Async messaging | [ADR-002: Kafka as Messaging Backbone](../adr/ADR-002-kafka-messaging-backbone.md) |
| Database strategy | [ADR-003: PostgreSQL + Database-per-Service](../adr/ADR-003-postgresql-database-per-service.md) |
| Reliable event publishing | [ADR-004: Outbox Pattern](../adr/ADR-004-outbox-pattern-event-publishing.md) |
| Runner protocol | [ADR-005: gRPC for Runner Communication](../adr/ADR-005-grpc-runner-communication.md) |
| Multi-tenancy | [ADR-006: Pool Multi-Tenancy Model](../adr/ADR-006-pool-multi-tenancy-model.md) |
| Secret management | [ADR-007: HashiCorp Vault](../adr/ADR-007-vault-secret-management.md) |
| Service identity | [ADR-008: mTLS Service-to-Service](../adr/ADR-008-mtls-service-to-service.md) |
| User authentication | [ADR-009: JWT RS256 + OAuth 2.0](../adr/ADR-009-jwt-oauth2-authentication.md) |
| Deployment platform | [ADR-010: Kubernetes + Helm + Argo CD](../adr/ADR-010-kubernetes-helm-argocd.md) |
| Distributed transactions | [ADR-011: Saga Choreography](../adr/ADR-011-saga-choreography.md) |
| Shared ephemeral state | [ADR-012: Redis](../adr/ADR-012-redis-caching-locking.md) |
| Tenant data isolation | [ADR-013: PostgreSQL RLS](../adr/ADR-013-postgresql-rls-tenant-isolation.md) |
| Distributed tracing | [ADR-014: Tail-Based Sampling](../adr/ADR-014-tail-based-sampling.md) |
| Autoscaling | [ADR-015: KEDA](../adr/ADR-015-keda-event-driven-autoscaling.md) |
