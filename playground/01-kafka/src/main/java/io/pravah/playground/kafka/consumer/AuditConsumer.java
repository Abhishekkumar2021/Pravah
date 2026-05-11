package io.pravah.playground.kafka.consumer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulates the Audit Service reading the same topic independently.
 *
 * Consumer group: audit-service
 *
 * This is the core lesson of Task 2: TWO consumer groups reading the SAME topic.
 * Both groups receive every message. Neither is aware of the other.
 * Neither blocks the other. Their offsets advance independently.
 *
 * In Pravah, multiple services consume pravah.job.created:
 *   - execution-service: manages job lifecycle
 *   - audit-service:     writes immutable audit trail
 *   - billing-service:   meters compute usage
 * All three are independent consumer groups on the same topic.
 *
 * HOW KAFKA FAN-OUT WORKS:
 *   Kafka does NOT push messages to consumers. Consumers PULL from the broker.
 *   Each consumer group has its own committed offset per partition.
 *   When the broker gets a message, it just appends it to the partition log.
 *   Every consumer group independently tracks where it has read up to.
 *   The message stays in the log until the retention period expires —
 *   not until all consumers have read it.
 */
@Slf4j
@Component
public class AuditConsumer {

    // In-memory audit log. In real Pravah this would be a PostgreSQL append-only table.
    // synchronizedList because multiple listener threads (concurrency=3) write concurrently.
    private final List<String> auditLog = Collections.synchronizedList(new ArrayList<>());

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Independent consumer group
    // ─────────────────────────────────────────────────────────────

    /**
     * Note: containerFactory = "auditServiceFactory"
     *   → uses the factory with group.id = "audit-service"
     *   → offsets are tracked SEPARATELY from execution-service
     *   → if execution-service is slow or down, audit-service is unaffected
     *
     * After running, open Kafdrop (http://localhost:9000) → Consumer Groups.
     * You will see TWO groups, each with their own lag and offset on pravah.job.created.
     * That is proof of independent fan-out.
     */
    @KafkaListener(
            topics = "${pravah.kafka.topics.job-created}",
            groupId = "${pravah.kafka.consumer-groups.audit-service}",
            containerFactory = "auditServiceFactory"
    )
    public void audit(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        JobCreated event = record.value();

        String entry = String.format("[AUDIT] jobId=%s tenantId=%s pipelineId=%s stepId=%s at=%d",
                event.getJobId(), event.getTenantId(),
                event.getPipelineId(), event.getStepId(),
                event.getCreatedAt());

        auditLog.add(entry);
        log.info(entry);

        // Acknowledge: we've written to our audit log, mark this offset done.
        acknowledgment.acknowledge();
    }

    /** Exposes the audit log for the test in Task 2. */
    public List<String> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }
}
