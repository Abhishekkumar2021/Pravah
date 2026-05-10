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
│ Inter-service   │ gRPC (sync/streaming) + Kafka (async events)                 │
│ Runner protocol │ gRPC bidirectional stream · mTLS per-runner certs            │
│ External API    │ REST (mutations, SDK) + GraphQL (UI read queries)            │
│ Database        │ PostgreSQL 16 + Flyway · Patroni HA (auto-failover)          │
│ Cache / State   │ Redis Sentinel — locks, pub/sub, rate limiting, session state │
│ Object Store    │ MinIO (S3-compatible) — artifacts, logs, snapshots            │
│ Search / Lineage│ Elasticsearch — log search, data catalog, lineage graph      │
│ Secrets         │ HashiCorp Vault — dynamic credentials, mTLS PKI, Transit enc │
│ Processing      │ DuckDB (embedded, per runner) — in-process SQL transforms    │
│ Observability   │ Prometheus + Grafana + OpenTelemetry + Jaeger + ELK          │
│ Auth            │ JWT RS256 + OAuth 2.0 + SSO (OIDC/SAML 2.0) + mTLS         │
│ Feature flags   │ OpenFeature SDK — Redis provider, kill switches, rollouts    │
│ Data lineage    │ OpenLineage spec — column-level, stored in Elasticsearch      │
│ Service mesh    │ Istio — L7 AuthorizationPolicy, traffic management           │
│ Infra / GitOps  │ Kubernetes + Helm + Argo CD + KEDA                          │
│ UI              │ Next.js 14 · React Flow (DAG canvas) · TailwindCSS           │
│ Agent           │ Spring AI · Claude / GPT-4 · ReAct · pgvector               │
└─────────────────┴──────────────────────────────────────────────────────────────┘
```

---

## Repository Layout

```
Pravah/
├── README.md
│
├── docs/
│   ├── architecture/
│   │   └── high-level-architecture.md    # Full service map, data flows, infra
│   ├── adr/                              # Architecture Decision Records (ADR-001–033)
│   ├── design/                           # Extended design documents
│   └── theory/                           # 9-phase engineering curriculum (75 chapters)
│       ├── README.md                     # Full chapter index
│       ├── phase-1-distributed-systems/  # 12 chapters ✅
│       ├── phase-2-kafka-messaging/      # 12 chapters ✅
│       ├── phase-3-database-design/      # 13 chapters ✅
│       ├── phase-4-observability/        #  7 chapters ✅
│       ├── phase-5-security/             #  7 chapters ✅
│       ├── phase-6-kubernetes/           #  6 chapters ✅
│       ├── phase-7-ai-agent-architecture/#  7 chapters ✅
│       ├── phase-8-etl-data-engineering/ #  7 chapters ✅
│       └── phase-9-system-design-synthesis/ # 4 chapters ✅
│
├── implementation/                       # Source code — starts next
│   ├── services/                         # Spring Boot microservices
│   ├── runner/                           # Standalone runner binary
│   ├── proto/                            # Protobuf contracts
│   └── infra/                            # Docker Compose, Helm charts
│
└── scripts/                              # Migration scripts, tooling
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

### Phase 3 — Database Design & Scaling ✅

> PostgreSQL deep-dive: MVCC, WAL, indexing, partitioning, PgBouncer, replication, sharding, Redis, Elasticsearch, cold storage archival.

See [docs/theory/phase-3-database-design/](docs/theory/phase-3-database-design/README.md) — 13 chapters complete.

---

### Phases 4–9 ✅

| Phase | Topic Area | Chapters |
|-------|-----------|----------|
| **Phase 4** | Observability & Reliability — SLOs, Prometheus, tracing, logging, alerting, chaos | 7 ✅ |
| **Phase 5** | Security, Auth & Multi-Tenancy — JWT, mTLS, Vault, RBAC, RLS, OWASP | 7 ✅ |
| **Phase 6** | Kubernetes, Helm & Production Infra — workloads, autoscaling, GitOps, PDBs | 6 ✅ |
| **Phase 7** | AI/Agent Architecture — LLM fundamentals, ReAct, Spring AI, RAG, memory, evals | 7 ✅ |
| **Phase 8** | ETL & Data Engineering — DAG design, CDC, data contracts, backfill, lineage, DuckDB | 7 ✅ |
| **Phase 9** | System Design Synthesis — full walkthrough, capacity planning, bottleneck analysis, interview prep | 4 ✅ |

→ Full chapter index: [docs/theory/README.md](docs/theory/README.md)

---

## Architecture & Design Documents

| Document | Description |
|----------|-------------|
| [High-Level Architecture](docs/architecture/high-level-architecture.md) | Complete service map, data flows, infrastructure, security, multi-tenancy, observability |
| [Service API & Event Contracts](docs/design/system-architecture.md) | REST endpoints, GraphQL schema, gRPC protobuf definitions, Kafka event schemas, DB schema summaries |
| [ADR Index](docs/adr/README.md) | 33 Architecture Decision Records — every major design decision |

---

## Project Status

```
Theory (75 chapters — complete)
  ✅  Phase 1 — Distributed Systems Fundamentals    (12/12 chapters)
  ✅  Phase 2 — Messaging & Kafka Internals         (12/12 chapters)
  ✅  Phase 3 — Database Design & Scaling           (13/13 chapters)
  ✅  Phase 4 — Observability & Reliability          (7/7  chapters)
  ✅  Phase 5 — Security, Auth & Multi-Tenancy       (7/7  chapters)
  ✅  Phase 6 — Kubernetes & Production Infra        (6/6  chapters)
  ✅  Phase 7 — AI/Agent Architecture                (7/7  chapters)
  ✅  Phase 8 — ETL & Data Engineering               (7/7  chapters)
  ✅  Phase 9 — System Design Synthesis              (4/4  chapters)

Architecture (complete)
  ✅  33 Architecture Decision Records (ADR-001 through ADR-033)
  ✅  High-Level Architecture — full service map, all 11 services, all data flows

Implementation  ← starts next
  📋  Gradle multi-module project skeleton
  📋  Protobuf contracts for all gRPC services
  📋  Database schemas + Flyway migrations (per service)
  📋  Docker Compose local dev environment
  📋  Kubernetes + Helm charts
```

---

## Getting Started

```bash
# Clone the repo
git clone https://github.com/yourusername/Pravah.git
cd Pravah

# Start from the theory curriculum
open docs/theory/README.md

# Or start from the architecture
open docs/architecture/high-level-architecture.md

# Explore the full ADR set
open docs/adr/README.md
```

> Implementation bootstrap instructions will be added once the service skeletons are committed.

---

<div align="center">

Built as a serious engineering study — every design decision is intentional, documented, and interview-ready.

</div>
