# ADR-004: Outbox Pattern for Reliable Event Publishing

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

In Pravah, almost every database write must be accompanied by a Kafka event. When a job's status changes from `PENDING` to `RUNNING`, the Execution Service must:

1. Update the job row in PostgreSQL
2. Publish a `job.status.updated` event to Kafka

The naive implementation does these two things sequentially:

```java
// WRONG — dual write problem
jobRepository.updateStatus(jobId, RUNNING);   // succeeds
kafkaTemplate.send("job.status.updated", ...); // may fail
```

This creates a **dual write problem**. If the Kafka publish fails (network blip, broker restart, producer misconfiguration), the database reflects `RUNNING` but no event was published. Downstream consumers — the Runner Service waiting for confirmation, the audit log, the real-time dashboard — never learn that the job started. The system is now in an inconsistent state that is invisible until someone notices the downstream has not progressed.

The reverse failure is equally bad: if Kafka acknowledges the publish but the subsequent database commit fails, an event exists for a state transition that never happened.

There is no distributed transaction that spans PostgreSQL and Kafka. Two-phase commit could theoretically solve this, but it requires Kafka's transaction coordinator and PostgreSQL to participate in the same XA transaction — a configuration that is fragile, slow, and generally avoided in production (see Phase 1 theory, Two-Phase Commit).

The problem requires a different approach: make both the state change and the intent to publish atomic within a single database transaction.

---

## Decision

The Outbox Pattern is the standard mechanism for all event publishing in Pravah services.

**Mechanism:**

Every service that needs to publish Kafka events has an `outbox_events` table in its own database:

```sql
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(255) NOT NULL,       -- e.g., job_id
    event_type   VARCHAR(255) NOT NULL,       -- e.g., 'job.status.updated'
    payload      JSONB         NOT NULL,
    topic        VARCHAR(255) NOT NULL,       -- Kafka topic name
    partition_key VARCHAR(255),               -- used for Kafka partitioning
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,                 -- NULL = not yet published
    retry_count  INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
    WHERE published_at IS NULL;
```

When the Execution Service updates a job's status, it does so in a single transaction:

```java
@Transactional
public void transitionJobToRunning(String jobId) {
    // 1. Update primary state
    Job job = jobRepository.findById(jobId).orElseThrow();
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(Instant.now());
    jobRepository.save(job);

    // 2. Write the event to the outbox — same transaction
    OutboxEvent event = OutboxEvent.builder()
        .aggregateId(jobId)
        .eventType("job.status.updated")
        .topic("pravah.job.status.updated")
        .partitionKey(jobId)
        .payload(buildPayload(job))
        .build();
    outboxRepository.save(event);

    // If the transaction commits: both the job row and the outbox row exist.
    // If the transaction rolls back: neither exists. No dual write.
}
```

A dedicated **Outbox Publisher** component polls the `outbox_events` table for unpublished rows and publishes them to Kafka:

```
┌─────────────────────────────────────────────────────────┐
│                   Execution Service                      │
│                                                         │
│  ┌────────────────────┐         ┌────────────────────┐  │
│  │  Business Logic     │         │  Outbox Publisher  │  │
│  │                    │         │                    │  │
│  │  UPDATE jobs       │         │  SELECT * FROM     │  │
│  │  INSERT outbox ────┤         │  outbox_events     │  │
│  │  (same @Tx)        │         │  WHERE published   │  │
│  └────────────────────┘         │  IS NULL           │  │
│                                 │  ORDER BY created  │  │
│       execution_db              │  LIMIT 100         │  │
│  ┌──────────────────┐           │        │           │  │
│  │  jobs            │           │        ▼           │  │
│  │  outbox_events ◄─┤           │  kafkaTemplate     │  │
│  └──────────────────┘           │  .send(...)        │  │
│                                 │        │           │  │
│                                 │  UPDATE published  │  │
│                                 │  _at = NOW()       │  │
│                                 └────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                                           │
                                           ▼
                                      Kafka Cluster
```

**Publisher implementation details:**

- Runs as a `@Scheduled` Spring component within the service (every 100ms by default)
- Batch size: 100 events per poll to amortize the SELECT overhead
- On Kafka publish success: updates `published_at = NOW()`
- On Kafka publish failure: increments `retry_count`; exponential backoff based on retry count; after 10 retries, routes to a dead-letter admin queue for human review
- For multi-instance deployments: uses a `SELECT ... FOR UPDATE SKIP LOCKED` to avoid multiple publisher instances picking up the same events

```sql
SELECT * FROM outbox_events
WHERE published_at IS NULL
  AND retry_count < 10
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

**Debezium alternative (CDC-based outbox):**

For very high event volumes, the polling approach adds latency (up to 100ms + PostgreSQL query overhead). An alternative is to use Debezium to tail PostgreSQL's WAL and publish outbox rows to Kafka as they appear. This reduces latency to near-zero. Pravah's initial implementation uses the polling approach because it is simpler to operate; the Debezium path is available as an upgrade if latency requirements tighten.

---

## Consequences

### Positive

- **Atomicity guaranteed**: the business state change and the event publication intent are in the same ACID transaction. It is impossible for the job to be `RUNNING` without the event being recorded, or for the event to exist without the job update committing.
- **Kafka failure does not block the business operation**: if Kafka is unavailable, the transaction still commits. Events accumulate in the outbox table and are published once Kafka recovers. The system degrades gracefully rather than failing hard.
- **At-least-once delivery with idempotent consumers**: the outbox publisher may publish the same event twice (e.g., if the service crashes after Kafka acknowledges but before the `published_at` update commits). Downstream consumers must be idempotent — they must handle duplicate events correctly. This is a deliberate design choice, not a bug.
- **Full event history**: the outbox table serves as an audit trail for all events that were supposed to be published. Missed events are immediately detectable by querying `WHERE published_at IS NULL AND created_at < NOW() - INTERVAL '5 minutes'`.
- **Operational visibility**: the outbox queue depth is a real metric (`unpublished event count`) that is monitored and alerted on.

### Negative

- **Increased write load on PostgreSQL**: every event write is an additional INSERT + UPDATE in the database. At high event rates, the outbox table becomes a hotspot.
- **Polling latency**: with a 100ms poll interval, events have up to 100ms of additional latency before reaching Kafka. For Pravah's use cases (job scheduling, status tracking), this is acceptable. For use cases requiring sub-10ms event delivery, the WAL-based CDC approach would be necessary.
- **Outbox table requires maintenance**: the table grows indefinitely if published events are not pruned. A background job must archive or delete rows where `published_at IS NOT NULL AND published_at < NOW() - INTERVAL '24 hours'`.
- **Added complexity**: each service must manage an outbox table, a publisher component, and the pruning job. This is boilerplate that must be replicated across services.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Outbox publisher dies silently | Publisher health is exposed via Spring Actuator and monitored; last publish timestamp is a gauge metric |
| `FOR UPDATE SKIP LOCKED` causes lock contention | Batch size limits keep lock duration short; publisher runs frequently in small batches rather than rarely in large ones |
| Outbox table grows unboundedly | Partitioned by `created_at` (monthly); published partitions are dropped by a scheduled job |
| High event volume overwhelms the polling approach | Debezium CDC upgrade path; Debezium reads WAL directly and has minimal impact on PostgreSQL write path |

---

## Alternatives Considered

### Dual Write with Retry

Publish to Kafka first. If that fails, store the event in a retry queue and retry later. If the database write fails, issue a compensating Kafka event (a tombstone or cancellation).

Rejected because:
- The retry logic itself can fail. There is no durable storage for the retry queue unless it is another database — at which point you have the dual-write problem again with a different database.
- Compensating events for a failed write are semantically complex. If the job status never changed, publishing a "job status updated" followed by a "cancel previous event" is fragile and error-prone.

### Kafka Transactions (Exactly-Once)

Kafka transactions allow a producer to atomically publish to multiple topics. They do not solve the problem of coordinating a Kafka publish with a PostgreSQL commit — these are two separate systems.

Kafka transactions are used within Pravah for Kafka-to-Kafka operations (consume from one topic, process, publish to another) to achieve exactly-once semantics within the Kafka ecosystem. They do not replace the outbox pattern for database-to-Kafka coordination.

### Event Sourcing

Store all state as an event log (Event Sourcing) rather than as a mutable row. Publishing an event and persisting state become the same operation.

Considered but deferred. Event sourcing significantly changes the data model and query patterns. Pravah's data is primarily relational and query-heavy (find all jobs matching these criteria). Event sourcing would add complexity that is not justified by the current requirements. If Pravah evolves toward a strong audit and time-travel query requirement, Event Sourcing is the right direction (see Phase 1 theory, Event Sourcing & CQRS).
