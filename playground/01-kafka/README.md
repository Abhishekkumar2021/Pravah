# Exercise 01 — Kafka: Producer, Consumer, Exactly-Once, DLQ

**Proves:** [ADR-002 (Kafka as Messaging Backbone)](../../docs/adr/ADR-002-kafka-messaging-backbone.md) · [ADR-006 (Multi-Tenancy — tenant_id in every message)](../../docs/adr/ADR-006-pool-multi-tenancy-model.md)

**Time estimate:** 3–4 hours to complete all tasks.

---

## What You Will Build

A miniature version of Pravah's job lifecycle event flow:

```
JobEventProducer
    │ publishes pravah.job.created (Avro, Schema Registry)
    ▼
Kafka  ─── 3 partitions, keyed by tenant_id
    │
    ├── JobEventConsumer (consumer group: execution-service)
    │       Processes job events, simulates job state transitions
    │       Publishes pravah.job.completed
    │
    └── AuditConsumer (consumer group: audit-service)
            Reads the same topic independently
            Writes to an in-memory audit log

On processing failure:
    JobEventConsumer → publishes to pravah.job.created.DLT
    DLQHandler → logs the poison pill, records reason
```

By the end you will have personally verified:
- Kafka partition assignment by key (`tenant_id`)
- Two independent consumer groups reading the same topic
- Avro serialization with Schema Registry
- Exactly-once semantics using Kafka transactions
- Dead Letter Topic (DLT) for poison pill messages
- What happens when a consumer crashes mid-batch (offset commit behavior)

---

## Infrastructure

Start everything with:

```bash
cd playground/01-kafka
docker compose up -d
```

This starts:
- **Kafka** (KRaft mode, no ZooKeeper) on `localhost:9092`
- **Schema Registry** on `localhost:8081`
- **Kafdrop** (Kafka UI) on `http://localhost:9000`

Wait ~15 seconds for Kafka to be ready:

```bash
docker compose logs kafka | grep "Kafka Server started"
```

---

## Tasks

Work through these in order. Each task has a `// TODO` comment in the skeleton code marking exactly where to add your implementation.

### Task 1 — Publish a `JobCreated` event (Avro)

**File:** `src/main/java/io/pravah/playground/kafka/producer/JobEventProducer.java`

The Avro schema is already defined at `src/main/avro/job-created.avsc`. Your task:

1. Complete the `publish()` method to send a `JobCreated` event to `pravah.job.created`
2. Use `tenant_id` as the Kafka message key so events for the same tenant always go to the same partition
3. Run `KafkaPlaygroundApplication` and call the REST endpoint:
   ```bash
   curl -X POST http://localhost:8080/jobs \
     -H "Content-Type: application/json" \
     -d '{"tenantId":"acme","pipelineId":"orders-etl","stepId":"extract"}'
   ```
4. Open Kafdrop at `http://localhost:9000` and verify the message appears in `pravah.job.created`

**What to observe:** The message is stored in the partition that corresponds to `hash("acme") % 3`. Send messages for different tenants and notice they land in different partitions. All messages for `tenant_id=acme` always go to the same partition — this is the ordering guarantee.

---

### Task 2 — Consume events with two independent consumer groups

**File:** `src/main/java/io/pravah/playground/kafka/consumer/JobEventConsumer.java`
**File:** `src/main/java/io/pravah/playground/kafka/consumer/AuditConsumer.java`

1. Complete `JobEventConsumer` — consumer group `execution-service`
   - On each `JobCreated` event: print it, simulate processing (Thread.sleep 100ms), publish `pravah.job.completed`
2. Complete `AuditConsumer` — consumer group `audit-service`
   - On each `JobCreated` event: append to the in-memory audit log
3. Verify in Kafdrop that both consumer groups have their own independent offsets

**What to observe:** Both groups read every message. `execution-service` at offset 5 and `audit-service` at offset 3 — they progress independently. Neither blocks the other.

---

### Task 3 — Exactly-once with Kafka transactions

**File:** `src/main/java/io/pravah/playground/kafka/producer/JobEventProducer.java`

The `publishWithTransaction()` method must:
1. Begin a Kafka transaction
2. Publish `pravah.job.created`
3. Publish `pravah.job.audit.log` (a second topic)
4. Commit the transaction

Then simulate a failure: throw an exception between the two publishes. Verify that **neither** message appears in either topic (atomicity).

**What to observe:** Without transactions, a crash between publish #1 and publish #2 leaves partial state — event A arrived but its audit record did not. With transactions, it is all-or-nothing.

---

### Task 4 — Dead Letter Topic (DLT)

**File:** `src/main/java/io/pravah/playground/kafka/consumer/JobEventConsumer.java`
**File:** `src/main/java/io/pravah/playground/kafka/consumer/DeadLetterHandler.java`

1. In `JobEventConsumer`, add logic: if `stepId` equals `"poison"`, throw a `PoisonPillException`
2. Configure Spring Kafka's `DefaultErrorHandler` to send failed messages to `pravah.job.created.DLT` after 3 retries with exponential backoff (1s, 2s, 4s)
3. Complete `DeadLetterHandler` — consume from the DLT and log the original message, the exception class, and the retry count header
4. Test it:
   ```bash
   curl -X POST http://localhost:8080/jobs \
     -H "Content-Type: application/json" \
     -d '{"tenantId":"acme","pipelineId":"orders-etl","stepId":"poison"}'
   ```

**What to observe:** The consumer retries 3 times (watch the logs — 1s pause, 2s pause, 4s pause), then publishes to the DLT. The main topic offset advances past the bad message. The DLT contains the original message with failure metadata in headers.

---

### Task 5 — Consumer group rebalancing

Run the application normally, then in a second terminal start a second instance on port 8081:

```bash
SERVER_PORT=8081 ./gradlew bootRun
```

Watch the logs. You will see a rebalance: partitions redistribute between the two consumer instances. Send messages and verify each instance only processes messages from its assigned partitions.

Then kill the second instance (`Ctrl+C`). Watch the rebalance again — the first instance reclaims all partitions.

**What to observe:** This is exactly what happens when Pravah's Execution Service scales up (KEDA adds pods) or down. Kafka rebalances automatically; no message is lost or processed twice (because offsets are committed only after successful processing).

---

### Task 6 — Schema evolution (stretch goal)

1. Add a new field `priority` (int, default 5) to `job-created.avsc`
2. Register the new schema version with Schema Registry
3. Verify that a consumer running the **old schema** can still read messages produced with the **new schema** (backward compatibility)
4. Try registering an incompatible change (rename a field, remove a field without a default). Schema Registry should reject it.

**What to observe:** This is exactly how Pravah handles pipeline version upgrades — a new producer schema does not break old consumers as long as backward compatibility is maintained.

---

## Verification Checklist

Before moving to Exercise 02, you should be able to answer these questions from memory:

- [ ] Why does Pravah key all Kafka messages by `tenant_id`?
- [ ] What is the difference between a consumer group and a consumer instance?
- [ ] What exactly does `acks=all` guarantee that `acks=1` does not?
- [ ] What is the difference between at-least-once and exactly-once delivery?
- [ ] What happens to a DLT message — does it block the main topic?
- [ ] If you have 3 partitions and start 4 consumer instances in one group, what happens to the 4th instance?

If you can answer all six confidently, proceed to [Exercise 02 — gRPC](../02-grpc/).

---

## Teardown

```bash
docker compose down -v   # -v removes volumes (clears all Kafka data)
```
