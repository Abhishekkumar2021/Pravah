package io.pravah.playground.kafka.consumer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumes messages from the Dead Letter Topic (DLT).
 *
 * When the DefaultErrorHandler gives up retrying a message, it publishes it here.
 * Spring Kafka automatically adds headers to the DLT message:
 *
 *   kafka_dlt-exception-fqcn          → fully qualified class name of the exception
 *   kafka_dlt-exception-message       → exception message
 *   kafka_dlt-exception-cause-fqcn    → root cause class
 *   kafka_dlt-original-topic          → the topic the message came from
 *   kafka_dlt-original-partition      → the partition
 *   kafka_dlt-original-offset         → the original offset
 *
 * In real Pravah, the DLT handler would:
 *   1. Write the message to a dead_letter_events table in PostgreSQL
 *   2. Alert the team via PagerDuty / Slack
 *   3. Expose a /v1/dead-letters endpoint for operators to inspect and replay
 *
 * WHY A DLT AND NOT JUST LOGGING?
 *   If we only logged and acknowledged, the message is gone forever.
 *   A DLT is a Kafka topic — the message is durable. An operator can:
 *     a. Fix the bug
 *     b. Read from the DLT
 *     c. Re-publish to the original topic
 *   Zero data loss even for unhandled failures.
 */
@Slf4j
@Component
public class DeadLetterHandler {

    // Counts DLT messages received — used by KafkaIntegrationTest to assert
    // that a poison pill was correctly routed to the DLT.
    private final AtomicInteger dltCount = new AtomicInteger(0);

    // ─────────────────────────────────────────────────────────────
    // Task 4 — Handle DLT messages
    // ─────────────────────────────────────────────────────────────

    /**
     * Note: containerFactory = "auditServiceFactory"
     *   We reuse the audit factory (no DLT error handler) so we don't create
     *   an infinite loop: DLT message fails → tries to route to DLT-of-DLT.
     *
     * The groupId "dlt-handler" is a distinct consumer group.
     * It is NOT the same as execution-service or audit-service.
     */
    @KafkaListener(
            topics = "${pravah.kafka.topics.job-created-dlt}",
            groupId = "dlt-handler",
            containerFactory = "auditServiceFactory"
    )
    public void handleDlt(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        // Extract the DLT diagnostic headers added by Spring Kafka's DeadLetterPublishingRecoverer
        String exceptionClass  = headerValue(record, "kafka_dlt-exception-fqcn");
        String exceptionMsg    = headerValue(record, "kafka_dlt-exception-message");
        String originalTopic   = headerValue(record, "kafka_dlt-original-topic");
        String originalOffset  = headerValue(record, "kafka_dlt-original-offset");

        log.error("[DLT] Poison pill received: jobId={} tenantId={} | exception={} | msg={} | from={}@{}",
                record.value().getJobId(), record.value().getTenantId(),
                exceptionClass, exceptionMsg, originalTopic, originalOffset);

        // In real Pravah: persist to dead_letter_events table + alert ops team.
        dltCount.incrementAndGet();

        // Acknowledge: we've recorded the failure, let the DLT offset advance.
        acknowledgment.acknowledge();
    }

    /** Returns how many DLT messages have been received. Used in integration tests. */
    public int getDltCount() {
        return dltCount.get();
    }

    /** Reads a header value as a UTF-8 string. Returns "unknown" if the header is absent. */
    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) return "unknown";
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
