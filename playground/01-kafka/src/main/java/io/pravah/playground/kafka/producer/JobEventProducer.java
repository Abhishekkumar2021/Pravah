package io.pravah.playground.kafka.producer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
     * HOW KAFKA ROUTING WORKS:
     *   Kafka uses the message KEY to decide which partition to send the message to.
     *   The default partitioner hashes the key → partition number.
     *   By using tenantId as the key:
     *     - All "acme" events → same partition (ordered for acme)
     *     - All "beta" events → same partition (ordered for beta)
     *     - acme and beta → possibly different partitions → processed in parallel
     *
     *   If you used a random UUID as the key, events for the same tenant
     *   could land on different partitions and be processed out of order.
     *
     * TRANSACTIONAL PRODUCER + Spring Kafka 3.x:
     *   application.yml sets transactional.id for Task 3. With that set, KafkaTemplate
     *   only allows sends inside executeInTransaction(...) (or a listener-managed txn).
     *   So even this single-message publish runs as its own tiny Kafka transaction:
     *   begin → send → commit. Consumers using isolation.level=read_committed only see
     *   it after commit (default consumer isolation is read_uncommitted — still fine).
     *
     *   Inside the callback we block on Future.get() so the transaction does not commit
     *   before the broker acks the produce request.
     */
    public String publish(String tenantId, String pipelineId, String stepId) {
        String jobId = UUID.randomUUID().toString();

        JobCreated event = JobCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setJobId(jobId)
                .setTenantId(tenantId)
                .setPipelineId(pipelineId)
                .setStepId(stepId)
                .setPriority(5)
                .setCreatedAt(System.currentTimeMillis())
                .build();

        return kafkaTemplate.executeInTransaction(ops -> {
            try {
                SendResult<String, Object> result =
                        ops.send(jobCreatedTopic, tenantId, event).get(30, TimeUnit.SECONDS);
                log.info("Published job.created: jobId={} partition={} offset={}",
                        jobId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while publishing job.created", e);
            } catch (ExecutionException | TimeoutException e) {
                log.error("Failed to publish job.created: jobId={}", jobId, e);
                throw new IllegalStateException("Could not publish job.created", e);
            }
            log.info("Committed transaction for job.created: jobId={} tenantId={}", jobId, tenantId);
            return jobId;
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Task 3 — Exactly-once with Kafka transactions
    // ─────────────────────────────────────────────────────────────

    /**
     * Publish a JobCreated event AND a job.audit.log event atomically.
     * Either BOTH arrive in Kafka or NEITHER does.
     *
     * HOW KAFKA TRANSACTIONS WORK:
     *   1. Producer calls beginTransaction()
     *   2. Producer sends messages to one or more topics/partitions
     *   3. Producer calls commitTransaction() — broker makes all messages visible atomically
     *   If anything throws between step 1 and 3, the transaction is aborted:
     *   no message becomes visible to any consumer configured with isolation.level=read_committed.
     *
     * executeInTransaction() is the Spring Kafka wrapper around this:
     *   - Opens the transaction before the lambda
     *   - Commits after the lambda returns normally
     *   - Aborts if the lambda throws
     *
     * WHY simulateCrash?
     *   To prove the guarantee: crash AFTER the first send, BEFORE the second.
     *   Without transactions, the first message would still be visible.
     *   With transactions, neither message appears → Kafdrop shows both topics empty.
     *
     * CONNECTION TO PRAVAH (ADR-004 Outbox Pattern):
     *   In the real system, the "transaction" spans a PostgreSQL write + an outbox row
     *   in the same DB transaction. The Outbox relay then reads the outbox and publishes
     *   to Kafka. This playground demonstrates the Kafka-only transaction variant —
     *   same atomicity guarantee, different scope.
     */
    public String publishWithTransaction(String tenantId, String pipelineId,
                                         String stepId, boolean simulateCrash) {
        String jobId = UUID.randomUUID().toString();

        JobCreated event = JobCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setJobId(jobId)
                .setTenantId(tenantId)
                .setPipelineId(pipelineId)
                .setStepId(stepId)
                .setPriority(5)
                .setCreatedAt(System.currentTimeMillis())
                .build();

        return kafkaTemplate.executeInTransaction(ops -> {
            try {
                ops.send(jobCreatedTopic, tenantId, event).get(30, TimeUnit.SECONDS);

                if (simulateCrash) {
                    throw new RuntimeException("Simulated crash after first send — transaction rolled back");
                }

                ops.send(jobAuditTopic, tenantId, event).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException(e);
            }

            log.info("Transaction committed: jobId={} in both topics", jobId);
            return jobId;
        });
    }
}
