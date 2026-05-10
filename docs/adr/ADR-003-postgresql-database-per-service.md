# ADR-003: PostgreSQL with Database-per-Service

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah is a microservices system (ADR-001). The question of data storage has two parts: what database engine to use, and how to distribute data ownership across services.

**On distribution**: the most important property of a microservices architecture is independent deployability. If Service A and Service B share the same database schema, then:
- A schema migration for Service A can break Service B
- Service B's team must be consulted before Service A changes any table it touches
- Service A cannot be scaled independently from its data — both services hit the same connection pool
- In a multi-tenant system, Service A's query patterns (e.g., a heavy aggregation) can create lock contention that affects Service B's reads

Shared databases are the most common way that microservices revert to being a distributed monolith.

**On engine choice**: Pravah's data has the following characteristics:

| Entity | Access Pattern | Key Properties |
|--------|----------------|----------------|
| Pipelines | Read-heavy, complex queries (joins, filters, version history) | ACID, strong consistency |
| Pipeline executions | Write-heavy during execution, read for audit | ACID, append patterns, time-range queries |
| Jobs | High write throughput (state transitions), concurrent updates | ACID, row-level locking, upserts |
| Runners | Heartbeat writes, capacity reads | Low volume, ACID |
| Tenants | Read-heavy (cached), rare writes | ACID, strong consistency |
| Audit logs | Append-only, large volume, time-series queries | High write throughput, partitioning |

The dominant access pattern is relational: jobs belong to executions, executions belong to pipelines, pipelines belong to tenants. Multi-table JOINs are the normal way to query this data. A document database would denormalize all this into large documents, creating update anomalies and making aggregate queries across executions painful.

The secondary requirement is correctness: Pravah is a task execution platform. If a job's status is incorrectly updated due to a lost write or a race condition, a real computation may be double-executed or silently skipped. ACID transactions are not optional.

---

## Decision

**Database engine**: PostgreSQL is the standard RDBMS for all Pravah services.

**Data ownership**: each service owns exactly one database schema (logical database within a shared PostgreSQL cluster for the pool model, separate clusters in the silo model). No service reads or writes another service's tables directly.

```
┌──────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Cluster                            │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │  pipeline_db      │  │  execution_db     │  │  runner_db    │  │
│  │                  │  │                  │  │               │  │
│  │  pipelines       │  │  executions      │  │  runners      │  │
│  │  pipeline_steps  │  │  jobs            │  │  heartbeats   │  │
│  │  triggers        │  │  job_attempts    │  │  assignments  │  │
│  │  versions        │  │  outbox_events   │  │               │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │  tenant_db        │  │  audit_db         │                     │
│  │                  │  │                  │                     │
│  │  tenants         │  │  audit_events    │                     │
│  │  api_keys        │  │  (partitioned    │                     │
│  │  tenant_config   │  │   by month)      │                     │
│  └──────────────────┘  └──────────────────┘                     │
└──────────────────────────────────────────────────────────────────┘
```

**Cross-service data needs are resolved via events, not joins.** If the Execution Service needs a pipeline's step definitions to create jobs, it either:
1. Receives that data in the Kafka event (the Scheduler embeds it when publishing the trigger), or
2. Calls the Pipeline Service's API to fetch it at job creation time

The Execution Service never reaches into `pipeline_db` directly.

**Connection management**: PgBouncer runs as a sidecar to each service, providing connection pooling in transaction mode. This allows dozens of application threads to share a small pool of actual PostgreSQL connections (see Phase 3 theory for PgBouncer internals).

**Read replicas**: the Execution Service and Pipeline Service each have a dedicated read replica. Dashboard queries, export queries, and audit reads go to the replica; all writes and transactionally-sensitive reads go to the primary.

---

## Consequences

### Positive

- **Schema independence**: the Pipeline Service can add a column to `pipelines`, add an index, or run a VACUUM operation without any impact on the Execution Service. Services are deployable independently.
- **Failure isolation**: a long-running query in `audit_db` does not affect connection availability in `execution_db`. Each service's connection pool is independent.
- **Right-sized for each service**: `audit_db` can be tuned for append-only time-series workloads (large `work_mem`, aggressive autovacuum settings for partitioned tables). `execution_db` can be tuned for high-concurrency OLTP (conservative `work_mem`, short lock timeouts).
- **Strong consistency within a service**: all operations within a service that need ACID guarantees get them — job state transitions are transactional, and the outbox pattern (ADR-004) uses the same transaction to guarantee event publication.
- **PostgreSQL's rich feature set**: JSONB for flexible metadata, table partitioning for audit logs, LISTEN/NOTIFY for lightweight pub/sub, Row-Level Security for tenant isolation (ADR-013), and rich indexing (B-tree, GIN, partial) all apply without giving up the relational model.

### Negative

- **No cross-service transactions**: if the Pipeline Service and Execution Service need a consistent view of pipeline state and execution state simultaneously, there is no database transaction to provide it. Eventual consistency is the contract. The Saga pattern (ADR-011) handles cases where a multi-step operation must be atomic.
- **Cross-service joins are not possible**: aggregate reports that span pipelines (Pipeline Service) and their execution history (Execution Service) cannot use SQL JOINs. These must be assembled in application code, denormalized into a read model, or exported to a data warehouse.
- **Data duplication is necessary and intentional**: the Execution Service stores a `pipeline_id` (a foreign key to a table it does not own). Enforcing referential integrity across service boundaries is not possible at the database level. Application-level validation must substitute.
- **More schemas to migrate**: 5 separate databases each have their own migration lifecycle (Flyway / Liquibase per service). In practice this is manageable; each service's migration is part of its own CI/CD pipeline.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Shared cluster creates noisy-neighbor problem | Resource groups or separate PostgreSQL instances per service for high-traffic services |
| Connection pool exhaustion if PgBouncer is misconfigured | Pool size monitoring as a P1 alert; circuit breaker around DB calls |
| Audit_db grows without bound | Table partitioning by month + ILM policy to archive partitions older than 90 days to cold storage |
| Schema drift when services evolve | Each service owns its migration scripts; no schema changes are manual — all changes go through Flyway |

---

## Alternatives Considered

### Shared Database

A single PostgreSQL schema shared across all services. Simpler to query (JOINs work), simpler to deploy (one migration), simpler to manage.

Rejected because:
- It collapses the independence guarantees of the microservices architecture. One service's schema change affects all services.
- In a multi-tenant system, a single shared schema makes Row-Level Security enforcement more complex — each table must carry `tenant_id` and every query in every service must include it.
- Connection pool contention across services degrades reliability in unexpected ways.

### MongoDB / Document Store

A document database would store a pipeline as a single document with embedded steps, triggers, and configuration. Reads that fetch an entire pipeline would be fast (no joins). 

Rejected because:
- Pravah's query patterns are relational. Queries like "find all jobs for tenant X in execution state RUNNING that were created more than 1 hour ago" are natural SQL but awkward document queries.
- ACID transactions in MongoDB are a later addition and carry significant performance overhead compared to PostgreSQL's MVCC.
- The relational model better enforces data integrity (e.g., a job cannot reference a non-existent pipeline execution).

### CockroachDB / Distributed SQL

A distributed SQL database that provides horizontal write scalability without the application-level sharding complexity.

Rejected for the current scale because:
- PostgreSQL with a well-designed schema, connection pooling, and read replicas handles Pravah's target scale comfortably. CockroachDB's distributed transaction overhead is a cost paid regardless of whether it is needed.
- If Pravah reaches the scale where a single PostgreSQL primary cannot handle write throughput, horizontal sharding at the application level (see Phase 3 theory, Sharding Strategies) is the migration path — not a wholesale database replacement.
