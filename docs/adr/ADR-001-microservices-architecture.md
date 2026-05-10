# ADR-001: Microservices Architecture over Monolith

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah is a multi-tenant distributed pipeline platform. Its core responsibilities span four clearly distinct domains:

- **API surface**: accepting pipeline definitions, managing tenant configuration, exposing a management UI
- **Scheduling**: determining when pipelines should run based on cron expressions, triggers, and dependency graphs
- **Execution orchestration**: breaking pipelines into jobs, assigning them to runners, tracking their state
- **Runner fleet management**: registering runners, tracking heartbeats, managing capacity

Each domain has radically different operational characteristics:

| Domain | Traffic Pattern | Scaling Driver | Deployment Frequency |
|--------|----------------|----------------|----------------------|
| API Gateway | Bursty, user-facing | Request volume | High |
| Scheduler | Low throughput, critical correctness | Time, not load | Low |
| Execution Service | High throughput, stateful | Job queue depth | High |
| Runner Service | gRPC streaming, fleet-scale | Runner count | Medium |

In a monolith, these would share a single deployment unit, a single database schema, and a single failure boundary. A bug in the scheduler would take down the API. A schema migration for job execution would require downtime across all features. Scaling the execution layer would mean scaling the scheduler unnecessarily.

Pravah also has a hard requirement: the runner binary runs in customer infrastructure (on-premise, private cloud). This runner must communicate with the cloud control plane over a well-defined, versioned protocol. A monolith cannot cleanly expose such a protocol boundary.

---

## Decision

Pravah is built as a set of independent microservices, each with:

- Its own codebase and deployment lifecycle
- Its own database schema (database-per-service, see ADR-003)
- Its own scaling policy
- Communication over Kafka (async) or gRPC (sync, as needed)

The initial service decomposition:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Public Internet                          │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTPS
                     ┌──────────▼──────────┐
                     │    API Gateway       │  Route, Auth, Rate Limit
                     └──────────┬──────────┘
              ┌─────────────────┼───────────────────┐
              │                 │                   │
   ┌──────────▼────────┐ ┌──────▼──────┐ ┌─────────▼─────────┐
   │  Pipeline Service │ │  Tenant Svc │ │  Scheduler Service │
   │  (CRUD, versions) │ │  (config)   │ │  (cron, triggers)  │
   └──────────┬────────┘ └─────────────┘ └─────────┬─────────┘
              │ Kafka                               │ Kafka
   ┌──────────▼──────────────────────────────────▼─┘
   │                  Execution Service                          │
   │           (job lifecycle, state machine)                    │
   └──────────────────────┬──────────────────────────────────────┘
                          │ gRPC (streaming)
              ┌───────────▼───────────┐
              │    Runner Service      │  Fleet management, assignment
              └───────────────────────┘
                          │ gRPC (bidirectional)
              ┌───────────▼───────────┐
              │  Runner (on-premise)   │  Executes jobs in customer env
              └───────────────────────┘
```

---

## Consequences

### Positive

- **Independent deployability**: each service can be released without coordinating with others. The execution service can be hot-patched at 3am without touching the API.
- **Independent scaling**: the execution service scales with job queue depth; the scheduler stays at 2 replicas.
- **Fault isolation**: a crash in the Runner Service does not affect pipeline CRUD or scheduling. Each service has its own failure domain.
- **Technology optionality**: the runner binary is a separate Go/Rust binary that is independently versioned and distributed. This would be impossible inside a monolith.
- **Team boundaries**: as Pravah grows, teams can own individual services without stepping on each other.

### Negative

- **Operational complexity**: 5+ services to deploy, monitor, and debug rather than one. Distributed traces span multiple services. Log correlation requires trace IDs.
- **Network latency**: what was a function call is now a network hop. Each hop can fail, time out, or return partial results.
- **Distributed transactions**: operations that span multiple services cannot use database transactions. We need the Saga pattern (ADR-011) and the Outbox pattern (ADR-004).
- **Testing complexity**: integration tests require standing up multiple services or using contract testing.
- **Initial velocity cost**: the first three months of building microservices is slower than building a monolith, because infrastructure (service mesh, observability, deployment pipelines) must be built first.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Service discovery failure | Kubernetes DNS + readiness probes ensure only healthy services receive traffic |
| Cascade failures | Circuit breakers (Resilience4j) and bulkheads prevent one slow service from consuming all threads |
| Data consistency across services | Saga + Outbox pattern; eventual consistency is explicitly designed into the data model |
| Observability gaps | OpenTelemetry traces propagate across all service boundaries; no call is invisible |

---

## Alternatives Considered

### Modular Monolith

A single deployable with strong internal module boundaries. This would be simpler to operate and test. It was seriously considered for the v1 MVP. Rejected because:

1. The runner boundary is non-negotiable — the runner runs outside our infrastructure and must speak a versioned protocol. This forces a clean service boundary between the runner and the control plane regardless.
2. The scheduler has strong correctness requirements (leader election, distributed locking) that are easier to reason about in isolation.
3. Multi-tenancy isolation is stronger when each service enforces its own tenant context rather than relying on a shared module enforcing it.

### Serverless Functions (Lambda / Cloud Run)

Rejected because:
- Runners maintain long-lived bidirectional gRPC streams with the control plane. Serverless functions cannot maintain persistent connections.
- Job execution can run for hours. Serverless has hard execution time limits.
- Cold starts would add unacceptable latency to job dispatch.

### Service Mesh from Day One

Deferred (not rejected). Istio or Linkerd would simplify mTLS between internal services. However, the operational overhead of a service mesh at early scale is high. mTLS via `cert-manager` (ADR-008) provides the security benefit without the mesh's complexity until traffic volume justifies it.
