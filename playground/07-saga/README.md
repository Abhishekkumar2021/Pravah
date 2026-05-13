# Playground 07 — Saga (Choreography)

**ADR**: [ADR-011 Saga Pattern](../../docs/adr/ADR-011-saga-choreography.md)  
**Concepts**: Distributed transactions, choreography vs orchestration, compensating actions, idempotent event handlers

---

## Why Saga?

In microservices, some operations span multiple services:

1. **Order Service** creates an order
2. **Inventory Service** reserves stock
3. **Payment Service** charges the customer

If payment fails after inventory is reserved, you need to **compensate** — release the reserved stock. There's no distributed ACID transaction. Each service commits locally; rollback means publishing a compensating event.

**Choreography** = no central coordinator. Each service reacts to events and publishes new events. The saga "emerges" from the event flow.

---

## Quick Start

```bash
cd /Users/abhishek/Dev/Pravah/playground/07-saga

# Run tests (EmbeddedKafka — no Docker needed)
./gradlew test

# Or run with real Kafka (for Kafdrop visualization)
docker compose up -d
./gradlew bootRun
```

---

## Saga Flow

```
┌─────────────┐     OrderCreatedEvent      ┌──────────────────┐
│ OrderService│ ─────────────────────────▶ │ InventoryService │
└─────────────┘                            └──────────────────┘
       │                                           │
       │ ◀── InventoryReservedEvent ───────────────┘
       │                                           │
       │     InventoryReservedEvent                ▼
       │ ────────────────────────────────▶ ┌───────────────┐
       │                                   │PaymentService │
       │                                   └───────────────┘
       │                                           │
       │ ◀── PaymentProcessedEvent ────────────────┘
       │
       ▼
 OrderCompletedEvent
```

**Compensation flow (payment fails):**

```
PaymentService ── PaymentFailedEvent ──▶ OrderService (marks CANCELLED)
                                              │
                                              ▼
                                       OrderCancelledEvent
                                              │
                              InventoryService ◀─ (releases stock)
```

---

## What's in Here

| File | Purpose |
|------|---------|
| `event/*.java` | Sealed interface + records for all saga events |
| `SagaTopics.java` | Topic names (single source of truth) |
| `OrderService.java` | Saga initiator + state aggregator |
| `InventoryService.java` | Reserves/releases stock, listens for compensation |
| `PaymentService.java` | Processes payment (fails if amount > 400) |
| `SagaChoreographyIT.java` | Tests: happy path, inventory fail, payment fail + compensation |

---

## Tasks

### 1. Trace the event flow

Add `log.info()` statements (or breakpoints) in each `@KafkaListener`. Run the happy-path test. Observe the sequence:

```
OrderCreatedEvent → InventoryReservedEvent → PaymentProcessedEvent → OrderCompletedEvent
```

### 2. Trigger compensation

Run `paymentFails_orderCancelledAndInventoryReleased`. Watch:

1. Inventory reserved (stock decremented)
2. Payment fails (amount > threshold)
3. `PaymentFailedEvent` → `OrderCancelledEvent`
4. InventoryService releases stock (compensating action)

### 3. Idempotency guards

Each handler checks if it already processed the event (e.g., `reservations.contains(orderId)`). Remove one guard and run the test — observe duplicate processing. Re-add the guard.

### 4. Add a new participant

Create a `NotificationService` that listens for `OrderCompletedEvent` and `OrderCancelledEvent` and logs a "notification sent" message. No changes to existing services — that's the beauty of choreography.

### 5. Simulate network partition

In `InventoryService.onOrderCreated()`, add a 50% random failure:

```java
if (Math.random() < 0.5) throw new RuntimeException("Simulated failure");
```

Observe Kafka's retry behavior. Then implement proper retry + DLT handling (see 01-kafka playground).

### 6. Visualize in Kafdrop

Run with Docker Compose, create an order via a test or REST endpoint, then open <http://localhost:9000>. Browse the saga topics and see the event payloads.

---

## Choreography vs Orchestration

| Choreography | Orchestration |
|--------------|---------------|
| No central coordinator | Central "saga orchestrator" service |
| Services react to events | Orchestrator calls services in sequence |
| Harder to see full flow | Single place to see saga steps |
| Loose coupling | Orchestrator couples to all participants |

Pravah uses choreography (ADR-011) because it aligns with the event-driven Kafka backbone.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Test hangs forever | Check for missing `@KafkaListener` or wrong topic name |
| Duplicate events processed | Add idempotency check (Set of processed orderIds) |
| Compensation not triggered | Ensure `OrderCancelledEvent` is published and InventoryService subscribes |

---

## Further Reading

- [Microservices.io — Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Chris Richardson — Saga choreography vs orchestration](https://chrisrichardson.net/post/sagas/2019/08/15/developing-sagas-part-3.html)
- ADR-011 in this repo
