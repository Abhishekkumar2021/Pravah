# Pravah Playground

> Hands-on exercises to solidify understanding of every tool and concept used in Pravah — before touching the real backend.
>
> Each exercise is a **self-contained mini-project**: its own Docker Compose, its own Spring Boot application, and a clear set of things to prove.

---

## Philosophy

Reading theory is not the same as building it. These exercises exist to answer the question: *"I know how this works on paper — now can I make it work on my machine?"*

Each exercise simulates a real scenario from Pravah — not a toy example. When you finish the playground, every line of code in the real backend will feel familiar.

---

## Exercise Index

| # | Exercise | Tools | Pravah ADRs | Status |
|---|----------|-------|-------------|--------|
| [01](01-kafka/) | **Kafka** — Producer, Consumer, Exactly-Once, DLQ | Kafka, Spring Kafka, Testcontainers | ADR-002, ADR-006 | ✅ Complete |
| [02](02-grpc/) | **gRPC** — Bidirectional Streaming + Reflection | gRPC, Protobuf, Netty | ADR-005, ADR-008 | ✅ Complete |
| [03](03-postgres-advanced/) | **PostgreSQL** — Partitioning, RLS, PgBouncer | PostgreSQL 16, PgBouncer, Flyway | ADR-003, ADR-013, ADR-022 | ✅ Complete |
| [04](04-redis/) | **Redis** — Distributed Lock + Token Bucket Rate Limiter | Redis 7, Lua Scripts, Spring Data Redis | ADR-012 | ✅ Complete |
| [05](05-vault/) | **Vault** — Dynamic DB Credentials + PKI | HashiCorp Vault, Spring Vault | ADR-007, ADR-008 | ✅ Complete |
| [06](06-outbox-pattern/) | **Outbox Pattern** — Atomic publish with crash recovery | PostgreSQL, Kafka, JPA | ADR-004 | ✅ Complete |
| [07](07-saga/) | **Saga** — Choreography with compensation | Kafka, Event-Driven, Idempotency | ADR-011 | ✅ Complete |
| [08](08-duckdb/) | **DuckDB** — In-process SQL transforms | DuckDB, JDBC, Parquet | ADR-023 | ✅ Complete |
| [09](09-spring-ai/) | **Spring AI** — ReAct agent with tools | Spring AI, Gemini API | ADR-020 | ✅ Complete |
| [10](10-kubernetes/) | **Kubernetes** — KEDA autoscaling on Kafka lag | Kind, KEDA, Apache Kafka | ADR-010, ADR-015 | ✅ Complete |
| [11](11-opentelemetry/) | **OpenTelemetry** — Distributed tracing | OTel, Jaeger, OTLP | ADR-014 | ✅ Complete |
| [12](12-graphql/) | **GraphQL** — DataLoader batching + pagination | Spring GraphQL, N+1 Prevention | ADR-033 | ✅ Complete |

**All 12 exercises complete!**

---

## How Each Exercise Is Structured

```
playground/NN-name/
├── README.md           ← What to build, what to prove, step-by-step guide
├── docker-compose.yml  ← Infrastructure (Kafka, PostgreSQL, Redis, etc.)
├── build.gradle.kts    ← Gradle build for the exercise
├── settings.gradle.kts
└── src/
    ├── main/java/      ← Implementation code
    ├── main/resources/ ← Configuration, SQL, Lua scripts
    └── test/java/      ← Integration tests that prove correctness
```

**Each README tells you what to build. The code shows how. The tests prove it works.**

---

## Prerequisites

All exercises require:

```bash
# Java 21
java -version  # should show 21

# Docker + Docker Compose
docker --version
docker compose version

# Gradle (or use the wrapper: ./gradlew)
gradle --version
```

Individual exercises may need additional tools — each README lists them.

---

## Learning Sequence

Work through the exercises in order. Each one builds on the previous:

```
01 Kafka        → The async backbone — understand before everything else
02 gRPC         → How runners talk to the cloud
03 PostgreSQL   → The data layer and tenant isolation (RLS)
04 Redis        → Ephemeral state, distributed locks, rate limiting
05 Vault        → Secrets and dynamic credentials
06 Outbox       → Combine Kafka + PostgreSQL: reliable event publishing
07 Saga         → Combine Kafka + Outbox: multi-step coordination
08 DuckDB       → How runners process data without a central DB
09 Spring AI    → The agent intelligence layer
10 Kubernetes   → How the platform scales in production
11 OpenTelemetry→ How to observe a distributed system
12 GraphQL      → The UI query API with N+1 prevention
```

---

## Running an Exercise

```bash
# Navigate to an exercise
cd playground/01-kafka

# Start the infrastructure
docker compose up -d

# Run the tests
./gradlew test

# Stop the infrastructure
docker compose down
```

---

## Key Concepts Covered

| Concept | Exercise |
|---------|----------|
| Exactly-once semantics | 01-kafka, 06-outbox |
| Consumer groups & rebalancing | 01-kafka |
| Bidirectional streaming | 02-grpc |
| Row-Level Security (RLS) | 03-postgresql |
| Connection pooling | 03-postgresql |
| Distributed locking | 04-redis |
| Rate limiting (token bucket) | 04-redis |
| Dynamic secrets | 05-vault |
| PKI certificate issuance | 05-vault |
| Transactional outbox | 06-outbox |
| Saga choreography | 07-saga |
| Compensating transactions | 07-saga |
| Columnar analytics | 08-duckdb |
| ReAct pattern | 09-spring-ai |
| KEDA auto-scaling | 10-kubernetes |
| Distributed tracing | 11-opentelemetry |
| DataLoader batching | 12-graphql |
| Cursor pagination | 12-graphql |

---

## What's Next

After completing all 12 exercises, you have hands-on experience with every component in Pravah's architecture.

**Next step**: Begin building features in the `backend/` directory (services, runner, shared libraries).

---

## Related Documentation

- [Theory Curriculum](../docs/theory/README.md) — Deep-dive into each concept
- [Architecture Decision Records](../docs/adr/README.md) — Why we chose these technologies
- [High-Level Architecture](../docs/architecture/high-level-architecture.md) — How it all fits together
- [Low-Level Design](../docs/lld/README.md) — Patterns, state machines, sequences
