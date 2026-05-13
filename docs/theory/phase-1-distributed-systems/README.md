# Phase 1 — Distributed Systems Fundamentals

> **Status:** ✅ Complete — 12 chapters

The moment you split a system across multiple machines, a new class of problems appears that simply doesn't exist on a single node. This phase builds the conceptual foundation that every other phase depends on.

By the end of Phase 1 you will understand:
- Why distributed systems behave unexpectedly under failure
- How Pravah makes consistency vs availability trade-offs per component
- The exact patterns (Saga, Outbox, Idempotency, Circuit Breaker) Pravah uses to stay correct at scale

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [1.1](1.1-cap-theorem-and-consistency.md) | **CAP Theorem & Pravah's Consistency Choices** | Why P is mandatory; CP vs AP decisions mapped to every Pravah component |
| [1.2](1.2-consistency-models.md) | **Consistency Models** | The spectrum from Linearizability → Sequential → Causal → Eventual; where Pravah uses each |
| [1.3](1.3-distributed-clocks-ordering-causality.md) | **Distributed Clocks, Ordering & Causality** | Why clocks lie; Lamport timestamps; vector clocks; happens-before |
| [1.4](1.4-leader-election.md) | **Leader Election** | Bully algorithm, ZooKeeper, Redis-based election; fencing tokens; split-brain prevention |
| [1.5](1.5-consensus-algorithms-raft-paxos.md) | **Consensus — Raft & Paxos** | How nodes agree under failure; Raft log replication; quorums; why etcd uses Raft |
| [1.6](1.6-failure-modes.md) | **Failure Modes** | Crash-stop, crash-recovery, Byzantine; network partitions; failure detection with adaptive timeouts |
| [1.7](1.7-idempotency-exactly-once.md) | **Idempotency & Exactly-Once Semantics** | Idempotency keys; deduplication tables; at-least-once with idempotent consumers |
| [1.8](1.8-saga-pattern.md) | **Saga Pattern** | Orchestration vs choreography; compensating transactions; Pravah's pipeline lifecycle saga |
| [1.9](1.9-event-sourcing-cqrs.md) | **Event Sourcing & CQRS** | Event log as source of truth; state reconstruction; read/write model separation; time travel |
| [1.10](1.10-outbox-pattern.md) | **The Outbox Pattern** | Atomic writes to DB + Kafka without distributed transactions; relay polling; Debezium CDC |
| [1.11](1.11-circuit-breaker-bulkhead-backpressure.md) | **Circuit Breaker, Bulkhead & Backpressure** | Resilience4j circuit states; bulkhead thread isolation; consumer lag and backpressure |
| [1.12](1.12-two-phase-commit.md) | **Two-Phase Commit & Why We Avoid It** | 2PC phases; blocking problem; partial commits; Pravah's alternative: Saga + Outbox + Idempotency |

---

## Key Themes

### Every Component Makes a CAP Choice

```
Pravah Component         → CP or AP?   Why
────────────────────────────────────────────────────────────
Pipeline definitions      CP           Wrong version = data corruption
Job run status (cache)    AP           Stale UI is fine; errors are not
Runner heartbeats         AP           30s staleness is acceptable
Secret store (Vault)      CP           Stale credentials = broken pipelines
Kafka event log           CP           Ordering & durability non-negotiable
Scheduler leader election CP           Two leaders = chaos (split-brain)
```

### Failure Is the Default

Distributed systems don't fail rarely — they fail constantly in small ways. Every design decision in Pravah assumes failure:

- **Idempotency** ensures retried jobs don't run twice
- **Outbox pattern** ensures DB writes and Kafka events stay in sync
- **Saga** ensures multi-step failures can be compensated
- **Circuit breaker** ensures one failing service doesn't cascade

### The Stack That Replaces 2PC

Two-Phase Commit is theoretically correct but operationally disastrous. Pravah uses a combination of patterns instead:

```
2PC replacement stack
─────────────────────
Saga             → multi-step distributed transaction management
Outbox Pattern   → atomic DB + event publishing
Idempotency      → safe retries at every layer
Event Sourcing   → crash-safe state reconstruction
Circuit Breaker  → failure isolation
Consensus (Raft) → leader agreement for critical coordination
```

---

## How to Read These Chapters

Each chapter follows the same structure:

1. **The Problem** — what breaks without this concept
2. **How It Works** — internals with ASCII architecture diagrams
3. **Pravah Context** — exactly where and how Pravah applies it
4. **Trade-offs** — what you give up; alternatives considered
5. **Interview Angles** — how Senior-level interviewers probe this topic
6. **Key Takeaways** — summary and link to next chapter

---

## Navigation

← [Curriculum Overview](../00-curriculum-overview.md)  
→ [Phase 2 — Messaging & Kafka Internals](../phase-2-kafka-messaging/README.md)
