package io.pravah.playground.kafka.producer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes job lifecycle events to Kafka.
 *
 * Key design decisions mirrored from Pravah:
 *   - Message KEY = tenant_id  → all events for the same tenant go to the
 *     same partition → ordered delivery per tenant, no cross-tenant ordering needed
 *   - event_id = UUID          → consumers use this for idempotency (skip duplicates)
 *   - Avro + Schema Registry   → schema enforced at publish time; consumers
 *     always know the shape of the message
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${pravah.kafka.topics.job-created}")
    private String jobCreatedTopic;

    @Value("${pravah.kafka.topics.job-audit}")
    private String jobAuditTopic;

    // ─────────────────────────────────────────────────────────────
    // Task 1 — Basic publish
    // ─────────────────────────────────────────────────────────────

    /**
     * Publish a single JobCreated event.
     *
     * TODO:
     *   1. Build a JobCreated Avro record using the builder:
     *        JobCreated event = JobCreated.newBuilder()
     *            .setEventId(UUID.randomUUID().toString())
     *            .setJobId(jobId)
     *            .setTenantId(tenantId)
     *            .setPipelineId(pipelineId)
     *            .setStepId(stepId)
     *            .setPriority(5)
     *            .setCreatedAt(System.currentTimeMillis())
     *            .build();
     *
     *   2. Send it using kafkaTemplate.send(topic, key, value)
     *        The KEY must be tenantId (not jobId, not a random UUID).
     *        WHY: Kafka guarantees ordering within a partition. By using tenantId
     *        as the key, all events for tenant "acme" go to the same partition
     *        and are processed in order. Different tenants can run in parallel
     *        across different partitions.
     *
     *   3. Add a callback to log success/failure:
     *        future.whenComplete((result, ex) -> {
     *            if (ex == null) {
     *                log.info("Published job.created: jobId={} partition={} offset={}",
     *                    jobId,
     *                    result.getRecordMetadata().partition(),
     *                    result.getRecordMetadata().offset());
     *            } else {
     *                log.error("Failed to publish job.created: jobId={}", jobId, ex);
     *            }
     *        });
     *
     *   4. Return the jobId so the REST controller can include it in the response.
     */
    public String publish(String tenantId, String pipelineId, String stepId) {
        String jobId = UUID.randomUUID().toString();

        // TODO: build the Avro record and send it

        log.info("Sending job.created: jobId={} tenantId={}", jobId, tenantId);
        return jobId;
    }

    // ─────────────────────────────────────────────────────────────
    // Task 3 — Exactly-once with Kafka transactions
    // ─────────────────────────────────────────────────────────────

    /**
     * Publish a JobCreated event AND a job.audit.log event atomically.
     * Either both arrive in Kafka or neither does.
     *
     * TODO:
     *   Use kafkaTemplate.executeInTransaction(ops -> {
     *       ops.send(jobCreatedTopic, tenantId, jobCreatedEvent);
     *       ops.send(jobAuditTopic, tenantId, auditEvent);    // same Avro type for now
     *       return jobId;
     *   });
     *
     *   Then simulate a crash to prove atomicity:
     *   Add a parameter `simulateCrash` (boolean). If true, throw a RuntimeException
     *   AFTER the first send but BEFORE the second send (inside the transaction lambda).
     *   Verify in Kafdrop that NO message arrived in either topic.
     *
     *   WHY this matters for Pravah:
     *   In the real Outbox Pattern (ADR-004), the "transaction" spans a database write
     *   and an outbox row — not two Kafka sends. But the principle is identical:
     *   you never want partial state. The job must be created AND audited, or neither.
     */
    public String publishWithTransaction(String tenantId, String pipelineId,
                                         String stepId, boolean simulateCrash) {
        String jobId = UUID.randomUUID().toString();

        // TODO: implement transactional publish

        return jobId;
    }
}
