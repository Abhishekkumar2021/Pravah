package io.pravah.playground.kafka.consumer;

import io.pravah.playground.kafka.avro.JobCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

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

    // ─────────────────────────────────────────────────────────────
    // Task 2 — Basic consume
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Add the @KafkaListener annotation:
     *        @KafkaListener(
     *            topics = "${pravah.kafka.topics.job-created}",
     *            groupId = "${pravah.kafka.consumer-groups.execution-service}",
     *            containerFactory = "executionServiceFactory"
     *        )
     *
     *   2. Inside the method:
     *        a. Log the received event:
     *             log.info("[execution-service] Received job.created: jobId={} tenantId={} partition={} offset={}",
     *                 record.value().getJobId(), record.value().getTenantId(),
     *                 record.partition(), record.offset());
     *
     *        b. Simulate processing time: Thread.sleep(100)
     *           WHY: In the real system, processing involves DB writes, runner assignment, etc.
     *           The sleep makes rebalancing behavior visible in Task 5.
     *
     *        c. Check for poison pill (Task 4):
     *             if ("poison".equals(record.value().getStepId().toString())) {
     *                 throw new PoisonPillException("Intentional poison pill: stepId=poison");
     *             }
     *
     *        d. Publish job.completed (pretend the job ran successfully):
     *             kafkaTemplate.send("${pravah.kafka.topics.job-completed}", tenantId, completedEvent);
     *             (For now, publish the same JobCreated record — in real Pravah this would be a JobCompleted Avro record)
     *
     *        e. Acknowledge the offset ONLY after all of the above succeeds:
     *             acknowledgment.acknowledge();
     *           WHY: If we acknowledge before processing finishes and then crash,
     *           Kafka thinks the message was processed — it won't be redelivered.
     *           That's a silent data loss. Acknowledge last = at-least-once guarantee.
     *
     *   3. The method signature:
     *        public void consume(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment)
     */
    public void consume(ConsumerRecord<String, JobCreated> record, Acknowledgment acknowledgment) {
        // TODO: implement
    }
}
