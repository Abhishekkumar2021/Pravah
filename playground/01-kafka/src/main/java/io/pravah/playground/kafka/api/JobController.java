package io.pravah.playground.kafka.api;

import io.pravah.playground.kafka.producer.JobEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint to trigger job events manually during the exercise.
 *
 * You use this with curl to drive the exercise scenarios:
 *
 *   # Task 1 — basic publish
 *   curl -X POST http://localhost:8080/jobs \
 *     -H "Content-Type: application/json" \
 *     -d '{"tenantId":"acme","pipelineId":"orders-etl","stepId":"extract"}'
 *
 *   # Task 3 — transactional publish
 *   curl -X POST http://localhost:8080/jobs/transactional \
 *     -H "Content-Type: application/json" \
 *     -d '{"tenantId":"acme","pipelineId":"orders-etl","stepId":"extract","simulateCrash":false}'
 *
 *   # Task 4 — trigger poison pill
 *   curl -X POST http://localhost:8080/jobs \
 *     -H "Content-Type: application/json" \
 *     -d '{"tenantId":"acme","pipelineId":"orders-etl","stepId":"poison"}'
 *
 *   # Task 5 — simulate 10 tenants (different partition keys)
 *   for t in acme beta gamma delta epsilon; do
 *     curl -s -X POST http://localhost:8080/jobs \
 *       -H "Content-Type: application/json" \
 *       -d "{\"tenantId\":\"$t\",\"pipelineId\":\"orders-etl\",\"stepId\":\"extract\"}"
 *   done
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobEventProducer producer;

    // ─────────────────────────────────────────────────────────────
    // Task 1 — Basic publish endpoint
    // ─────────────────────────────────────────────────────────────

    /**
     * WHY 202 Accepted and not 201 Created?
     *   201 Created implies the resource now exists in the system.
     *   At this point we have only written to Kafka — the job has not
     *   been picked up by the execution service yet.
     *   202 Accepted = "we received and queued your request; processing is async."
     *   This is the correct HTTP semantics for any event-driven, async API.
     *   Pravah's real Pipeline Service returns 202 for /v1/pipelines/{id}/trigger.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> body) {
        String tenantId    = body.get("tenantId");
        String pipelineId  = body.get("pipelineId");
        String stepId      = body.get("stepId");

        String jobId = producer.publish(tenantId, pipelineId, stepId);

        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    // ─────────────────────────────────────────────────────────────
    // Task 3 — Transactional publish endpoint
    // ─────────────────────────────────────────────────────────────

    /**
     * Demonstrates Kafka transactions (exactly-once).
     *
     * Try with simulateCrash=true:
     *   - The response will be 500
     *   - Open Kafdrop: pravah.job.created AND pravah.job.audit.log are BOTH empty
     *   - This proves the transaction was fully rolled back
     *
     * Try with simulateCrash=false:
     *   - Both topics receive the message atomically
     */
    @PostMapping("/transactional")
    public ResponseEntity<Map<String, String>> createJobTransactional(@RequestBody Map<String, Object> body) {
        String tenantId    = (String) body.get("tenantId");
        String pipelineId  = (String) body.get("pipelineId");
        String stepId      = (String) body.get("stepId");
        boolean simulateCrash = Boolean.TRUE.equals(body.get("simulateCrash"));

        try {
            String jobId = producer.publishWithTransaction(tenantId, pipelineId, stepId, simulateCrash);
            return ResponseEntity.accepted().body(Map.of("jobId", jobId));
        } catch (RuntimeException ex) {
            // The transaction was aborted — no message was written to any topic.
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
