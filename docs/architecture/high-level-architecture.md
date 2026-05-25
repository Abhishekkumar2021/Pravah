# Pravah — High-Level Architecture

> This document is the single reference for Pravah's **target** architecture: service map, data flows, infrastructure, security, observability, and the decisions that shaped them. Each section links to the relevant ADR for the full decision record. All 33 ADRs are indexed at the end.
>
> **Not everything here is implemented yet.** Before assuming a service or integration exists in code, read **[Implementation Status](../IMPLEMENTATION_STATUS.md)** (e.g. alpha uses embedded stage executors in `execution-service`; runner gRPC fleet, GraphQL UI, lineage, and agent service are planned).

**Last updated:** 2026-05-17

---

## What Pravah Does

Pravah is a distributed data pipeline orchestration platform. Engineering and data teams define pipelines as code, schedule them (cron, event-driven, webhook, or manual), and execute them on a fleet of runners that can live in the customer's own infrastructure or in ephemeral cloud pods.

The five core functions are:

1. **Pipeline management** — define, version, and configure DAG-based pipelines and their steps
2. **Scheduling** — trigger pipelines on cron schedules, Kafka events, webhooks, or API calls
3. **Execution** — break pipelines into jobs, resolve DAG dependencies, assign jobs to runners, track state to completion
4. **Observability & lineage** — emit structured events, capture column-level data lineage via OpenLineage, surface data quality violations
5. **Agentic intelligence** — LLM-powered auto-heal: detect schema drift, suggest fixes, and optionally apply them with human approval

---

## Architecture Overview

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            PUBLIC INTERNET                                   ║
╚════════════════════════════════════╤═════════════════════════════════════════╝
                                     │ HTTPS
                          ┌──────────▼──────────┐
                          │     API Gateway       │  JWT · API key · Rate limit
                          │  Spring Cloud Gateway │  mTLS out · SSO redirect
                          └──┬─────────┬─────────┘
                             │         │
              ┌──────────────┘         └──────────────┐
              │ REST (mTLS)                            │ GraphQL (mTLS)
   ┌──────────▼──────────┐                 ┌──────────▼──────────┐
   │  REST API Services   │                 │  GraphQL Service     │
   │  (mutations, SDK)    │                 │  (UI read queries)   │
   └──────────┬──────────┘                 └─────────────────────┘
              │
     ┌────────┴────────────────────────────────────────────┐
     │              Internal mTLS gRPC / HTTP mesh           │
     └───┬──────────┬──────────┬──────────┬────────────────┘
         │          │          │          │
┌────────▼───┐ ┌────▼───────┐ ┌▼─────────┐ ┌▼────────────┐ ┌─────────────┐
│  Pipeline  │ │ Scheduler  │ │Execution │ │   Runner    │ │   Tenant    │
│  Service   │ │  Service   │ │ Service  │ │  Service    │ │  Service    │
│ DAG · CRUD │ │cron·trigger│ │saga·jobs │ │fleet·assign │ │config·RBAC │
└────────────┘ └────────────┘ └──────────┘ └──────┬──────┘ └─────────────┘
                                                    │ gRPC bidirectional (mTLS)
              ┌──────────────────────────────────────┘
              │
╔═════════════╪═══════════════════════════════════════════╗
║  CUSTOMER INFRASTRUCTURE                                 ║
║             │                                            ║
║  ┌──────────▼────────┐  ┌────────────────┐              ║
║  │  Runner Binary     │  │  Runner Binary  │  ...        ║
║  │  Executes jobs     │  │                │             ║
║  │  DuckDB transforms │  │                │             ║
║  └────────────────────┘  └────────────────┘             ║
╚══════════════════════════════════════════════════════════╝

Supporting services (all internal, mTLS):

┌────────────────┐  ┌──────────────────┐  ┌────────────────┐
│ Agent Service  │  │ Metadata Service  │  │ Connect Service│
│ Spring AI·RAG  │  │ Lineage·Catalog   │  │ CDC·Connectors │
└────────────────┘  └──────────────────┘  └────────────────┘

┌────────────────┐  ┌──────────────────┐
│Notification Svc│  │  Billing Service  │
│email·Slack·PD  │  │ metering·invoices │
└────────────────┘  └──────────────────┘

Async backbone: Apache Kafka (all inter-service events)
```

---

## Services

### API Gateway

The single entry point for all external traffic. Contains no business logic.

**Responsibilities:**
- JWT validation (RS256, JWKS endpoint) for user requests
- API key validation (SHA-256 hash lookup) for programmatic access
- SSO redirect: extracts email domain, routes to IdP for SSO tenants (ADR-030)
- Per-tenant rate limiting via Redis token bucket (ADR-012)
- Request routing to downstream services over mTLS (ADR-008)
- HTTP security headers (CSP, HSTS, X-Frame-Options)
- OpenFeature flag evaluation for feature gating before routing

**Key decisions:** [ADR-001](../adr/ADR-001-microservices-architecture.md), [ADR-008](../adr/ADR-008-mtls-service-to-service.md), [ADR-009](../adr/ADR-009-jwt-oauth2-authentication.md), [ADR-012](../adr/ADR-012-redis-caching-locking.md), [ADR-030](../adr/ADR-030-sso-saml-enterprise-auth.md)

---

### GraphQL Service

A thin read-only composition layer for the Pravah UI. Has no database of its own.

**Responsibilities:**
- Accepts GraphQL queries from the browser (POST /graphql)
- Translates graph queries into calls to underlying REST/gRPC services
- DataLoader batching prevents N+1 downstream calls
- Returns assembled composite responses in a single round-trip
- All mutations remain on REST; this service handles reads only

**Why it exists:** The UI pipeline canvas needs pipeline definition + latest execution status + lineage upstream + current runner — 4 entities — in one render. Without GraphQL this requires 4 REST calls. With GraphQL it is one query.

**Key decisions:** [ADR-033](../adr/ADR-033-graphql-api.md)

---

### Pipeline Service

Owns the canonical definition of what a pipeline is and what its steps do.

**Responsibilities:**
- CRUD operations on pipeline definitions
- Pipeline versioning (immutable versions; changing a definition creates a new version)
- Step configuration: step type, timeout, retry policy, PII column declarations
- DAG validation: detect cycles, unreachable nodes, invalid dependencies
- Trigger configuration: cron expressions, Kafka topic subscriptions, webhook endpoints
- Event sourcing: every pipeline mutation appended to `pipeline.events` (ADR-017)

**Database:** `pipeline_db` — tables: `pipelines`, `pipeline_versions`, `pipeline_steps`, `pipeline_dag_edges`, `triggers`, `outbox_events`

**Key decisions:** [ADR-003](../adr/ADR-003-postgresql-database-per-service.md), [ADR-006](../adr/ADR-006-pool-multi-tenancy-model.md), [ADR-013](../adr/ADR-013-postgresql-rls-tenant-isolation.md), [ADR-017](../adr/ADR-017-event-sourcing-pipeline-service.md), [ADR-018](../adr/ADR-018-dag-engine-step-execution.md)

---

### Tenant Service

Owns all tenant configuration, identity, and SSO configuration.

**Responsibilities:**
- Tenant registration and lifecycle
- API key management (issuance, SHA-256 hashing, revocation)
- SSO configuration per tenant (OIDC or SAML 2.0 IdP settings, stored in Vault)
- SCIM 2.0 endpoint for automated user provisioning from the IdP
- Per-tenant configuration: rate limits, runner labels, resource quotas, feature flag overrides
- RBAC role assignments per user per tenant

**Database:** `tenant_db` — tables: `tenants`, `users`, `api_keys`, `sso_configurations`, `tenant_config`, `role_assignments`

**Caching:** Tenant config cached in Redis (10-minute TTL), invalidated on update. Every API request reads from cache to enforce rate limits.

**Key decisions:** [ADR-006](../adr/ADR-006-pool-multi-tenancy-model.md), [ADR-009](../adr/ADR-009-jwt-oauth2-authentication.md), [ADR-030](../adr/ADR-030-sso-saml-enterprise-auth.md), [ADR-031](../adr/ADR-031-openfeature-feature-flags.md)

---

### Scheduler Service

Determines when pipelines should run. Has the strongest correctness requirements of any service — a double-trigger costs real money.

**Responsibilities:**
- Evaluating cron expressions against the current time
- Processing event-based triggers (Kafka event subscriptions, webhook arrivals)
- Distributed locking to guarantee exactly-one trigger per pipeline per schedule window
- DAG dependency resolution: only trigger a step when all declared upstream steps have succeeded
- Backfill orchestration: re-run a pipeline over historical date ranges with configurable concurrency limits (ADR-025)
- Publishing `pipeline.triggered` events to Kafka

**Leader election:** Multiple Scheduler replicas run for HA. A Redis lock (SET NX PX 30000) ensures exactly one instance evaluates and triggers per pipeline per window. Fencing token in the event body prevents duplicate execution even if two instances race.

**Database:** `scheduler_db` — tables: `trigger_configs`, `trigger_history`, `scheduled_runs`, `backfill_requests`

**Key decisions:** [ADR-002](../adr/ADR-002-kafka-messaging-backbone.md), [ADR-012](../adr/ADR-012-redis-caching-locking.md), [ADR-018](../adr/ADR-018-dag-engine-step-execution.md), [ADR-025](../adr/ADR-025-backfill-concurrency-policy.md)

---

### Execution Service

The core of Pravah. Manages the full lifecycle of every job from dispatch to completion.

**Responsibilities:**
- Translating a `pipeline.triggered` event into an `Execution` record and child `Job` records
- Maintaining the job state machine (`PENDING` → `ASSIGNED` → `RUNNING` → `SUCCEEDED` / `FAILED`)
- DAG step sequencing: releasing downstream jobs only when all their dependencies succeed
- Coordinating the pipeline saga via Kafka choreography (ADR-011)
- Publishing all state transitions via the Outbox Pattern (ADR-004) — guarantees events are never lost
- Saga compensation: releasing reserved runner slots when a step fails
- Durable checkpointing: persisting step-level progress so interrupted jobs can resume (ADR-024)

**Database:** `execution_db` — tables: `executions`, `jobs`, `job_attempts`, `job_checkpoints`, `outbox_events`

**Scaling:** KEDA scales on `pravah.job.assigned` Kafka consumer lag. Target: <100 messages lag per pod. Range: 3–50 replicas. (ADR-015)

**Job state machine:**

```
PENDING ──────► ASSIGNED ──────► RUNNING ──────► SUCCEEDED
   │                │                │
   │                │                └──────────► FAILED
   │                │
   │                └──────────────────────────── CANCELLED
   │
   └─── (no runner available) ─────────────────► FAILED
```

**Key decisions:** [ADR-004](../adr/ADR-004-outbox-pattern-event-publishing.md), [ADR-011](../adr/ADR-011-saga-choreography.md), [ADR-013](../adr/ADR-013-postgresql-rls-tenant-isolation.md), [ADR-015](../adr/ADR-015-keda-event-driven-autoscaling.md), [ADR-024](../adr/ADR-024-durable-execution-checkpointing.md)

---

### Runner Service

Manages the fleet of runner binaries that execute jobs.

**Responsibilities:**
- Accepting runner registrations via the gRPC `Connect()` bidirectional stream (ADR-005)
- Tracking runner capacity, labels, and heartbeats in Redis (30-second TTL)
- Assigning jobs to runners based on available capacity, label selectors, and PII region constraints
- Monitoring runner health: `SUSPECT` at 30 s silence, `DEAD` at 60 s silence
- Streaming `JobAssignment` messages to runners over the gRPC stream
- Receiving job results and log streams from runners; forwarding results to Execution Service

**Runner authentication:** Runners authenticate via mTLS client certificates. The `RunnerIdentityInterceptor` extracts runner identity from the certificate SAN — not from the message payload. Prevents a compromised runner from impersonating another.

**Runner binary:** A standalone JAR/Docker image. Contains DuckDB for in-process SQL transforms (ADR-023). Sends heartbeats every 10 seconds. Streams structured logs back to the cloud via gRPC.

**Database:** `runner_db` — tables: `runners`, `runner_assignments`, `runner_certificates`

**Ephemeral state:** Runner heartbeat and capacity in Redis (30-second TTL). Loss of Redis means temporary inability to assign; runners continue executing in-progress jobs.

**Key decisions:** [ADR-005](../adr/ADR-005-grpc-runner-communication.md), [ADR-008](../adr/ADR-008-mtls-service-to-service.md), [ADR-023](../adr/ADR-023-duckdb-runner-transforms.md)

---

### Agent Service

LLM-powered intelligence layer. Observes pipeline events, detects anomalies, and can propose or apply corrective actions.

**Responsibilities:**
- Subscribing to `pravah.execution.failed` and `pravah.data.quality.alert` Kafka topics
- Classifying failure causes (schema drift, timeout, transient error, misconfiguration)
- Building context from execution logs, lineage graph, and historical run patterns (RAG over Elasticsearch)
- Using the ReAct pattern to call tools: `get_execution_logs`, `get_lineage`, `get_schema_diff`, `propose_fix`
- For schema drift: generating a proposed pipeline fix and sending it to the Notification Service
- For transient errors: automatically re-triggering with an updated retry policy (with human approval gate in the default configuration)
- Storing agent observations and actions in PostgreSQL for auditability

**Memory:** Short-term in Redis (current reasoning context). Long-term in PostgreSQL + pgvector (embedding store for past incident patterns). Episodic (recent run history) fetched from Execution Service on demand.

**LLM:** Spring AI (ADR-020) with configurable model backend (Anthropic Claude, OpenAI GPT-4). Temperature = 0 for tool calls; 0.3 for explanation text.

**Database:** `agent_db` — tables: `agent_observations`, `healing_actions`, `schema_drift_events`, `approved_fixes`

**Key decisions:** [ADR-020](../adr/ADR-020-agent-service-architecture.md)

---

### Metadata Service

Single source of truth for data catalog and data lineage across all pipeline runs.

**Responsibilities:**
- Ingesting OpenLineage events emitted by runners after each step completes (ADR-019)
- Storing column-level lineage graph in Elasticsearch (dataset → column → dataset chains)
- Maintaining a data catalog: dataset schema snapshots, column types, PII classification tags
- Detecting schema drift: comparing the current source schema against the registered schema in the catalog
- Publishing `schema.drift.detected` events to Kafka when drift is found
- Exposing search APIs for the UI data catalog and lineage explorer

**Database:** `metadata_db` (PostgreSQL) — tables: `datasets`, `dataset_schemas`, `schema_snapshots`
**Search / lineage:** Elasticsearch — index `lineage_events`, index `data_catalog`

**Key decisions:** [ADR-019](../adr/ADR-019-openlineage-metadata-service.md), [ADR-026](../adr/ADR-026-data-quality-contracts.md)

---

### Notification Service

Delivers alerts to humans when pipelines fail, data quality thresholds breach, or agent actions require approval.

**Responsibilities:**
- Consuming `pravah.execution.failed`, `pravah.data.quality.alert`, `agent.action.requires_approval` Kafka topics
- Routing notifications to the correct channel per tenant configuration: email, Slack, PagerDuty, webhook
- Deduplication: suppress duplicate alerts within a configurable window (default 15 minutes)
- Rate limiting: prevent alert storms (max N alerts per pipeline per hour)
- Tracking delivery status and retry on transient delivery failures

**Delivery channels:** Email (SMTP/SendGrid), Slack (webhook), PagerDuty (Events API v2), generic webhook (HTTPS POST)

**Database:** `notification_db` — tables: `notification_configs`, `notification_log`, `delivery_attempts`

**Key decisions:** [ADR-027](../adr/ADR-027-notification-service.md)

---

### Billing Service

Tracks compute consumption per tenant and generates invoices for usage-based billing.

**Responsibilities:**
- Consuming `pravah.job.completed` events and extracting: tenant, pipeline, duration, CPU, memory peak
- Accumulating metered usage in PostgreSQL (partitioned by month)
- Applying per-tenant pricing plans and quota limits
- Generating monthly usage summaries and invoices
- Enforcing quota limits: publishing `billing.quota.exceeded` when a tenant exceeds their plan cap

**Database:** `billing_db` — tables: `usage_events` (partitioned by month), `invoices`, `pricing_plans`, `tenant_quotas`

**Key decisions:** [ADR-028](../adr/ADR-028-billing-compute-metering.md)

---

### Connect Service

Manages data source connectors and CDC (Change Data Capture) pipelines.

**Responsibilities:**
- CRUD for connector configurations (JDBC, S3, Kafka, MongoDB, REST API sources)
- Deploying and monitoring Kafka Connect connectors (Debezium for CDC)
- Health checks on active connectors; restarting failed connectors automatically
- Storing encrypted connector credentials in Vault (ADR-007); never in its own database
- Validating connector schemas and publishing `schema.change.detected` events

**Database:** `connect_db` — tables: `connector_configs`, `connector_health_log`
**Credential storage:** Vault KV v2 at `pravah/connectors/{tenant_id}/{connector_id}/credentials`

**Key decisions:** [ADR-029](../adr/ADR-029-connect-service-cdc-management.md), [ADR-007](../adr/ADR-007-vault-secret-management.md)

---

## Kafka Topic Map

All inter-service communication flows through Kafka. Direct REST/gRPC is used only for synchronous queries.

```
Topic                          Producer              Consumers
──────────────────────────────────────────────────────────────────────────────
pravah.pipeline.triggered      Scheduler             Execution Service
pravah.pipeline.events         Pipeline Service      Agent Service, Audit
pravah.job.created             Execution Service     Runner Service
pravah.job.assigned            Runner Service        Execution Service
pravah.job.completed           Runner Service        Execution Service, Billing, Metadata
pravah.execution.completed     Execution Service     Notification, Agent, Billing, Audit
pravah.execution.failed        Execution Service     Notification, Agent
pravah.runner.heartbeat        Runner Binary         Runner Service
pravah.runners.reserved        Runner Service        Execution Service
pravah.data.quality.alert      Execution Service     Notification, Agent
pravah.schema.drift.detected   Metadata Service      Agent, Notification
pravah.lineage.events          Runner Binary         Metadata Service
pravah.billing.compute.used    Billing Service       (analytics sink)
pravah.notifications.outbound  Notification Service  (delivery workers)
pravah.agent.observations      Agent Service         (audit, analytics)
```

**Topic configuration:**
- Replication factor: 3 (all topics)
- `min.insync.replicas`: 2
- Retention: 7 days (default), 30 days for audit and lineage topics
- Partitions: 24 for high-throughput topics (job.created, job.completed), 6 for low-volume

---

## Primary Data Flow: Pipeline Triggered → Jobs Executing

```
User / Cron
    │
    │ 1. POST /api/v1/pipelines/{id}/trigger  — or — cron fires in Scheduler
    ▼
API Gateway
    │
    │ 2. Validate JWT, check rate limit (Redis), route to Scheduler
    ▼
Scheduler Service
    │
    │ 3. Acquire distributed lock: SET scheduler:{pipeline}:{window} NX PX 30000
    │ 4. Validate trigger conditions and DAG readiness
    │ 5. Publish → pravah.pipeline.triggered  (with fencing_token)
    ▼
Execution Service (Kafka consumer: pipeline.triggered)
    │
    │ 6. Create Execution record  (status: INITIALIZING)
    │ 7. Fetch pipeline definition from Pipeline Service (cached in Redis)
    │ 8. Resolve DAG: create Job records for root-level steps (status: PENDING)
    │ 9. Write outbox event → pravah.job.created  (same transaction as DB write)
    ▼
Runner Service (Kafka consumer: job.created)
    │
    │ 10. Filter runners by label, capacity, PII region constraints
    │ 11. Reserve runner slots (optimistic lock on capacity)
    │ 12. Publish → pravah.runners.reserved
    ▼
Execution Service (Kafka consumer: runners.reserved)
    │
    │ 13. Transition jobs: PENDING → ASSIGNED
    │ 14. Publish → pravah.job.assigned
    ▼
Runner Service (Kafka consumer: job.assigned)
    │
    │ 15. Send JobAssignment over gRPC bidirectional stream to runner
    ▼
Runner (customer infrastructure)
    │
    │ 16. Acknowledge assignment
    │ 17. Execute job: extract → transform (DuckDB) → load
    │ 18. Stream structured log lines back via gRPC
    │ 19. Apply PII masking before any data leaves the step boundary (ADR-032)
    │ 20. Emit OpenLineage event (published to pravah.lineage.events)
    │ 21. Publish job result via gRPC stream
    ▼
Runner Service
    │
    │ 22. Publish → pravah.job.completed
    ▼
Execution Service (Kafka consumer: job.completed)
    │
    │ 23. Transition job: RUNNING → SUCCEEDED / FAILED
    │ 24. Release DAG: check if downstream steps' dependencies are all satisfied
    │ 25. If satisfied: create next-tier Job records (PENDING) → back to step 9
    │ 26. When all steps done: Transition Execution: RUNNING → COMPLETED / FAILED
    │ 27. Publish → pravah.execution.completed  or  pravah.execution.failed
    ▼
Fan-out consumers (all read pravah.execution.completed / failed):
    ├── Billing Service:        record compute usage (tenant, duration, CPU, memory)
    ├── Notification Service:   fire alerts if execution failed or SLA breached
    ├── Agent Service:          on failure, begin failure triage and auto-heal flow
    └── Audit Service:          append immutable audit record
```

---

## Secondary Data Flow: Schema Drift Detection & Auto-Heal

```
Runner
    │ 20b. OpenLineage event: dataset schema at runtime
    ▼
Metadata Service (Kafka consumer: lineage.events)
    │
    │ Compare runtime schema against registered schema in data catalog
    │ If columns added / removed / type changed:
    ▼
Metadata Service → publish pravah.schema.drift.detected

Agent Service (Kafka consumer: schema.drift.detected)
    │
    │ Build context: fetch pipeline definition, execution logs, lineage graph
    │ ReAct loop:
    │   Thought: "Column 'loyalty_tier' removed from source. Pipeline step 'enrich' will fail."
    │   Action:  get_pipeline_definition(pipeline_id)
    │   Action:  get_lineage(dataset="orders", column="loyalty_tier")
    │   Action:  propose_fix(type="add_null_coalesce", column="loyalty_tier")
    ▼
Notification Service
    │ Send: "Schema drift detected on 'orders.loyalty_tier'. Proposed fix attached.
    │        Click to approve auto-application."
    ▼
Human approves (or auto-apply if confidence > 0.9 and tenant has auto_heal=true)
    │
    ▼
Agent Service → PATCH /v1/pipelines/{id}/steps/{stepId}  (applies fix)
    │
    ▼
Pipeline Service → creates new pipeline version with fix applied
```

---

## Infrastructure Map

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (pravah namespace)                      │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  Application Tier                                                    │    │
│  │                                                                      │    │
│  │  API Gateway          (2 replicas, HPA on CPU)                      │    │
│  │  GraphQL Service      (2 replicas)                                  │    │
│  │  Pipeline Service     (2 replicas)                                  │    │
│  │  Tenant Service       (2 replicas)                                  │    │
│  │  Scheduler Service    (3 replicas, distributed leader election)      │    │
│  │  Execution Service    (3–50 replicas, KEDA on Kafka consumer lag)    │    │
│  │  Runner Service       (3 replicas)                                  │    │
│  │  Agent Service        (2 replicas)                                  │    │
│  │  Metadata Service     (2 replicas)                                  │    │
│  │  Notification Service (2 replicas)                                  │    │
│  │  Billing Service      (2 replicas)                                  │    │
│  │  Connect Service      (2 replicas)                                  │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  Data Tier                                                           │    │
│  │                                                                      │    │
│  │  PostgreSQL (Primary + 2 replicas per service, Patroni HA) (ADR-022)│    │
│  │  PgBouncer  (sidecar per service, transaction mode pooling)          │    │
│  │  Redis Sentinel (1 primary + 2 replicas + 3 sentinels)              │    │
│  │  Apache Kafka (3 brokers, ZooKeeper or KRaft, 3× replication)       │    │
│  │  MinIO (S3-compatible object storage, 4-node erasure coding) (ADR-021)│   │
│  │  Elasticsearch (3-node cluster, hot-warm-cold tiers)                │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  Infrastructure Tier                                                 │    │
│  │                                                                      │    │
│  │  HashiCorp Vault (3-node Raft HA, auto-unseal via KMS)   (ADR-007) │    │
│  │  cert-manager   (mTLS certificates for all services)      (ADR-008) │    │
│  │  Istio          (service mesh, L7 AuthorizationPolicy)    (ADR-016) │    │
│  │  Argo CD        (GitOps reconciliation)                   (ADR-010) │    │
│  │  KEDA operator  (event-driven autoscaling)                (ADR-015) │    │
│  │  OpenFeature provider  (Redis-backed feature flags)       (ADR-031) │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │  Observability Tier (monitoring namespace)                           │    │
│  │                                                                      │    │
│  │  Prometheus + Alertmanager                                           │    │
│  │  Grafana (4 dashboards: executive, service, runner fleet, Kafka)     │    │
│  │  OpenTelemetry Collector (tail-based sampling)            (ADR-014) │    │
│  │  Jaeger (trace storage, 7-day retention)                            │    │
│  │  Filebeat → Logstash → Elasticsearch → Kibana                       │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Security Architecture

```
External request
    │
    │ TLS 1.3 termination at Ingress
    ▼
API Gateway
    ├── JWT validation (RS256, JWKS)           ← interactive user
    ├── SSO redirect (OIDC / SAML 2.0)        ← enterprise users via IdP  [ADR-030]
    ├── API key validation (SHA-256 hash)      ← programmatic / SDK
    ├── Rate limiting (Redis token bucket, per-tenant)                     [ADR-012]
    └── Routes over mTLS to internal services

Internal service-to-service
    └── mTLS with cert-manager + Istio (SPIFFE URIs, 24-hour cert rotation)
        All services present client certificates.                           [ADR-008]
        Istio AuthorizationPolicy enforces L7 call-level rules.            [ADR-016]

Runner ↔ Cloud
    └── mTLS client certificate on gRPC bidirectional stream               [ADR-005]
        Certificate issued by Vault PKI, 7-day validity.
        Runner identity extracted from certificate SAN — not message payload.

Data at rest
    ├── PostgreSQL RLS: FORCE ROW LEVEL SECURITY on every table            [ADR-013]
    ├── Vault Transit: per-tenant encryption keys for sensitive fields      [ADR-007]
    ├── Dynamic DB credentials: 1-hour TTL, auto-revoked on pod death
    └── PII masking: runner applies column-level masking before egress      [ADR-032]

Secrets
    └── Vault Agent sidecar: secrets injected as files on tmpfs             [ADR-007]
        No environment variables. No static passwords.
        Dynamic PostgreSQL credentials per pod startup.

Feature flags
    └── OpenFeature (Redis provider): kill switches, gradual rollout        [ADR-031]
        Flag change propagates within 30 seconds via Redis Pub/Sub.
```

---

## Multi-Tenancy Architecture

```
All tenants share:
    - Kubernetes cluster
    - Kafka cluster  (topics shared, tenant_id in every message payload)
    - PostgreSQL cluster  (tables shared, tenant_id column on every table)
    - Redis  (all keys namespaced: tenant:{tenant_id}:*)
    - MinIO  (bucket-per-tenant: pravah-artifacts-{tenant_id})

Isolation enforced at five layers:
    Layer 1: API Gateway — extracts tenant_id from JWT / API key                [ADR-009]
    Layer 2: Application code — findByIdAndTenantId() on every repository call  [ADR-006]
    Layer 3: PostgreSQL RLS — FORCE ROW LEVEL SECURITY, SET LOCAL tenant_id     [ADR-013]
    Layer 4: Kafka consumers — validate tenant context on every message consumed [ADR-002]
    Layer 5: Vault Transit — per-tenant encryption key (different key per tenant)[ADR-007]

PII region compliance:
    Pipelines declaring GDPR-restricted PII are only assigned to runners
    in approved regions. Enforced at Scheduler job-assignment time.              [ADR-032]

Enterprise upgrade path:
    High-compliance tenants → dedicated silo deployment.
    Separate cluster, separate databases, separate Kafka.
    Triggered by contract requirement, not code change.                          [ADR-006]
```

---

## Observability Architecture

```
Metrics (Prometheus pull):
    Each service → /actuator/prometheus
    Prometheus scrapes every 15 seconds
    Recording rules pre-compute SLIs (burn rate, error ratio, latency p99)
    Grafana dashboards: executive SLO, per-service, runner fleet, Kafka health
    Alertmanager: three-tier routing (page → ticket → dashboard)               [ADR-001]

Traces (OpenTelemetry, tail-based sampling):
    All services export 100% of spans to OTel Collector                        [ADR-014]
    Collector buffers spans per trace_id (30-second window)
    Sampling: errors=100%, latency >2s=100%, healthy=1%
    Sampled traces → Jaeger → Elasticsearch (7-day retention)
    trace_id propagated in: HTTP headers, gRPC metadata, Kafka message headers

Logs (structured JSON, ELK):
    Mandatory fields: timestamp, level, service, trace_id, tenant_id, message
    Filebeat → Logstash → Elasticsearch → Kibana
    PII field scrubber runs in Logstash pipeline                               [ADR-032]
    Retention: hot 7 days, warm 30 days, cold archive 90 days in MinIO        [ADR-021]

Correlation:
    Every log line has trace_id → links to full distributed trace in Jaeger
    Prometheus exemplars embed trace_id → click from metric spike to trace
    Any incident: start from the alert, go to the dashboard, go to the trace, go to the log
```

---

## Deployment Architecture

```
Git (source of truth)
    │
    │  Helm charts + environment-specific values (dev / staging / production)
    ▼
Argo CD (GitOps reconciliation)                                                [ADR-010]
    │
    │  Detects drift, applies changes, reports sync status
    ▼
Kubernetes
    │
    ├── Rolling updates  (maxUnavailable=0, maxSurge=1)
    ├── Canary deployments via Argo Rollouts  (5% → 20% → 50% → 100%)
    │   Automated Prometheus analysis gate between each step:
    │   error_rate < 0.1% AND p99_latency < baseline + 20%
    ├── PodDisruptionBudgets  (minAvailable=2 per stateful service)
    └── PriorityClasses  (critical services preempt non-critical)

Autoscaling:
    KEDA   → Execution Service  (Kafka lag, 3–50 replicas)                     [ADR-015]
    HPA    → API Gateway        (CPU, 2–10 replicas)
    VPA    → All services       (recommendation mode, right-size resource requests)

Feature rollout:
    OpenFeature flags control which tenants get new features                   [ADR-031]
    New code deployed to 100% of pods; flag enables the feature for 5% of tenants.
    Flag expanded progressively. Rollback = toggle flag off, no deployment needed.
```

---

## ADR Cross-Reference

### Foundational Architecture

| Decision | ADR |
|----------|-----|
| Service decomposition | [ADR-001: Microservices Architecture](../adr/ADR-001-microservices-architecture.md) |
| Async messaging backbone | [ADR-002: Kafka as Messaging Backbone](../adr/ADR-002-kafka-messaging-backbone.md) |
| Database strategy | [ADR-003: PostgreSQL + Database-per-Service](../adr/ADR-003-postgresql-database-per-service.md) |
| Reliable event publishing | [ADR-004: Outbox Pattern](../adr/ADR-004-outbox-pattern-event-publishing.md) |
| Runner protocol | [ADR-005: gRPC for Runner Communication](../adr/ADR-005-grpc-runner-communication.md) |
| Multi-tenancy model | [ADR-006: Pool Multi-Tenancy Model](../adr/ADR-006-pool-multi-tenancy-model.md) |

### Security & Identity

| Decision | ADR |
|----------|-----|
| Secret management | [ADR-007: HashiCorp Vault](../adr/ADR-007-vault-secret-management.md) |
| Service-to-service identity | [ADR-008: mTLS Service-to-Service](../adr/ADR-008-mtls-service-to-service.md) |
| User authentication | [ADR-009: JWT RS256 + OAuth 2.0](../adr/ADR-009-jwt-oauth2-authentication.md) |
| Tenant data isolation | [ADR-013: PostgreSQL Row-Level Security](../adr/ADR-013-postgresql-rls-tenant-isolation.md) |
| Distributed tracing | [ADR-014: Tail-Based Sampling](../adr/ADR-014-tail-based-sampling.md) |
| Enterprise SSO | [ADR-030: SSO / SAML 2.0](../adr/ADR-030-sso-saml-enterprise-auth.md) |
| PII masking & GDPR | [ADR-032: PII Masking & GDPR Controls](../adr/ADR-032-pii-masking-gdpr.md) |

### Execution & Reliability

| Decision | ADR |
|----------|-----|
| Distributed transactions | [ADR-011: Saga Choreography](../adr/ADR-011-saga-choreography.md) |
| Autoscaling | [ADR-015: KEDA](../adr/ADR-015-keda-event-driven-autoscaling.md) |
| DAG engine & step execution | [ADR-018: DAG Engine & Step Execution](../adr/ADR-018-dag-engine-step-execution.md) |
| Durable execution | [ADR-024: Durable Execution & Checkpointing](../adr/ADR-024-durable-execution-checkpointing.md) |
| Backfill & concurrency | [ADR-025: Backfill & Concurrency Policy](../adr/ADR-025-backfill-concurrency-policy.md) |

### Data & Storage

| Decision | ADR |
|----------|-----|
| Shared ephemeral state | [ADR-012: Redis](../adr/ADR-012-redis-caching-locking.md) |
| Artifact storage | [ADR-021: MinIO](../adr/ADR-021-minio-artifact-storage.md) |
| PostgreSQL HA | [ADR-022: Patroni](../adr/ADR-022-patroni-postgresql-ha.md) |
| In-process transforms | [ADR-023: DuckDB on Runner](../adr/ADR-023-duckdb-runner-transforms.md) |

### Platform Services

| Decision | ADR |
|----------|-----|
| Deployment platform | [ADR-010: Kubernetes + Helm + Argo CD](../adr/ADR-010-kubernetes-helm-argocd.md) |
| Service mesh | [ADR-016: Istio](../adr/ADR-016-istio-service-mesh.md) |
| Pipeline event sourcing | [ADR-017: Event Sourcing for Pipeline Service](../adr/ADR-017-event-sourcing-pipeline-service.md) |
| Data lineage standard | [ADR-019: OpenLineage & Metadata Service](../adr/ADR-019-openlineage-metadata-service.md) |
| AI agent architecture | [ADR-020: Agent Service (Spring AI + ReAct)](../adr/ADR-020-agent-service-architecture.md) |
| Data quality contracts | [ADR-026: Data Quality Contracts](../adr/ADR-026-data-quality-contracts.md) |
| Notification delivery | [ADR-027: Notification Service](../adr/ADR-027-notification-service.md) |
| Compute billing | [ADR-028: Billing & Compute Metering](../adr/ADR-028-billing-compute-metering.md) |
| Connector management | [ADR-029: Connect Service & CDC](../adr/ADR-029-connect-service-cdc-management.md) |
| Feature flags | [ADR-031: OpenFeature](../adr/ADR-031-openfeature-feature-flags.md) |
| UI query API | [ADR-033: GraphQL alongside REST](../adr/ADR-033-graphql-api.md) |
