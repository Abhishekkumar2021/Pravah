# ADR-002: Kafka as the Primary Messaging Backbone

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's microservices need to communicate asynchronously. The core event flows are:

1. **Pipeline triggered → job created**: the Scheduler publishes a trigger event; the Execution Service creates jobs
2. **Job assigned → runner picks up work**: the Execution Service assigns a job; the Runner Service notifies the runner
3. **Job completed → downstream triggered**: a completed job may unblock dependent jobs or trigger webhooks
4. **Audit events**: every state transition must be recorded for compliance and debugging

The messaging system must satisfy several requirements:

- **Durability**: events must survive the failure of any single node, including the broker. A lost trigger event means a pipeline silently does not run — this is unacceptable.
- **Ordered delivery within a pipeline**: all events for a given pipeline execution must be processed in order. Job A must complete before Job B starts, even if the consumer restarts mid-processing.
- **Replay**: when a bug causes incorrect processing, we need to replay events from a point in time without requiring the producers to resend anything.
- **High throughput**: at scale, Pravah processes hundreds of thousands of job events per hour. The messaging layer must not be the bottleneck.
- **Consumer group semantics**: multiple instances of the Execution Service must share the work of processing job events without duplicating processing.
- **Schema evolution**: event schemas will change over time as Pravah evolves. Old consumers must be able to read new messages, and new consumers must be able to read old messages.

---

## Decision

Apache Kafka is the primary messaging backbone for all async service-to-service communication in Pravah.

**Topic design:**

```
pravah.pipeline.triggered          Partition by: pipeline_id
pravah.job.created                 Partition by: pipeline_execution_id
pravah.job.assigned                Partition by: job_id
pravah.job.status.updated          Partition by: job_id
pravah.job.completed               Partition by: pipeline_execution_id
pravah.runner.heartbeat            Partition by: runner_id
pravah.audit.events                Partition by: tenant_id
```

**Configuration decisions:**

| Setting | Value | Reason |
|---------|-------|--------|
| Replication factor | 3 | Tolerate 1 broker failure without data loss |
| `min.insync.replicas` | 2 | Writes require 2 in-sync replicas before acknowledging |
| Producer `acks` | `all` | Leader waits for all in-sync replicas |
| Consumer `enable.auto.commit` | `false` | Manual offset commits after processing completes |
| Retention | 7 days | Allows replay for incident recovery |
| Partition count | 12 (default) | Allows up to 12 parallel consumers per group |

**Schema management:** all events use Avro schemas registered in Confluent Schema Registry with backward compatibility enforced (see ADR discussion for Schema Registry). This means new optional fields can be added without breaking existing consumers.

---

## Consequences

### Positive

- **Durability by design**: with `acks=all` and `min.insync.replicas=2`, a message is only acknowledged after it is written to at least 2 brokers. No message is silently lost.
- **Ordered processing**: partitioning by `pipeline_execution_id` ensures all events for a single pipeline run land on the same partition, processed by the same consumer instance, in order.
- **Replay capability**: Kafka's immutable log with configurable retention allows any consumer to re-read any window of events. This is invaluable for debugging and for backfilling new consumers (e.g., a new audit service can replay the past 7 days).
- **Consumer isolation**: each service has its own consumer group. The Audit Service consuming `job.completed` does not affect the Execution Service consuming the same topic — each group maintains its own offset.
- **Backpressure handling**: if the Execution Service is slow, events accumulate in the Kafka partition. The producer (Scheduler) is not affected. Consumer lag becomes a visible metric that drives KEDA scaling (ADR-015).
- **Decoupling**: the Scheduler does not know or care that the Execution Service exists. New consumers (e.g., a real-time dashboard feed) can subscribe to existing topics without any producer changes.

### Negative

- **Operational burden**: Kafka requires ZooKeeper (or KRaft in newer versions), careful broker sizing, and ongoing tuning. It is not a lightweight dependency.
- **Not a queue**: Kafka does not natively support message acknowledgment-based retry like RabbitMQ. Failed messages must be handled explicitly — either retry in code, or route to a dead-letter topic (see DLQ design in Phase 2 theory).
- **Consumer rebalancing latency**: when a consumer instance dies or is scaled, a group rebalance occurs. During rebalancing, no consumer in the group processes messages. Rebalance duration depends on partition assignment strategy and consumer session timeout.
- **At-least-once by default**: without Kafka transactions (exactly-once), consumers must be idempotent. Every handler must be designed to handle duplicate delivery correctly.
- **Latency floor**: Kafka is optimized for throughput, not latency. The `linger.ms` and batching configuration create a small latency floor (5–10ms) that would not exist with a direct RPC call.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Consumer lag grows unbounded | KEDA scales consumers based on lag (ADR-015); lag alert pages on-call at >10k messages |
| Poison pill message crashes consumer in loop | Dead-letter topic after 3 retries with exponential backoff; manual intervention queue |
| Schema incompatibility breaks deserialization | Schema Registry with backward compatibility enforced; CI validates schema changes before merge |
| Kafka cluster unavailability | Outbox pattern (ADR-004) ensures messages are durably stored in PostgreSQL even if Kafka is down during publish |

---

## Alternatives Considered

### RabbitMQ

RabbitMQ is simpler to operate and provides native message acknowledgment, dead-lettering, and priority queues. It was considered for Pravah's v1 because of its lower operational overhead.

Rejected because:
- RabbitMQ does not retain messages after they are consumed. Replay is impossible without re-sending from the producer.
- At Pravah's target scale, RabbitMQ's throughput is significantly lower than Kafka's.
- RabbitMQ's consumer model is push-based. It lacks native consumer group semantics for parallel processing with ordered delivery.

### AWS SQS / GCP Pub/Sub

Managed services that eliminate the operational burden of running Kafka.

Rejected because:
- Pravah must be deployable on-premise and in private clouds. A dependency on a specific cloud's managed messaging service violates the multi-cloud deployment requirement.
- SQS does not support message ordering within partitions (FIFO queues are limited and do not provide the same semantics as Kafka partitions).
- Managed services lock Pravah's architecture into a specific cloud provider.

### Direct gRPC calls between services

Making synchronous RPC calls instead of publishing to Kafka.

Rejected for async workflows because:
- A slow or unavailable downstream service would block the upstream service. The Scheduler would block waiting for the Execution Service to acknowledge a trigger.
- If the downstream service is temporarily down when an event occurs, the event is lost unless the caller implements retry logic with persistent storage — which is exactly what the Outbox pattern provides. At that point, Kafka is a cleaner solution.
- gRPC is used for the runner ↔ cloud channel (ADR-005) because that requires bidirectional streaming. Internal service-to-service async workflows use Kafka.
