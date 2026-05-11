package io.pravah.playground.kafka.consumer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simulates the Execution Service consuming job.created events.
 *
 * Consumer group: execution-service
 * This group gets its own set of offsets, independent of audit-service.
 *
 * Key concepts demonstrated:
 *   - Manual offset acknowledgment (Acknowledgment.acknowledge())
 *   - Poison pill detection (Task 4)
 *   - Publishing a downstream event after processing (job.completed)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobEventConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${pravah.kafka.topics.job-completed}")
    private String jobCompletedTopic;

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Basic consume + Task 4 — Poison pill
    // ─────────────────────────────────────────────────────────────

    /**
     * HOW @KafkaListener WORKS:
     *   Spring creates a thread pool (concurrency=3, one per partition) that
     *   continuously polls Kafka for new messages. When a message arrives,
     *   Spring deserializes it and calls this method on one of those threads.
     *
     *   topics:           which topic to subscribe to
     *   groupId:          this consumer group's ID — offsets are tracked per group
     *   containerFactory: which factory bean to use → executionServiceFactory
     *                     (that factory has the DLT error handler attached)
     *
     * WHY ConsumerRecord instead of just JobCreated?
     *   ConsumerRecord gives you access to the metadata: partition number, offset,
     *   timestamp, and headers. You need partition+offset to log what you processed,
     *   and headers to extract DLT metadata in DeadLetterHandler.
     *
     * WHY Acknowledgment as a parameter?
     *   With ack-mode=MANUAL_IMMEDIATE, Spring passes the Acknowledgment object
     *   to your method. You MUST call acknowledgment.acknowledge() yourself.
     *   If you don't call it, the offset is never committed — on restart, Kafka
     *   will redeliver this message (which is sometimes what you want).
     */
    @KafkaListener(
            topics = "${pravah.kafka.topics.job-created}",
            groupId = "${pravah.kafka.consumer-groups.execution-service}",
            containerFactory = "executionServiceFactory"
    )
    public void consume(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        JobCreated event = record.value();

        log.info("[execution-service] Received job.created: jobId={} tenantId={} partition={} offset={}",
                event.getJobId(), event.getTenantId(), record.partition(), record.offset());

        // Simulate the real processing time (DB writes, runner assignment, etc.).
        // This also makes partition rebalancing visible during Task 5.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Poison pill check (Task 4):
        // If stepId == "poison", throw PoisonPillException.
        // The executionServiceFactory's DefaultErrorHandler catches it and retries
        // 3 times with exponential backoff (1s, 2s, 4s). After exhausting retries,
        // it routes the message to pravah.job.created.DLT.
        // The main topic offset then advances — other messages are NOT blocked.
        if ("poison".equals(event.getStepId().toString())) {
            throw new PoisonPillException("Intentional poison pill: stepId=poison for jobId=" + event.getJobId());
        }

        // Same transactional KafkaTemplate rules as JobEventProducer.publish — wrap send.
        kafkaTemplate.executeInTransaction(ops -> {
            try {
                SendResult<String, Object> result =
                        ops.send(jobCompletedTopic, event.getTenantId().toString(), event)
                                .get(30, TimeUnit.SECONDS);
                log.info("[execution-service] Published job.completed: jobId={} partition={} offset={}",
                        event.getJobId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("job.completed publish failed for jobId=" + event.getJobId(), e);
            }
            return null;
        });

        // Acknowledge LAST — only after all processing and downstream publish succeed.
        //
        // WHY ORDER MATTERS:
        //   If we acknowledge FIRST and then crash before publishing job.completed,
        //   the offset is committed — Kafka won't redeliver this message.
        //   The job silently disappears: created but never completed.
        //   Acknowledge LAST = at-least-once guarantee: if we crash, Kafka
        //   redelivers and we process again. Your processing must be idempotent
        //   (use event_id to skip already-processed messages).
        acknowledgment.acknowledge();
    }
}
