package io.pravah.playground.graphql.controller;

import io.pravah.playground.graphql.model.Execution;
import io.pravah.playground.graphql.model.Runner;
import org.dataloader.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

/**
 * GraphQL controller for Execution type field resolvers.
 */
@Controller
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    /**
     * Resolver for Execution.runner.
     * Uses DataLoader to batch runner lookups across multiple executions.
     */
    @SchemaMapping(typeName = "Execution", field = "runner")
    public CompletableFuture<Runner> runner(
            Execution execution,
            DataLoader<String, Runner> dataLoader) {
        
        if (execution.runnerId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        log.debug("Resolver: Execution({}).runner - queuing load for runner {}",
                execution.id(), execution.runnerId());
        return dataLoader.load(execution.runnerId());
    }
}
