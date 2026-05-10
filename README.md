<div align="center">

# Pravah — प्रवाह

**Production-grade distributed ETL orchestration platform**

*Sanskrit: "flow" or "current"*

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=spring)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black?style=flat-square&logo=apache-kafka)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

</div>

---

## What Is Pravah?

Pravah is a **GitHub Actions / Airbyte / Prefect-level ETL platform** built from scratch — designed as an engineering deep-dive and FAANG-level portfolio project.

It handles the full lifecycle of data pipelines: scheduling, distributed execution across self-hosted and cloud runners, lineage tracking, observability, and an agentic layer that can detect and heal schema drift automatically.

### Core Capabilities

| Capability | Description |
|---|---|
| **Pipeline Orchestration** | DAG-based, cron-triggered, event-driven; dependency resolution and backfill |
| **Hybrid Runner Model** | Self-hosted runners (on-prem data access) + cloud runners (auto-scaled K8s pods) |
| **Kafka Event Bus** | Decoupled job lifecycle, lineage events, CDC ingestion via Debezium |
| **Multi-Tenancy** | Row-level security, namespace isolation, per-tenant resource quotas |
| **Agentic Layer** | LLM-powered auto-heal, schema drift detection, natural-language pipeline builder |
| **Full Observability** | Prometheus → Grafana dashboards, distributed tracing (Jaeger), ELK log aggregation |
| **Data Lineage** | Column-level lineage via OpenLineage spec, stored in Elasticsearch |

---

## Architecture Overview

```
┌─────────────────────────────────── CONTROL PLANE ────────────────────────────────────┐
│                                                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │  Pipeline    │  │  Scheduler   │  │  Execution   │  │  Agent Service           │  │
│  │  Service     │  │  Service     │  │  Service     │  │  (LLM / auto-heal)       │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────────────────────────┘  │
│         └─────────────────┴──────────────────┘                                        │
│                            gRPC mesh (internal)                                       │
│                                    │                                                  │
│  ┌─────────────┐  ┌────────────────▼──────────────┐  ┌──────────────────────────┐   │
│  │ API Gateway │  │       Kafka Cluster            │  │ Observability Stack      │   │
│  │ (REST + WS) │  │  job.assigned · job.result     │  │ Prometheus · Grafana     │   │
│  │             │  │  runner.heartbeat · audit.log  │  │ Jaeger · ELK             │   │
│  └─────────────┘  └───────────────────────────────┘  └──────────────────────────┘   │
│                                                                                       │
│  ┌────────────────────────────────────────────────────────────────────────────────┐  │
│  │                              Data Layer                                         │  │
│  │  PostgreSQL (metadata)  ·  Redis (state/cache)  ·  MinIO (artifacts)           │  │
│  │  Elasticsearch (logs/lineage)  ·  HashiCorp Vault (secrets)                    │  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────┬────────────────────────────────────────────────┘
                                       │
                        gRPC Bidirectional Streaming + mTLS
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
   ┌──────────▼──────────┐  ┌──────────▼──────────┐  ┌─────────▼──────────┐
   │   Self-Hosted Runner │  │  Cloud Runner (K8s) │  │  Ephemeral Runner  │
   │   On-prem data access│  │  Auto-scaled pods   │  │  Per-job pod       │
   │   JAR / Docker       │  │  Managed by Pravah  │  │  Destroyed on done │
   └──────────────────────┘  └──────────────────────┘  └────────────────────┘
```

> **Data flow for a single pipeline run:** UI/API/Cron → Scheduler resolves DAG → `job.assigned` to Kafka → Execution Service assigns runner → Job pushed via gRPC stream → Runner extracts, transforms (DuckDB), loads → Lineage event emitted → Agent monitors for anomalies → Notification fires if configured.

See [`docs/design/system-architecture.md`](docs/design/system-architecture.md) for the complete design document.

---

## Tech Stack

```
┌─────────────────┬──────────────────────────────────────────────────────────────┐
│ Layer           │ Technology                                                   │
├─────────────────┼──────────────────────────────────────────────────────────────┤
│ Services        │ Java 21 · Spring Boot 3 · Spring Cloud                       │
│ Inter-service   │ gRPC (sync internal calls) + Kafka (async events)            │
│ Runner protocol │ gRPC bidirectional stream · mTLS per-runner certs            │
│ API / UI        │ REST + WebSocket via Spring Cloud Gateway                    │
│ Database        │ PostgreSQL 16 + Flyway migrations                            │
│ Cache / State   │ Redis (Redisson) — locks, pub/sub, job state                 │
│ Object Store    │ MinIO (S3-compatible) — artifacts, logs, snapshots            │
│ Search / Lineage│ Elasticsearch — log search, data catalog, lineage graph      │
│ Secrets         │ HashiCorp Vault — dynamic credentials, secret rotation       │
│ Processing      │ DuckDB (embedded, per runner) — in-process transforms        │
│ Observability   │ Prometheus + Grafana + Jaeger + ELK                          │
│ Auth            │ Spring Security + JWT (API) + mTLS (runners)                 │
│ Infra           │ Docker Compose (local) → Kubernetes + Helm (production)      │
│ UI              │ Next.js 14 · React Flow (DAG editor) · TailwindCSS           │
│ Agent           │ Spring AI + Claude API                                       │
└─────────────────┴──────────────────────────────────────────────────────────────┘
```

---

## Repository Layout

```
Pravah/
├── README.md
│
├── docs/
│   ├── design/
│   │   └── system-architecture.md    # Full architecture, data flow, service contracts
│   ├── adr/                          # Architecture Decision Records
│   └── theory/                       # Chapter-by-chapter theory curriculum
│       ├── 00-curriculum-overview.md
│       ├── phase-1-distributed-systems/   README.md + 12 chapters
│       ├── phase-2-kafka-messaging/       README.md + 12 chapters
│       └── phase-3-database-design/       README.md + chapters (growing)
│
├── implementation/                   # Source code — added as theory completes
│   ├── services/                     # Spring Boot microservices
│   ├── runner/                       # Standalone runner JAR
│   ├── proto/                        # Protobuf contracts
│   └── infra/                        # Docker Compose, Helm charts
│
└── scripts/                          # Migration scripts, partition management, tooling
```

---

## Theory Curriculum

The [`docs/theory/`](docs/theory/) directory is a **self-contained engineering course** — covering every concept needed to build and reason about a distributed system at this scale. Each chapter is a standalone reference document with architecture diagrams, code examples, and interview angles.

---

### Phase 1 — Distributed Systems Fundamentals ✅

> The foundation: how distributed systems make guarantees, fail, communicate, and stay correct under chaos.

| # | Chapter | Core Concept |
|---|---------|-------------|
| [1.1](docs/theory/phase-1-distributed-systems/1.1-cap-theorem-and-consistency.md) | CAP Theorem & Pravah's Consistency Choices | CP vs AP per component |
| [1.2](docs/theory/phase-1-distributed-systems/1.2-consistency-models.md) | Consistency Models | Linearizability → Sequential → Causal → Eventual |
| [1.3](docs/theory/phase-1-distributed-systems/1.3-distributed-clocks-ordering-causality.md) | Distributed Clocks, Ordering & Causality | Lamport timestamps, vector clocks |
| [1.4](docs/theory/phase-1-distributed-systems/1.4-leader-election.md) | Leader Election | Fencing tokens, split-brain prevention |
| [1.5](docs/theory/phase-1-distributed-systems/1.5-consensus-algorithms-raft-paxos.md) | Consensus — Raft & Paxos | How etcd / ZooKeeper actually work |
| [1.6](docs/theory/phase-1-distributed-systems/1.6-failure-modes.md) | Failure Modes | Crash, Byzantine, partition; detection strategies |
| [1.7](docs/theory/phase-1-distributed-systems/1.7-idempotency-exactly-once.md) | Idempotency & Exactly-Once | Idempotency keys, deduplication, retry safety |
| [1.8](docs/theory/phase-1-distributed-systems/1.8-saga-pattern.md) | Saga Pattern | Orchestration vs choreography; compensating transactions |
| [1.9](docs/theory/phase-1-distributed-systems/1.9-event-sourcing-cqrs.md) | Event Sourcing & CQRS | Immutable event log, time travel, read/write split |
| [1.10](docs/theory/phase-1-distributed-systems/1.10-outbox-pattern.md) | The Outbox Pattern | Atomic writes across DB and Kafka without 2PC |
| [1.11](docs/theory/phase-1-distributed-systems/1.11-circuit-breaker-bulkhead-backpressure.md) | Circuit Breaker, Bulkhead & Backpressure | Resilience4j; cascading failure prevention |
| [1.12](docs/theory/phase-1-distributed-systems/1.12-two-phase-commit.md) | Two-Phase Commit & Why We Avoid It | 2PC failure modes; Pravah's alternative stack |

---

### Phase 2 — Messaging & Kafka Internals ✅

> Kafka as Pravah's nervous system: internals, producers, consumers, schema evolution, CDC, streaming, and gRPC.

| # | Chapter | Core Concept |
|---|---------|-------------|
| [2.1](docs/theory/phase-2-kafka-messaging/2.1-why-kafka.md) | Why Kafka Over RabbitMQ / SQS | Log-based vs queue-based; 5 Pravah requirements |
| [2.2](docs/theory/phase-2-kafka-messaging/2.2-kafka-architecture.md) | Kafka Architecture | Brokers, topics, partitions, offsets, ISR |
| [2.3](docs/theory/phase-2-kafka-messaging/2.3-producer-internals.md) | Producer Internals | Batching, compression, `acks`, idempotent producers |
| [2.4](docs/theory/phase-2-kafka-messaging/2.4-consumer-groups-rebalancing.md) | Consumer Groups & Rebalancing | Partition assignment, offset commits, cooperative rebalancing |
| [2.5](docs/theory/phase-2-kafka-messaging/2.5-partition-strategy.md) | Partition Strategy | Key selection, hot partitions, custom partitioners |
| [2.6](docs/theory/phase-2-kafka-messaging/2.6-exactly-once-kafka-transactions.md) | Exactly-Once: Kafka Transactions | Idempotent producer, transaction coordinator, `consume-transform-produce` |
| [2.7](docs/theory/phase-2-kafka-messaging/2.7-schema-registry-avro-protobuf.md) | Schema Registry | Avro / Protobuf, compatibility modes, schema evolution |
| [2.8](docs/theory/phase-2-kafka-messaging/2.8-dead-letter-queues-poison-pill.md) | Dead Letter Queues | Poison pill detection, DLQ routing, replay UI |
| [2.9](docs/theory/phase-2-kafka-messaging/2.9-kafka-connect-cdc-debezium.md) | Kafka Connect & CDC | Debezium WAL capture, connector lifecycle, lag monitoring |
| [2.10](docs/theory/phase-2-kafka-messaging/2.10-kafka-streams-vs-flink.md) | Kafka Streams vs Flink | Event time, windowing, watermarks, when to use each |
| [2.11](docs/theory/phase-2-kafka-messaging/2.11-grpc-internals.md) | gRPC Internals | HTTP/2 frames, bidirectional streaming, flow control, Netty |
| [2.12](docs/theory/phase-2-kafka-messaging/2.12-grpc-vs-rest-vs-websocket.md) | gRPC vs REST vs WebSocket | Protocol selection per use-case across Pravah |

---

### Phase 3 — Database Design & Scaling 🔄

> PostgreSQL deep-dive: schema design, MVCC internals, indexing, partitioning, connection pooling, replication.

| # | Chapter | Core Concept | Status |
|---|---------|-------------|--------|
| [3.1](docs/theory/phase-3-database-design/3.1-database-per-service.md) | Database Per Service | Isolation strategies, cross-service data access patterns | ✅ |
| [3.2](docs/theory/phase-3-database-design/3.2-postgresql-internals-mvcc-wal-vacuum.md) | PostgreSQL Internals: MVCC, WAL & Vacuum | How PG handles concurrency without locking | ✅ |
| [3.3](docs/theory/phase-3-database-design/3.3-indexing-strategies.md) | Indexing Strategies | B-tree, GIN, BRIN, partial, covering — per Pravah table | ✅ |
| [3.4](docs/theory/phase-3-database-design/3.4-table-partitioning.md) | Table Partitioning | Monthly range partitioning of `job_runs`, `pg_partman` | ✅ |
| 3.5 | Connection Pooling & PgBouncer | Transaction-mode pooling, gotchas with prepared statements | 🔜 |
| 3.6 | Read Replicas & Replication Lag | Streaming replication, lag handling, read routing | 📋 |
| 3.7 | Sharding Strategies | When PG isn't enough; Citus, application-level sharding | 📋 |
| 3.8 | Redis — Caching, Locking & Pub/Sub | Cache patterns, distributed locks, Lua scripts | 📋 |

---

### Phases 4–6 — Planned

| Phase | Topic Area |
|-------|-----------|
| **Phase 4** | Observability & Reliability — SLOs, error budgets, distributed tracing, alerting |
| **Phase 5** | Security, Auth & Multi-Tenancy — JWT, mTLS, Vault, RBAC, tenant isolation |
| **Phase 6** | Kubernetes, Helm & Production Infra — deployments, autoscaling, GitOps |

---

## Project Status

```
Theory
  ✅  Phase 1 — Distributed Systems Fundamentals    (12/12 chapters)
  ✅  Phase 2 — Messaging & Kafka Internals         (12/12 chapters)
  🔄  Phase 3 — Database Design & Scaling           (4/12 chapters, in progress)
  📋  Phase 4 — Observability & Reliability         (planned)
  📋  Phase 5 — Security, Auth & Multi-Tenancy      (planned)
  📋  Phase 6 — Kubernetes & Production Infra       (planned)

Implementation
  📋  Service skeletons (Gradle multi-module monorepo)
  📋  Proto contracts for all gRPC services
  📋  Database schemas + Flyway migrations
  📋  Docker Compose local dev environment
  📋  Kubernetes + Helm charts
```

---

## Getting Started

```bash
# Clone the repo
git clone https://github.com/yourusername/Pravah.git
cd Pravah

# Start reading the theory curriculum
open docs/theory/phase-1-distributed-systems/README.md

# Or start from the system architecture
open docs/design/system-architecture.md
```

> Implementation bootstrap instructions will be added here once the service skeletons are committed.

---

<div align="center">

Built as a serious engineering study — every design decision is intentional, documented, and interview-ready.

</div>
