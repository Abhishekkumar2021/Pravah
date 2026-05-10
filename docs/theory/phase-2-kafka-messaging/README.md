# Phase 2 — Messaging & Kafka Internals

> **Status:** ✅ Complete — 12 chapters

Kafka is Pravah's nervous system — every job lifecycle event, runner heartbeat, lineage record, and CDC change flows through it. This phase goes from "why Kafka" all the way down to the HTTP/2 frame level of gRPC.

By the end of Phase 2 you will understand:
- How Kafka durably stores and distributes millions of events per second
- Every Pravah topic's partition strategy, retention policy, and consumer design
- How gRPC bidirectional streaming powers Pravah's runner protocol

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [2.1](2.1-why-kafka.md) | **Why Kafka Over RabbitMQ / SQS** | The 5 Pravah requirements that make Kafka the only choice; RabbitMQ and SQS trade-offs |
| [2.2](2.2-kafka-architecture.md) | **Kafka Architecture** | Brokers, topics, partitions, offsets, ISR, leader election, log segments on disk |
| [2.3](2.3-producer-internals.md) | **Producer Internals** | `RecordAccumulator`, batching, linger.ms, compression, `acks`, idempotent producers |
| [2.4](2.4-consumer-groups-rebalancing.md) | **Consumer Groups & Rebalancing** | Partition assignment strategies; cooperative rebalancing; offset commit patterns |
| [2.5](2.5-partition-strategy.md) | **Partition Strategy** | Key selection per topic; hot partition problem; custom partitioners; Pravah's full topic layout |
| [2.6](2.6-exactly-once-kafka-transactions.md) | **Exactly-Once: Kafka Transactions** | Idempotent producer PID; transaction coordinator; `consume-transform-produce` atomic loop |
| [2.7](2.7-schema-registry-avro-protobuf.md) | **Schema Registry** | Avro vs Protobuf; compatibility modes (BACKWARD, FORWARD, FULL); safe schema evolution |
| [2.8](2.8-dead-letter-queues-poison-pill.md) | **Dead Letter Queues** | Poison pill identification; DLQ routing after N failures; operator replay UI; alerting |
| [2.9](2.9-kafka-connect-cdc-debezium.md) | **Kafka Connect & CDC with Debezium** | WAL-based change capture; connector lifecycle; offset management; lag monitoring |
| [2.10](2.10-kafka-streams-vs-flink.md) | **Kafka Streams vs Flink** | Event-time processing; windowing; watermarks; when Kafka Streams is enough vs Flink |
| [2.11](2.11-grpc-internals.md) | **gRPC Internals** | HTTP/2 multiplexing; bidirectional stream frames; flow control; Netty event loop; mTLS |
| [2.12](2.12-grpc-vs-rest-vs-websocket.md) | **gRPC vs REST vs WebSocket** | Protocol selection criteria; exactly which Pravah interface uses which protocol and why |

---

## Pravah's Kafka Topics at a Glance

```
Topic                  Partitions  Retention   Partition Key         Consumer Groups
─────────────────────────────────────────────────────────────────────────────────────
job.assigned           24          7 days      runner_id             execution-service
job.result             24          30 days     pipeline_id           scheduler, agent
job.status.update      12          7 days      job_id                ui-gateway (WS)
runner.heartbeat       12          1 day       runner_id             execution-service
pipeline.events        12          30 days     pipeline_id           lineage, notif, agent
audit.log              6           365 days    tenant_id             compliance-service
schema.changes         6           30 days     source_id             metadata-service
dlq.*                  3           30 days     original_key          operator-ui
```

---

## Key Themes

### Kafka Is a Log, Not a Queue

The fundamental difference from RabbitMQ / SQS:

```
Queue (RabbitMQ / SQS)           Log (Kafka)
──────────────────────           ───────────
Message consumed → deleted       Message retained for N days
One consumer per message         Unlimited independent consumers
No replay                        Full replay from any offset
Push-based (broker delivers)     Pull-based (consumer controls)
```

This distinction enables Pravah's most important capability: **replay**. When the agent service is updated, it can reprocess 30 days of `job.result` events to rebuild its knowledge base. When a new analytics service is added, it can consume the full historical `audit.log`.

### The Exactly-Once Guarantee Stack

```
Producer side:
  enable.idempotence=true  → deduplication by PID + sequence number
  transactional.id=...     → atomic multi-topic writes

Consumer side:
  isolation.level=read_committed  → only reads committed transactions
  manual offset commit            → commit after successful processing

Result: consume-transform-produce with no duplicates or data loss
```

### Protocol Selection in Pravah

```
Interface                    Protocol      Reason
─────────────────────────────────────────────────────────
Runner ↔ Cloud               gRPC bidi     Long-lived stream, bidirectional, binary
Service ↔ Service (sync)     gRPC unary    Low latency, typed contracts, HTTP/2
External API (UI/SDK)        REST          Universal compatibility, stateless
UI real-time updates         WebSocket     Browser-native, push model
Kafka consumer → DB          Internal      Not a network protocol
```

---

## Navigation

← [Phase 1 — Distributed Systems Fundamentals](../phase-1-distributed-systems/README.md)  
→ [Phase 3 — Database Design & Scaling](../phase-3-database-design/README.md)
