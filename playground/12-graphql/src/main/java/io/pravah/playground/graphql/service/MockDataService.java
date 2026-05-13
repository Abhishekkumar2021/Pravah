package io.pravah.playground.graphql.service;

import io.pravah.playground.graphql.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Mock data service simulating downstream REST calls.
 * 
 * In production Pravah, each method here would be a REST call to a separate service:
 * - Pipeline Service
 * - Execution Service  
 * - Runner Service
 * 
 * The key learning: DataLoader batches multiple getX() calls into one getBatchX() call.
 */
@Service
public class MockDataService {

    private static final Logger log = LoggerFactory.getLogger(MockDataService.class);

    private final Map<String, Pipeline> pipelines = new HashMap<>();
    private final Map<String, Execution> executions = new HashMap<>();
    private final Map<String, Runner> runners = new HashMap<>();

    public MockDataService() {
        initMockData();
    }

    private void initMockData() {
        // Create mock pipelines
        var now = Instant.now();

        pipelines.put("p-1", new Pipeline(
                "p-1", "Customer ETL", "Daily customer data sync",
                3, "tenant-acme",
                List.of(
                        new PipelineStep("s-1", "extract_customers", StepType.SQL_TRANSFORM, List.of()),
                        new PipelineStep("s-2", "transform_customers", StepType.PYTHON_SCRIPT, List.of("s-1")),
                        new PipelineStep("s-3", "load_warehouse", StepType.SQL_TRANSFORM, List.of("s-2"))
                ),
                now.minus(30, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS)
        ));

        pipelines.put("p-2", new Pipeline(
                "p-2", "Revenue Analytics", "Hourly revenue aggregation",
                7, "tenant-acme",
                List.of(
                        new PipelineStep("s-4", "fetch_orders", StepType.SQL_TRANSFORM, List.of()),
                        new PipelineStep("s-5", "compute_revenue", StepType.SPARK_JOB, List.of("s-4")),
                        new PipelineStep("s-6", "update_dashboard", StepType.DBT_MODEL, List.of("s-5"))
                ),
                now.minus(60, ChronoUnit.DAYS), now.minus(30, ChronoUnit.MINUTES)
        ));

        pipelines.put("p-3", new Pipeline(
                "p-3", "User Behavior", "User activity analysis",
                2, "tenant-beta",
                List.of(
                        new PipelineStep("s-7", "collect_events", StepType.SQL_TRANSFORM, List.of()),
                        new PipelineStep("s-8", "aggregate_sessions", StepType.PYTHON_SCRIPT, List.of("s-7"))
                ),
                now.minus(10, ChronoUnit.DAYS), now.minus(2, ChronoUnit.HOURS)
        ));

        // Create mock executions
        executions.put("e-1", new Execution(
                "e-1", "p-1", ExecutionStatus.COMPLETED, TriggerType.SCHEDULED,
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS),
                List.of(
                        new Job("j-1", "s-1", JobStatus.SUCCEEDED, now.minus(2, ChronoUnit.HOURS), now.minus(90, ChronoUnit.MINUTES), null),
                        new Job("j-2", "s-2", JobStatus.SUCCEEDED, now.minus(90, ChronoUnit.MINUTES), now.minus(70, ChronoUnit.MINUTES), null),
                        new Job("j-3", "s-3", JobStatus.SUCCEEDED, now.minus(70, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.HOURS), null)
                ),
                "r-1"
        ));

        executions.put("e-2", new Execution(
                "e-2", "p-2", ExecutionStatus.FAILED, TriggerType.MANUAL,
                now.minus(1, ChronoUnit.HOURS), now.minus(30, ChronoUnit.MINUTES),
                List.of(
                        new Job("j-4", "s-4", JobStatus.SUCCEEDED, now.minus(1, ChronoUnit.HOURS), now.minus(50, ChronoUnit.MINUTES), null),
                        new Job("j-5", "s-5", JobStatus.FAILED, now.minus(50, ChronoUnit.MINUTES), now.minus(30, ChronoUnit.MINUTES), "OutOfMemoryError: Java heap space"),
                        new Job("j-6", "s-6", JobStatus.SKIPPED, null, null, null)
                ),
                "r-2"
        ));

        executions.put("e-3", new Execution(
                "e-3", "p-3", ExecutionStatus.RUNNING, TriggerType.EVENT,
                now.minus(10, ChronoUnit.MINUTES), null,
                List.of(
                        new Job("j-7", "s-7", JobStatus.SUCCEEDED, now.minus(10, ChronoUnit.MINUTES), now.minus(5, ChronoUnit.MINUTES), null),
                        new Job("j-8", "s-8", JobStatus.RUNNING, now.minus(5, ChronoUnit.MINUTES), null, null)
                ),
                "r-1"
        ));

        // Create mock runners
        runners.put("r-1", new Runner("r-1", "runner-us-east-1a", "us-east-1", RunnerStatus.BUSY));
        runners.put("r-2", new Runner("r-2", "runner-eu-west-1b", "eu-west-1", RunnerStatus.IDLE));
    }

    // ============ SINGLE-ITEM FETCHES ============

    public Optional<Pipeline> getPipeline(String id) {
        log.info("REST CALL: GET /v1/pipelines/{}", id);
        simulateLatency();
        return Optional.ofNullable(pipelines.get(id));
    }

    public Optional<Execution> getExecution(String id) {
        log.info("REST CALL: GET /v1/executions/{}", id);
        simulateLatency();
        return Optional.ofNullable(executions.get(id));
    }

    public Optional<Runner> getRunner(String id) {
        log.info("REST CALL: GET /v1/runners/{}", id);
        simulateLatency();
        return Optional.ofNullable(runners.get(id));
    }

    // ============ LIST FETCHES ============

    public List<Pipeline> getPipelinesByTenant(String tenantId) {
        log.info("REST CALL: GET /v1/pipelines?tenantId={}", tenantId);
        simulateLatency();
        return pipelines.values().stream()
                .filter(p -> p.tenantId().equals(tenantId))
                .toList();
    }

    // ============ BATCH FETCHES (for DataLoader) ============

    /**
     * Batch fetch latest executions for multiple pipelines.
     * This is what DataLoader calls instead of N individual calls.
     * 
     * WITHOUT DataLoader: 10 pipelines → 10 REST calls
     * WITH DataLoader:    10 pipelines → 1 REST call with batch
     */
    public Map<String, Execution> getLatestExecutionsBatch(Set<String> pipelineIds) {
        log.info("REST CALL (BATCH): GET /v1/executions/latest?pipelineIds={}", pipelineIds);
        simulateLatency();

        Map<String, Execution> result = new HashMap<>();
        for (Execution exec : executions.values()) {
            if (pipelineIds.contains(exec.pipelineId())) {
                // Keep only the latest execution per pipeline (simplified logic)
                result.merge(exec.pipelineId(), exec, (existing, newExec) ->
                        existing.startedAt().isAfter(newExec.startedAt()) ? existing : newExec);
            }
        }
        return result;
    }

    /**
     * Batch fetch runners by IDs.
     */
    public Map<String, Runner> getRunnersBatch(Set<String> runnerIds) {
        log.info("REST CALL (BATCH): GET /v1/runners?ids={}", runnerIds);
        simulateLatency();

        Map<String, Runner> result = new HashMap<>();
        for (String id : runnerIds) {
            Runner runner = runners.get(id);
            if (runner != null) {
                result.put(id, runner);
            }
        }
        return result;
    }

    /**
     * Batch fetch latest job status for multiple steps.
     */
    public Map<String, JobStatus> getLatestJobStatusBatch(Set<String> stepIds) {
        log.info("REST CALL (BATCH): GET /v1/jobs/latest-status?stepIds={}", stepIds);
        simulateLatency();

        Map<String, JobStatus> result = new HashMap<>();
        for (Execution exec : executions.values()) {
            for (Job job : exec.jobs()) {
                if (stepIds.contains(job.stepId())) {
                    result.put(job.stepId(), job.status());
                }
            }
        }
        return result;
    }

    /**
     * Get runner metrics (admin only).
     */
    public RunnerMetrics getRunnerMetrics(String runnerId) {
        log.info("REST CALL: GET /v1/runners/{}/metrics (ADMIN)", runnerId);
        simulateLatency();
        // Mock metrics
        return new RunnerMetrics(45.2, 67.8, 3, 5);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(10); // Simulate network latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
