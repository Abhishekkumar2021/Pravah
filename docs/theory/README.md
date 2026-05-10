# Pravah — Theory Curriculum

> A complete engineering curriculum covering every concept needed to build and reason about a production-grade distributed ETL platform.

Each chapter is a standalone reference document. You can read sequentially or jump to any topic. Every chapter connects concepts directly to Pravah's design, includes ASCII architecture diagrams, concrete code examples, and interview angles.

---

## Curriculum Map

```
Phase 1: Distributed Systems Fundamentals          ✅ 12/12 chapters
Phase 2: Messaging & Kafka Internals               ✅ 12/12 chapters
Phase 3: Database Design & Scaling                 🔄  4/12 chapters
Phase 4: Observability & Reliability               📋 planned
Phase 5: Security, Auth & Multi-Tenancy            📋 planned
Phase 6: Kubernetes & Production Infra             📋 planned
```

---

## Phase Index

| Phase | Focus | Chapters | Directory |
|-------|-------|----------|-----------|
| **1** | Distributed Systems — CAP, consensus, failure modes, saga, outbox | 12 ✅ | [phase-1-distributed-systems/](phase-1-distributed-systems/README.md) |
| **2** | Kafka internals, producers, consumers, CDC, gRPC | 12 ✅ | [phase-2-kafka-messaging/](phase-2-kafka-messaging/README.md) |
| **3** | PostgreSQL, indexing, partitioning, Redis, Elasticsearch | 12 🔄 | [phase-3-database-design/](phase-3-database-design/README.md) |
| **4** | SLOs, tracing, alerting, chaos engineering | — 📋 | planned |
| **5** | JWT, mTLS, Vault, RBAC, tenant isolation | — 📋 | planned |
| **6** | K8s, Helm, GitOps, autoscaling | — 📋 | planned |

---

## How Each Chapter Is Structured

```
1. The Problem       What breaks without this concept?
2. Internals         How does it work — with ASCII diagrams
3. Pravah Context    Exactly where/how Pravah uses this
4. Trade-offs        What you give up; alternatives
5. Interview Angles  How FAANG interviewers probe this
6. Key Takeaways     Summary + link to next chapter
```

---

## Quick Navigation

- [00-curriculum-overview.md](00-curriculum-overview.md) — full topic list with descriptions
- [Phase 1 →](phase-1-distributed-systems/README.md)
- [Phase 2 →](phase-2-kafka-messaging/README.md)
- [Phase 3 →](phase-3-database-design/README.md)
