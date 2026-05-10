# Pravah — Theory Curriculum

> A complete engineering curriculum covering every concept needed to build and reason about a production-grade distributed pipeline platform.

Each chapter is a standalone reference document. You can read sequentially or jump to any topic. Every chapter connects concepts directly to Pravah's design, includes ASCII architecture diagrams, concrete code examples, and FAANG interview angles.

---

## Curriculum Map

```
Phase 1: Distributed Systems Fundamentals     ✅ 12/12 chapters
Phase 2: Messaging & Kafka Internals          ✅ 12/12 chapters
Phase 3: Database Design & Scaling            ✅  8/8  chapters
Phase 4: Observability & Reliability          ✅  7/7  chapters
Phase 5: Security, Auth & Multi-Tenancy       ✅  7/7  chapters
Phase 6: Kubernetes & Production Infra        ✅  6/6  chapters
─────────────────────────────────────────────────────────────────
Total: 52 chapters complete
```

---

## Phase Index

| Phase | Focus | Chapters | Directory |
|-------|-------|----------|-----------|
| **1** | Distributed Systems — CAP, consensus, clocks, failure modes, saga, outbox | 12 ✅ | [phase-1-distributed-systems/](phase-1-distributed-systems/README.md) |
| **2** | Kafka internals, producers, consumers, exactly-once, CDC, gRPC | 12 ✅ | [phase-2-kafka-messaging/](phase-2-kafka-messaging/README.md) |
| **3** | PostgreSQL internals, indexing, partitioning, PgBouncer, Redis, sharding | 8 ✅ | [phase-3-database-design/](phase-3-database-design/README.md) |
| **4** | SLOs, Prometheus, distributed tracing, structured logging, alerting, chaos | 7 ✅ | [phase-4-observability/](phase-4-observability/README.md) |
| **5** | JWT, mTLS, Vault, RBAC, multi-tenancy, OWASP, rate limiting | 7 ✅ | [phase-5-security/](phase-5-security/README.md) |
| **6** | Kubernetes workloads, Helm, autoscaling, networking, resource management | 6 ✅ | [phase-6-kubernetes/](phase-6-kubernetes/README.md) |

---

## How Each Chapter Is Structured

```
1. The Problem       What breaks without this concept?
2. Internals         How does it work — with ASCII diagrams and code
3. Pravah Context    Exactly where and how Pravah uses this
4. Trade-offs        What you give up; alternatives considered
5. Interview Angles  How FAANG interviewers probe this topic
6. Key Takeaways     Summary + link to the next chapter
```

---

## Chapter Index

### Phase 1 — Distributed Systems Fundamentals

| # | Chapter |
|---|---------|
| 1.1 | [CAP Theorem & Consistency](phase-1-distributed-systems/1.1-cap-theorem-and-consistency.md) |
| 1.2 | [Consistency Models](phase-1-distributed-systems/1.2-consistency-models.md) |
| 1.3 | [Distributed Clocks, Ordering & Causality](phase-1-distributed-systems/1.3-distributed-clocks-ordering-causality.md) |
| 1.4 | [Leader Election](phase-1-distributed-systems/1.4-leader-election.md) |
| 1.5 | [Consensus Algorithms — Raft & Paxos](phase-1-distributed-systems/1.5-consensus-algorithms-raft-paxos.md) |
| 1.6 | [Failure Modes](phase-1-distributed-systems/1.6-failure-modes.md) |
| 1.7 | [Idempotency & Exactly-Once](phase-1-distributed-systems/1.7-idempotency-exactly-once.md) |
| 1.8 | [Saga Pattern](phase-1-distributed-systems/1.8-saga-pattern.md) |
| 1.9 | [Event Sourcing & CQRS](phase-1-distributed-systems/1.9-event-sourcing-cqrs.md) |
| 1.10 | [Outbox Pattern](phase-1-distributed-systems/1.10-outbox-pattern.md) |
| 1.11 | [Circuit Breaker, Bulkhead & Backpressure](phase-1-distributed-systems/1.11-circuit-breaker-bulkhead-backpressure.md) |
| 1.12 | [Two-Phase Commit & Why We Avoid It](phase-1-distributed-systems/1.12-two-phase-commit.md) |

### Phase 2 — Messaging & Kafka Internals

| # | Chapter |
|---|---------|
| 2.1 | [Why Kafka](phase-2-kafka-messaging/2.1-why-kafka.md) |
| 2.2 | [Kafka Architecture](phase-2-kafka-messaging/2.2-kafka-architecture.md) |
| 2.3 | [Producer Internals](phase-2-kafka-messaging/2.3-producer-internals.md) |
| 2.4 | [Consumer Groups & Rebalancing](phase-2-kafka-messaging/2.4-consumer-groups-rebalancing.md) |
| 2.5 | [Partition Strategy](phase-2-kafka-messaging/2.5-partition-strategy.md) |
| 2.6 | [Exactly-Once & Kafka Transactions](phase-2-kafka-messaging/2.6-exactly-once-kafka-transactions.md) |
| 2.7 | [Schema Registry — Avro & Protobuf](phase-2-kafka-messaging/2.7-schema-registry-avro-protobuf.md) |
| 2.8 | [Dead Letter Queues & Poison Pill](phase-2-kafka-messaging/2.8-dead-letter-queues-poison-pill.md) |
| 2.9 | [Kafka Connect & CDC with Debezium](phase-2-kafka-messaging/2.9-kafka-connect-cdc-debezium.md) |
| 2.10 | [Kafka Streams vs Apache Flink](phase-2-kafka-messaging/2.10-kafka-streams-vs-flink.md) |
| 2.11 | [gRPC Internals](phase-2-kafka-messaging/2.11-grpc-internals.md) |
| 2.12 | [gRPC vs REST vs WebSocket](phase-2-kafka-messaging/2.12-grpc-vs-rest-vs-websocket.md) |

### Phase 3 — Database Design & Scaling

| # | Chapter |
|---|---------|
| 3.1 | [Database Per Service](phase-3-database-design/3.1-database-per-service.md) |
| 3.2 | [PostgreSQL Internals — MVCC, WAL & VACUUM](phase-3-database-design/3.2-postgresql-internals-mvcc-wal-vacuum.md) |
| 3.3 | [Indexing Strategies](phase-3-database-design/3.3-indexing-strategies.md) |
| 3.4 | [Table Partitioning](phase-3-database-design/3.4-table-partitioning.md) |
| 3.5 | [Connection Pooling & PgBouncer](phase-3-database-design/3.5-connection-pooling-pgbouncer.md) |
| 3.6 | [Read Replicas & Replication Lag](phase-3-database-design/3.6-read-replicas-replication-lag.md) |
| 3.7 | [Sharding Strategies](phase-3-database-design/3.7-sharding-strategies.md) |
| 3.8 | [Redis — Caching, Locking & Pub/Sub](phase-3-database-design/3.8-redis-caching-locking-pubsub.md) |

### Phase 4 — Observability & Reliability

| # | Chapter |
|---|---------|
| 4.1 | [SLOs, SLAs & Error Budgets](phase-4-observability/4.1-slos-slas-error-budgets.md) |
| 4.2 | [Metrics with Prometheus & Grafana](phase-4-observability/4.2-metrics-prometheus-grafana.md) |
| 4.3 | [Distributed Tracing with OpenTelemetry & Jaeger](phase-4-observability/4.3-distributed-tracing-opentelemetry-jaeger.md) |
| 4.4 | [Structured Logging & the ELK Stack](phase-4-observability/4.4-structured-logging-elk.md) |
| 4.5 | [Alerting Strategy](phase-4-observability/4.5-alerting-strategy.md) |
| 4.6 | [Health Checks, Readiness & Liveness Probes](phase-4-observability/4.6-health-checks-readiness-liveness.md) |
| 4.7 | [Chaos Engineering](phase-4-observability/4.7-chaos-engineering.md) |

### Phase 5 — Security, Auth & Multi-Tenancy

| # | Chapter |
|---|---------|
| 5.1 | [Threat Model & Security Principles](phase-5-security/5.1-threat-model-security-principles.md) |
| 5.2 | [JWT Authentication & OAuth 2.0](phase-5-security/5.2-jwt-authentication-oauth2.md) |
| 5.3 | [mTLS for Service-to-Service Communication](phase-5-security/5.3-mtls-service-to-service.md) |
| 5.4 | [Secret Management with HashiCorp Vault](phase-5-security/5.4-secret-management-vault.md) |
| 5.5 | [RBAC & Authorization](phase-5-security/5.5-rbac-authorization.md) |
| 5.6 | [Multi-Tenancy & Data Isolation](phase-5-security/5.6-multi-tenancy-data-isolation.md) |
| 5.7 | [API Security — Rate Limiting, Input Validation & OWASP](phase-5-security/5.7-api-security-rate-limiting-owasp.md) |

### Phase 6 — Kubernetes & Production Infrastructure

| # | Chapter |
|---|---------|
| 6.1 | [Kubernetes Workload Design](phase-6-kubernetes/6.1-kubernetes-workload-design.md) |
| 6.2 | [Helm Charts & Release Management](phase-6-kubernetes/6.2-helm-charts-release-management.md) |
| 6.3 | [Autoscaling — HPA, VPA & KEDA](phase-6-kubernetes/6.3-autoscaling-hpa-vpa-keda.md) |
| 6.4 | [Kubernetes Networking & Network Policies](phase-6-kubernetes/6.4-networking-network-policies.md) |
| 6.5 | [Resource Management — Requests, Limits & QoS](phase-6-kubernetes/6.5-resource-management-requests-limits-qos.md) |
| 6.6 | [Production Operations — Deployments, Canary & PDBs](phase-6-kubernetes/6.6-production-operations-deployments-canary-pdbs.md) |

---

## What Comes Next

The theory curriculum is complete. The next steps before implementation:

1. **Architecture Decision Records (ADRs)** — formal documents recording each major architectural decision, the alternatives considered, and the reasoning. The bridge between theory and code.

2. **High-Level Architecture Document** — a single reference showing the complete service map, data flows, and component interactions.

3. **Implementation** — starting with the core domain model and working outward.
