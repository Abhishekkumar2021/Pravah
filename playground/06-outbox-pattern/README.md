# Playground 06 — Outbox Pattern

**ADR**: [ADR-004 Outbox Pattern](../../docs/adr/ADR-004-outbox-pattern-event-publishing.md)  
**Concepts**: Dual-write problem, transactional outbox, Kafka at-least-once delivery, `FOR UPDATE SKIP LOCKED`

---

## Why Outbox?

In microservices you frequently need to:

1. Update a database row (state change)
2. Publish an event to Kafka (notify others)

Doing these as two separate operations is **not atomic**. If step 2 fails after step 1 succeeds, your event is lost and the system is inconsistent.

The **Outbox Pattern** solves this: write the event **into a database table** (`outbox_events`) in the same transaction as the business data. A background publisher then polls the table and pushes events to Kafka. If the publisher crashes mid-send, the row stays and gets retried — no data loss.

---

## Quick Start

```bash
cd /Users/abhishek/Dev/Pravah/playground/06-outbox-pattern

# Start Postgres + Kafka + Kafdrop
docker compose up -d

# Run the tests (Testcontainers — needs Docker)
./gradlew test
```

Open Kafdrop at <http://localhost:9000> to watch events appear on `orders.events`.

---

## What's in Here

| File | Purpose |
|------|---------|
| `V1__outbox_table.sql` | Flyway migration: `outbox_events` + demo `orders` table |
| `OutboxEvent.java` | JPA entity mapping the outbox row |
| `OutboxEventRepository.java` | `findUnpublishedBatch` with `FOR UPDATE SKIP LOCKED` |
| `OutboxPublisher.java` | `@Scheduled` component that polls and publishes |
| `Order.java` / `OrderService.java` | Demo business entity; `createOrder` writes both in one `@Transactional` |
| `OutboxPatternIT.java` | Integration test: create order → consumer receives event |

---

## Tasks

### 1. Trace the transaction boundary

Open `OrderService.java`. Notice `@Transactional`. Add logging before and after `orders.save(...)` and `outbox.save(...)`. Restart and create an order (or run the test). Confirm both INSERTs happen within the same transaction ID (check Postgres logs or add `spring.jpa.properties.hibernate.generate_statistics=true`).

### 2. Simulate Kafka failure

In `OutboxPublisher.publishPendingEvents()`, temporarily throw an exception instead of calling `kafka.send(...)`. Run the test. Verify:

- The order row still exists (business state committed)
- The outbox row's `retry_count` increments each poll cycle
- After 10 retries the row stops being picked up (`retryCount < maxRetries` filter)

Revert and re-run to confirm normal publish resumes.

### 3. Multiple publisher instances (SKIP LOCKED)

The repository uses `PESSIMISTIC_WRITE` + `SKIP LOCKED`. This prevents two JVM instances from picking the same row. You can test this by:

1. Starting two Spring Boot instances on different ports
2. Creating several orders quickly
3. Watching the logs — each event is published by only one instance

### 4. Idempotent consumer

Outbox guarantees **at-least-once** delivery (the publisher may publish, crash before marking `published_at`, then republish on restart). Write a Kafka consumer that deduplicates by `orderId` (store seen IDs in a `Set` or DB).

### 5. Observability: outbox queue depth

Create a Spring `@Scheduled` gauge that exposes `outbox_unpublished_count` via Micrometer (Actuator). Alert if > N for > 5 min — this means Kafka or the publisher is unhealthy.

### 6. Pruning old events

Add a second scheduled task that deletes or archives rows where `published_at IS NOT NULL AND published_at < NOW() - INTERVAL '24 hours'`. Without this, the table grows forever.

---

## Debezium alternative

The polling publisher adds up to `poll-interval-ms` latency. For sub-10ms event delivery, you can use **Debezium** to tail PostgreSQL's WAL and stream outbox rows directly to Kafka. ADR-004 mentions this as a future upgrade path.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| "Connection refused" on 5432 | Is Postgres running? `docker compose ps` |
| Kafka send hangs forever | Kafka may not be healthy; check `docker logs ...kafka...` |
| Events not appearing in Kafdrop | Topic auto-create may be slow; refresh after a few seconds |
| Test skipped / Docker not available | Ensure Docker Desktop is running; Testcontainers needs it |

---

## Further Reading

- [Microservices.io — Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Outbox Event Router](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
- ADR-004 in this repo for full rationale
