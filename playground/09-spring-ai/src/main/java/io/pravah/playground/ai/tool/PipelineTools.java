package io.pravah.playground.ai.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

/**
 * Tools the agent can call during ReAct loops.
 * In Pravah, these would call internal services; here they're mocked for learning.
 *
 * Each @Bean function becomes a tool the LLM can invoke via Spring AI's function calling.
 */
@Configuration
public class PipelineTools {

    private static final Logger log = LoggerFactory.getLogger(PipelineTools.class);

    /**
     * Tool: Get execution logs for a failed job.
     * In real Pravah: calls Execution Service gRPC API.
     */
    @Bean
    @Description("Get the last N lines of logs for a specific pipeline step execution")
    public Function<GetLogsRequest, GetLogsResponse> getExecutionLogs() {
        return request -> {
            log.info("[Tool] getExecutionLogs called: executionId={}, stepId={}, lines={}",
                    request.executionId(), request.stepId(), request.lastNLines());

            // Mock response simulating a schema error
            String mockLogs = """
                [2026-05-13 09:15:23] INFO  Starting transform step...
                [2026-05-13 09:15:24] INFO  Loading source data from postgres://prod/orders
                [2026-05-13 09:15:25] ERROR Column 'loyalty_tier' not found in source table 'orders'
                [2026-05-13 09:15:25] ERROR Schema mismatch: expected columns [customer_id, loyalty_tier, revenue]
                [2026-05-13 09:15:25] ERROR Transform step failed with SchemaError
                """;

            return new GetLogsResponse(request.executionId(), request.stepId(), mockLogs);
        };
    }

    public record GetLogsRequest(String executionId, String stepId, int lastNLines) {}
    public record GetLogsResponse(String executionId, String stepId, String logs) {}

    /**
     * Tool: Get pipeline definition (the SQL transform, source, destination).
     * In real Pravah: reads from Pipeline Service event store.
     */
    @Bean
    @Description("Get the current definition of a pipeline including its transform SQL")
    public Function<GetPipelineRequest, GetPipelineResponse> getPipelineDefinition() {
        return request -> {
            log.info("[Tool] getPipelineDefinition called: pipelineId={}", request.pipelineId());

            return new GetPipelineResponse(
                    request.pipelineId(),
                    "v3",
                    "postgres://prod/public.orders",
                    "s3://data-lake/orders/daily/",
                    "SELECT customer_id, loyalty_tier, revenue FROM orders WHERE created_at > '{{ last_run }}'"
            );
        };
    }

    public record GetPipelineRequest(String pipelineId) {}
    public record GetPipelineResponse(
            String pipelineId,
            String version,
            String source,
            String destination,
            String transformSql
    ) {}

    /**
     * Tool: Get column lineage for a dataset.
     * In real Pravah: queries Metadata Service lineage graph.
     */
    @Bean
    @Description("Get column lineage information showing when columns were added/removed from a dataset")
    public Function<GetLineageRequest, GetLineageResponse> getColumnLineage() {
        return request -> {
            log.info("[Tool] getColumnLineage called: datasetId={}", request.datasetId());

            // Mock: loyalty_tier was removed recently
            List<ColumnChange> changes = List.of(
                    new ColumnChange("loyalty_tier", "REMOVED", "2026-05-08T14:30:00Z",
                            "Column dropped by upstream team"),
                    new ColumnChange("customer_id", "PRESENT", null, null),
                    new ColumnChange("revenue", "PRESENT", null, null)
            );

            return new GetLineageResponse(request.datasetId(), changes);
        };
    }

    public record GetLineageRequest(String datasetId) {}
    public record GetLineageResponse(String datasetId, List<ColumnChange> columns) {}
    public record ColumnChange(String column, String status, String changedAt, String reason) {}

    /**
     * Tool: Get recent runs for a pipeline.
     * In real Pravah: queries Execution Service.
     */
    @Bean
    @Description("Get the most recent execution runs for a pipeline with their status")
    public Function<GetRecentRunsRequest, GetRecentRunsResponse> getRecentRuns() {
        return request -> {
            log.info("[Tool] getRecentRuns called: pipelineId={}, count={}", 
                    request.pipelineId(), request.count());

            List<RunSummary> runs = List.of(
                    new RunSummary("exec-005", "FAILED", "2026-05-13T09:15:00Z", "SchemaError"),
                    new RunSummary("exec-004", "SUCCESS", "2026-05-12T09:15:00Z", null),
                    new RunSummary("exec-003", "SUCCESS", "2026-05-11T09:15:00Z", null),
                    new RunSummary("exec-002", "SUCCESS", "2026-05-10T09:15:00Z", null),
                    new RunSummary("exec-001", "SUCCESS", "2026-05-09T09:15:00Z", null)
            );

            return new GetRecentRunsResponse(request.pipelineId(), 
                    runs.subList(0, Math.min(request.count(), runs.size())));
        };
    }

    public record GetRecentRunsRequest(String pipelineId, int count) {}
    public record GetRecentRunsResponse(String pipelineId, List<RunSummary> runs) {}
    public record RunSummary(String executionId, String status, String startedAt, String error) {}

    /**
     * Tool: Propose a fix (creates a draft pipeline version).
     * In real Pravah: validates SQL, creates draft in Pipeline Service.
     */
    @Bean
    @Description("Propose a fix for a pipeline step by providing new SQL. Returns a draft version for review.")
    public Function<ProposeFixRequest, ProposeFixResponse> proposeFix() {
        return request -> {
            log.info("[Tool] proposeFix called: pipelineId={}, stepId={}", 
                    request.pipelineId(), request.stepId());
            log.info("[Tool] Proposed SQL:\n{}", request.newSql());

            // Mock: validate the SQL and create a draft version
            boolean sqlValid = request.newSql() != null && 
                    request.newSql().toUpperCase().contains("SELECT");

            if (!sqlValid) {
                return new ProposeFixResponse(null, "VALIDATION_FAILED", 
                        "Invalid SQL: must be a SELECT statement");
            }

            String draftVersionId = "v4-draft-" + Instant.now().toEpochMilli();
            return new ProposeFixResponse(draftVersionId, "VALIDATION_PASSED",
                    "Draft version created. Ready for review.");
        };
    }

    public record ProposeFixRequest(String pipelineId, String stepId, String newSql) {}
    public record ProposeFixResponse(String draftVersionId, String validationStatus, String message) {}
}
