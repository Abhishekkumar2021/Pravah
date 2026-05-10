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
 */
@Slf4j
@Component
public class AuditConsumer {

    // In-memory audit log. In real Pravah this would be a PostgreSQL append-only table.
    // CopyOnWriteArrayList because multiple listener threads write concurrently.
    private final List<String> auditLog = Collections.synchronizedList(new ArrayList<>());

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Independent consumer group
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Add the @KafkaListener annotation:
     *        @KafkaListener(
     *            topics = "${pravah.kafka.topics.job-created}",
     *            groupId = "${pravah.kafka.consumer-groups.audit-service}",
     *            containerFactory = "auditServiceFactory"
     *        )
     *
     *   2. Inside the method:
     *        a. Build an audit entry string:
     *             String entry = String.format("[AUDIT] jobId=%s tenantId=%s pipelineId=%s stepId=%s at=%d",
     *                 record.value().getJobId(), record.value().getTenantId(),
     *                 record.value().getPipelineId(), record.value().getStepId(),
     *                 record.value().getCreatedAt());
     *
     *        b. Add it to auditLog
     *        c. Log it: log.info(entry)
     *        d. Acknowledge: acknowledgment.acknowledge()
     *
     *   3. Method signature:
     *        public void audit(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment)
     *
     * After implementing, open Kafdrop (http://localhost:9000) and check the consumer groups tab.
     * You will see TWO groups — execution-service and audit-service — each with their own offsets
     * on the pravah.job.created topic. This is the proof that fan-out works.
     */
    public void audit(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        // TODO: implement
    }

    /** Exposes the audit log for the test in Task 2. */
    public List<String> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }
}
