package io.pravah.playground.graphql.dataloader;

import io.pravah.playground.graphql.model.Execution;
import io.pravah.playground.graphql.model.JobStatus;
import io.pravah.playground.graphql.model.Runner;
import io.pravah.playground.graphql.service.MockDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * DataLoader configuration for GraphQL using Spring's BatchLoaderRegistry.
 * 
 * DataLoader solves the N+1 problem:
 * - GraphQL resolver calls DataLoader.load(id) for each item
 * - DataLoader batches all load() calls within a single GraphQL execution
 * - At the end, DataLoader calls the batch loader ONCE with all IDs
 * 
 * Example: Query for 10 pipelines, each requesting latestExecution:
 * - WITHOUT DataLoader: 10 separate REST calls to Execution Service
 * - WITH DataLoader: 1 batch REST call with all 10 pipeline IDs
 */
@Component
public class DataLoaderConfig {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderConfig.class);

    public DataLoaderConfig(BatchLoaderRegistry registry, MockDataService dataService) {
        // Register DataLoader for: Pipeline -> latestExecution
        // The key is the value type (Execution.class), which Spring uses to match
        registry.forTypePair(String.class, Execution.class)
                .registerMappedBatchLoader((pipelineIds, env) -> {
                    log.info("DataLoader: Batch loading latest executions for {} pipelines", pipelineIds.size());
                    Map<String, Execution> result = dataService.getLatestExecutionsBatch(pipelineIds);
                    return Mono.just(result);
                });

        // Register DataLoader for: Execution -> runner
        registry.forTypePair(String.class, Runner.class)
                .registerMappedBatchLoader((runnerIds, env) -> {
                    log.info("DataLoader: Batch loading {} runners", runnerIds.size());
                    Map<String, Runner> result = dataService.getRunnersBatch(runnerIds);
                    return Mono.just(result);
                });

        // Register DataLoader for: PipelineStep -> latestJobStatus
        registry.forTypePair(String.class, JobStatus.class)
                .registerMappedBatchLoader((stepIds, env) -> {
                    log.info("DataLoader: Batch loading job statuses for {} steps", stepIds.size());
                    Map<String, JobStatus> result = dataService.getLatestJobStatusBatch(stepIds);
                    return Mono.just(result);
                });
    }
}
