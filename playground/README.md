# Pravah Playground

> Hands-on exercises to solidify understanding of every tool and concept used in Pravah — before touching the real implementation.
>
> Each exercise is a **self-contained mini-project**: its own Docker Compose, its own Spring Boot application, and a clear set of things to prove. The README for each exercise links to the exact ADRs it validates.

---

## Philosophy

Reading theory is not the same as building it. These exercises exist to answer the question: *"I know how this works on paper — now can I make it work on my machine?"*

Each exercise simulates a real scenario from Pravah — not a toy example. When you finish the playground, every line of code in the real implementation will feel familiar.

---

## Exercise Index

| # | Exercise | Tools | Pravah ADRs | Status |
|---|----------|-------|-------------|--------|
| [01](01-kafka/) | Kafka — Producer, Consumer, Exactly-Once, DLQ | Kafka, Schema Registry, Avro | ADR-002, ADR-006 | Complete |
| [02](02-grpc/) | gRPC — Bidirectional Streaming + mTLS | gRPC, Protobuf, cert-manager | ADR-005, ADR-008 | Ready |
| [03](03-postgres-advanced/) | PostgreSQL — Partitioning, RLS, PgBouncer | PostgreSQL, PgBouncer | ADR-003, ADR-013, ADR-022 | Complete |
| [04](04-redis/) | Redis — Distributed Lock + Token Bucket Rate Limiter | Redis, Lua | ADR-012 | Ready |
| [05](05-vault/) | Vault — Dynamic DB Credentials + PKI | HashiCorp Vault, Kubernetes Auth | ADR-007, ADR-008 | Ready |
| [06](06-outbox-pattern/) | Outbox Pattern — Atomic publish with crash recovery | PostgreSQL, Kafka | ADR-004 | Ready |
| [07](07-saga/) | Saga — Choreography with compensation | Kafka, Spring Boot | ADR-011 | Ready |
| [08](08-duckdb/) | DuckDB — In-process SQL transforms on real data | DuckDB, Parquet | ADR-023 | Ready |
| [09](09-spring-ai/) | Spring AI — ReAct agent with real tools | Spring AI, Gemini API | ADR-020 | Ready (needs API quota) |
| [10](10-kubernetes/) | Kubernetes — KEDA autoscaling on Kafka lag | Kubernetes, KEDA, Kind | ADR-010, ADR-015 | Ready |
| [11](11-opentelemetry/) | OpenTelemetry — Distributed trace across 2 services | OTel, Jaeger | ADR-014 | Ready |
| [12](12-graphql/) | GraphQL — DataLoader batching + field-level auth | Spring for GraphQL | ADR-033 | Scaffold |

---

## How Each Exercise Is Structured

```
playground/NN-name/
├── README.md           ← what to build, what to prove, step-by-step tasks
├── docker-compose.yml  ← infrastructure only (Kafka, PG, Redis, etc.)
├── build.gradle.kts    ← Gradle build for the exercise app
├── settings.gradle.kts
└── src/
    ├── main/java/      ← skeleton code with TODOs to fill in
    └── test/java/      ← integration tests that prove correctness
```

**The README tells you what to build. The code skeleton tells you where. The tests tell you if you got it right.**

---

## Prerequisites

All exercises require:

```bash
# Java 21
java -version  # should say 21

# Docker + Docker Compose
docker --version
docker compose version

# Gradle (or use the wrapper: ./gradlew)
gradle --version
```

Individual exercises may need additional tools — each README lists them.

---

## Sequence

Work through the exercises in order. Each one builds on the previous:

```
01 Kafka        → understand the async backbone before everything else
02 gRPC         → understand how runners talk to the cloud
03 PostgreSQL   → understand the data layer and tenant isolation
04 Redis        → understand the ephemeral state and rate limiting layer
05 Vault        → understand secrets and dynamic credentials
06 Outbox       → combine 01 + 03: reliable Kafka publish from PostgreSQL
07 Saga         → combine 01 + 06: multi-step coordination with compensation
08 DuckDB       → understand how runners process data without a DB
09 Spring AI    → understand the agent intelligence layer
10 Kubernetes   → understand how the platform scales in production
11 OpenTelemetry→ understand how to observe a distributed system
12 GraphQL      → understand the UI query API
```

After completing all 12, you have hands-on experience with every component in Pravah's architecture. Implementation begins at that point.
