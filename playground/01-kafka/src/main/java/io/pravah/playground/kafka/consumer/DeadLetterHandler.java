package io.pravah.playground.kafka.consumer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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
 */
@Slf4j
@Component
public class DeadLetterHandler {

    // ─────────────────────────────────────────────────────────────
    // Task 4 — Handle DLT messages
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Add the @KafkaListener annotation:
     *        @KafkaListener(
     *            topics = "${pravah.kafka.topics.job-created-dlt}",
     *            groupId = "dlt-handler",
     *            containerFactory = "auditServiceFactory"   ← reuse the audit factory (no DLT on DLT)
     *        )
     *
     *   2. Extract and log the DLT headers:
     *        String exceptionClass = headerValue(record, "kafka_dlt-exception-fqcn");
     *        String exceptionMsg   = headerValue(record, "kafka_dlt-exception-message");
     *        String originalTopic  = headerValue(record, "kafka_dlt-original-topic");
     *        String originalOffset = headerValue(record, "kafka_dlt-original-offset");
     *
     *        log.error("[DLT] Poison pill received: jobId={} tenantId={} | exception={} | msg={} | from={}@{}",
     *            record.value().getJobId(), record.value().getTenantId(),
     *            exceptionClass, exceptionMsg, originalTopic, originalOffset);
     *
     *   3. Acknowledge the DLT message (we've recorded the failure, move on)
     *        acknowledgment.acknowledge();
     *
     *   4. Method signature:
     *        public void handleDlt(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment)
     *
     * After implementing, watch what happens when you send stepId=poison:
     *   - Main consumer logs "Processing failed, retrying..."  x3
     *   - DLT handler logs the poison pill
     *   - Kafdrop shows the message in pravah.job.created.DLT with all headers
     *   - The main topic offset advances — no other messages are blocked
     */
    public void handleDlt(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        // TODO: implement
    }

    /** Reads a header value as a UTF-8 string. Returns "unknown" if the header is absent. */
    private String headerValue(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) return "unknown";
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
