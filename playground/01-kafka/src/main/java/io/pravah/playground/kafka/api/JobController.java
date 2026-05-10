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
     * TODO:
     *   1. Extract tenantId, pipelineId, stepId from the request body map
     *   2. Call producer.publish(tenantId, pipelineId, stepId)
     *   3. Return 202 Accepted with the jobId:
     *        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
     *
     *   WHY 202 and not 201?
     *   The job has been queued in Kafka but not yet processed. 201 Created implies
     *   the resource exists. 202 Accepted means "we received the request, processing
     *   will happen asynchronously." This is the correct HTTP semantics for async systems.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createJob(@RequestBody Map<String, String> body) {
        // TODO: implement
        return ResponseEntity.accepted().body(Map.of("status", "not implemented"));
    }

    // ─────────────────────────────────────────────────────────────
    // Task 3 — Transactional publish endpoint
    // ─────────────────────────────────────────────────────────────

    /**
     * TODO:
     *   1. Extract tenantId, pipelineId, stepId from body
     *   2. Extract simulateCrash (boolean) from body — default false
     *   3. Call producer.publishWithTransaction(tenantId, pipelineId, stepId, simulateCrash)
     *   4. Return 202 Accepted with jobId
     *   5. If the transaction throws (simulateCrash=true), catch the exception and
     *      return 500 with the error message — verify in Kafdrop that no message was written
     */
    @PostMapping("/transactional")
    public ResponseEntity<Map<String, String>> createJobTransactional(@RequestBody Map<String, Object> body) {
        // TODO: implement
        return ResponseEntity.accepted().body(Map.of("status", "not implemented"));
    }
}
