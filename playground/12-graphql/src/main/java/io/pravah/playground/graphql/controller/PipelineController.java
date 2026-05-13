package io.pravah.playground.graphql.controller;

import io.pravah.playground.graphql.dataloader.DataLoaderConfig;
import io.pravah.playground.graphql.model.*;
import io.pravah.playground.graphql.service.MockDataService;
import org.dataloader.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Controller;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL controller for Pipeline queries.
 * 
 * @QueryMapping  - maps to Query type fields
 * @SchemaMapping - maps to nested type fields (e.g., Pipeline.latestExecution)
 * 
 * Key concept: SchemaMapping methods that use DataLoader return CompletableFuture.
 * This allows GraphQL to batch all DataLoader calls before executing them.
 */
@Controller
public class PipelineController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

    private final MockDataService dataService;

    public PipelineController(MockDataService dataService) {
        this.dataService = dataService;
    }

    // ============ ROOT QUERIES ============

    @QueryMapping
    public Pipeline pipeline(@Argument String id) {
        log.info("Query: pipeline(id={})", id);
        return dataService.getPipeline(id).orElse(null);
    }

    @QueryMapping
    public PipelineConnection pipelines(@Argument String tenantId,
                                        @Argument Integer first,
                                        @Argument String after) {
        log.info("Query: pipelines(tenantId={}, first={}, after={})", tenantId, first, after);

        List<Pipeline> allPipelines = dataService.getPipelinesByTenant(tenantId);

        // Simple cursor-based pagination
        int startIndex = 0;
        if (after != null) {
            String decodedCursor = new String(Base64.getDecoder().decode(after));
            for (int i = 0; i < allPipelines.size(); i++) {
                if (allPipelines.get(i).id().equals(decodedCursor)) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int pageSize = first != null ? first : 10;
        int endIndex = Math.min(startIndex + pageSize, allPipelines.size());

        List<Pipeline> page = allPipelines.subList(startIndex, endIndex);
        List<PipelineEdge> edges = page.stream()
                .map(p -> new PipelineEdge(p, Base64.getEncoder().encodeToString(p.id().getBytes())))
                .toList();

        PageInfo pageInfo = new PageInfo(
                endIndex < allPipelines.size(),
                startIndex > 0,
                edges.isEmpty() ? null : edges.getFirst().cursor(),
                edges.isEmpty() ? null : edges.getLast().cursor()
        );

        return new PipelineConnection(edges, pageInfo, allPipelines.size());
    }

    @QueryMapping
    public Execution execution(@Argument String id) {
        log.info("Query: execution(id={})", id);
        return dataService.getExecution(id).orElse(null);
    }

    // ============ NESTED FIELD RESOLVERS ============

    /**
     * Resolver for Pipeline.latestExecution.
     * Uses DataLoader to batch multiple pipeline's execution lookups.
     * 
     * When querying 10 pipelines each requesting latestExecution:
     * - This method is called 10 times (once per pipeline)
     * - Each call does dataLoader.load(pipelineId) 
     * - DataLoader collects all 10 IDs
     * - At the end, DataLoader makes ONE batch call to the service
     */
    @SchemaMapping(typeName = "Pipeline", field = "latestExecution")
    public CompletableFuture<Execution> latestExecution(
            Pipeline pipeline,
            DataLoader<String, Execution> dataLoader) {
        
        log.debug("Resolver: Pipeline({}).latestExecution - queuing load", pipeline.id());
        return dataLoader.load(pipeline.id());
    }

    /**
     * Resolver for PipelineStep.latestJobStatus.
     * Batched via DataLoader.
     */
    @SchemaMapping(typeName = "PipelineStep", field = "latestJobStatus")
    public CompletableFuture<JobStatus> latestJobStatus(
            PipelineStep step,
            DataLoader<String, JobStatus> dataLoader) {
        
        log.debug("Resolver: PipelineStep({}).latestJobStatus - queuing load", step.id());
        return dataLoader.load(step.id());
    }

    // ============ PAGINATION TYPES ============

    public record PipelineConnection(
            List<PipelineEdge> edges,
            PageInfo pageInfo,
            int totalCount
    ) {}

    public record PipelineEdge(
            Pipeline node,
            String cursor
    ) {}

    public record PageInfo(
            boolean hasNextPage,
            boolean hasPreviousPage,
            String startCursor,
            String endCursor
    ) {}
}
