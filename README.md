# Pravah

**Pravah** (प्रवाह) — Sanskrit for *flow* or *current*. A production-grade distributed ETL
orchestration platform, built from scratch as an engineering deep-dive and FAANG-level portfolio project.

---

## What Pravah Is

Pravah is a GitHub Actions / Airbyte / Prefect-level ETL platform with:

- **Self-hosted and cloud runners** connected via gRPC bidirectional streaming + mTLS
- **Kafka-backed event bus** for job lifecycle events, lineage, and observability
- **Multi-tenant pipeline orchestration** — DAG-based, cron + event-driven, with backfill
- **Agentic layer** — auto-heal, schema drift detection, natural-language pipeline builder (via LLM)
- **Full observability** — Prometheus + Grafana + Jaeger + ELK

See [`docs/design/system-architecture.md`](docs/design/system-architecture.md) for the complete
architecture, data flow, and tech stack.

---

## Repository Layout

```
Pravah/
├── docs/
│   ├── design/                     # System architecture, ADRs, data model
│   │   └── system-architecture.md
│   ├── adr/                        # Architecture Decision Records
│   └── theory/                     # Chapter-by-chapter theory curriculum
│       ├── 00-curriculum-overview.md
│       ├── phase-1-distributed-systems/
│       ├── phase-2-kafka-messaging/
│       └── phase-3-database-design/
├── implementation/                 # Source code (services, runner, proto, infra)
├── scripts/                        # Utility scripts (migrations, partition mgmt, etc.)
└── README.md
```

---

## Theory Curriculum

The curriculum is a self-contained course covering everything needed to build and understand
a production distributed system at this scale. Each chapter is a standalone markdown file.

### Phase 1 — Distributed Systems Fundamentals ✅

| # | Topic | File |
|---|-------|------|
| 1.1 | CAP Theorem & How Pravah Makes Consistency Choices | [1.1-cap-theorem-and-consistency.md](docs/theory/phase-1-distributed-systems/1.1-cap-theorem-and-consistency.md) |
| 1.2 | Consistency Models — Strong, Sequential, Causal, Eventual | [1.2-consistency-models.md](docs/theory/phase-1-distributed-systems/1.2-consistency-models.md) |
| 1.3 | Distributed Clocks, Ordering & Causality | [1.3-distributed-clocks-ordering-causality.md](docs/theory/phase-1-distributed-systems/1.3-distributed-clocks-ordering-causality.md) |
| 1.4 | Leader Election & Why Pravah's Scheduler Needs It | [1.4-leader-election.md](docs/theory/phase-1-distributed-systems/1.4-leader-election.md) |
| 1.5 | Consensus Algorithms — Raft & Paxos | [1.5-consensus-algorithms-raft-paxos.md](docs/theory/phase-1-distributed-systems/1.5-consensus-algorithms-raft-paxos.md) |
| 1.6 | Failure Modes: Crash, Byzantine & Network Partitions | [1.6-failure-modes.md](docs/theory/phase-1-distributed-systems/1.6-failure-modes.md) |
| 1.7 | Idempotency & Exactly-Once Semantics | [1.7-idempotency-exactly-once.md](docs/theory/phase-1-distributed-systems/1.7-idempotency-exactly-once.md) |
| 1.8 | Saga Pattern — Orchestration vs Choreography | [1.8-saga-pattern.md](docs/theory/phase-1-distributed-systems/1.8-saga-pattern.md) |
| 1.9 | Event Sourcing & CQRS | [1.9-event-sourcing-cqrs.md](docs/theory/phase-1-distributed-systems/1.9-event-sourcing-cqrs.md) |
| 1.10 | The Outbox Pattern | [1.10-outbox-pattern.md](docs/theory/phase-1-distributed-systems/1.10-outbox-pattern.md) |
| 1.11 | Circuit Breaker, Bulkhead & Backpressure | [1.11-circuit-breaker-bulkhead-backpressure.md](docs/theory/phase-1-distributed-systems/1.11-circuit-breaker-bulkhead-backpressure.md) |
| 1.12 | Two-Phase Commit & Why We Avoid It | [1.12-two-phase-commit.md](docs/theory/phase-1-distributed-systems/1.12-two-phase-commit.md) |

### Phase 2 — Messaging & Kafka Internals ✅

| # | Topic | File |
|---|-------|------|
| 2.1 | Why Kafka Over RabbitMQ / SQS for Pravah | [2.1-why-kafka.md](docs/theory/phase-2-kafka-messaging/2.1-why-kafka.md) |
| 2.2 | Kafka Architecture: Brokers, Topics, Partitions & Offsets | [2.2-kafka-architecture.md](docs/theory/phase-2-kafka-messaging/2.2-kafka-architecture.md) |
| 2.3 | Producer Internals: Batching, Compression & Acknowledgement | [2.3-producer-internals.md](docs/theory/phase-2-kafka-messaging/2.3-producer-internals.md) |
| 2.4 | Consumer Groups, Rebalancing & Offset Management | [2.4-consumer-groups-rebalancing.md](docs/theory/phase-2-kafka-messaging/2.4-consumer-groups-rebalancing.md) |
| 2.5 | Partition Strategy for Pravah's Topics | [2.5-partition-strategy.md](docs/theory/phase-2-kafka-messaging/2.5-partition-strategy.md) |
| 2.6 | Exactly-Once in Kafka: Transactions | [2.6-exactly-once-kafka-transactions.md](docs/theory/phase-2-kafka-messaging/2.6-exactly-once-kafka-transactions.md) |
| 2.7 | Schema Registry — Avro/Protobuf Versioning | [2.7-schema-registry-avro-protobuf.md](docs/theory/phase-2-kafka-messaging/2.7-schema-registry-avro-protobuf.md) |
| 2.8 | Dead Letter Queues & Poison Pill Handling | [2.8-dead-letter-queues-poison-pill.md](docs/theory/phase-2-kafka-messaging/2.8-dead-letter-queues-poison-pill.md) |
| 2.9 | Kafka Connect & CDC with Debezium | [2.9-kafka-connect-cdc-debezium.md](docs/theory/phase-2-kafka-messaging/2.9-kafka-connect-cdc-debezium.md) |
| 2.10 | Kafka Streams vs Flink for Streaming Pipelines | [2.10-kafka-streams-vs-flink.md](docs/theory/phase-2-kafka-messaging/2.10-kafka-streams-vs-flink.md) |
| 2.11 | gRPC Internals: HTTP/2, Bidirectional Streaming & Flow Control | [2.11-grpc-internals.md](docs/theory/phase-2-kafka-messaging/2.11-grpc-internals.md) |
| 2.12 | gRPC vs REST vs WebSocket — When to Use What | [2.12-grpc-vs-rest-vs-websocket.md](docs/theory/phase-2-kafka-messaging/2.12-grpc-vs-rest-vs-websocket.md) |

### Phase 3 — Database Design & Scaling 🔄 (in progress)

| # | Topic | File |
|---|-------|------|
| 3.1 | Database Per Service — Why & How | [3.1-database-per-service.md](docs/theory/phase-3-database-design/3.1-database-per-service.md) |
| 3.2 | PostgreSQL Internals: MVCC, WAL & Vacuum | [3.2-postgresql-internals-mvcc-wal-vacuum.md](docs/theory/phase-3-database-design/3.2-postgresql-internals-mvcc-wal-vacuum.md) |
| 3.3 | Indexing Strategies for Pravah's Tables | [3.3-indexing-strategies.md](docs/theory/phase-3-database-design/3.3-indexing-strategies.md) |
| 3.4 | Table Partitioning: Splitting `job_runs` by Date | [3.4-table-partitioning.md](docs/theory/phase-3-database-design/3.4-table-partitioning.md) |
| 3.5 | Connection Pooling & PgBouncer | *(coming up next)* |
| 3.6 | Read Replicas & Replication Lag | *(planned)* |
| 3.7 | Sharding Strategies | *(planned)* |
| 3.8 | Redis — Caching, Locking & Pub/Sub | *(planned)* |

### Phase 4 — Observability & Reliability *(planned)*

### Phase 5 — Security, Auth & Multi-Tenancy *(planned)*

### Phase 6 — Kubernetes, Helm & Production Infra *(planned)*

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Services | Java 21, Spring Boot 3, Spring Cloud |
| Inter-service | gRPC (internal) + Kafka (events) |
| Runner protocol | gRPC bidirectional stream + mTLS |
| API to UI | REST + WebSocket (Spring Cloud Gateway) |
| Database | PostgreSQL + Flyway migrations |
| Cache / State | Redis (Redisson) |
| Object Store | MinIO (S3-compatible) |
| Search / Lineage | Elasticsearch |
| Secrets | HashiCorp Vault |
| Processing | DuckDB (embedded, per runner) |
| Observability | Prometheus + Grafana + Jaeger + ELK |
| Auth | Spring Security + JWT + mTLS |
| Infra | Docker Compose → Kubernetes + Helm |
| UI | Next.js 14, React Flow, TailwindCSS |
| Agent | Spring AI + Claude API |

---

## Status

- [x] Theory: Phase 1 — Distributed Systems Fundamentals
- [x] Theory: Phase 2 — Messaging & Kafka Internals
- [x] Theory: Phase 3 (3.1–3.4) — Database Design & Scaling (in progress)
- [ ] Implementation: service skeletons, proto contracts, DB schemas
- [ ] Docker Compose local dev environment
