package io.pravah.playground.graphql.controller;

import io.pravah.playground.graphql.model.AuthContext;
import io.pravah.playground.graphql.model.Role;
import io.pravah.playground.graphql.model.Runner;
import io.pravah.playground.graphql.model.RunnerMetrics;
import io.pravah.playground.graphql.service.MockDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;

/**
 * GraphQL controller for Runner type field resolvers.
 * 
 * Demonstrates FIELD-LEVEL AUTHORIZATION:
 * - Runner.internalMetrics requires ADMIN role
 * - Regular users see null for this field
 */
@Controller
public class RunnerController {

    private static final Logger log = LoggerFactory.getLogger(RunnerController.class);

    private final MockDataService dataService;

    public RunnerController(MockDataService dataService) {
        this.dataService = dataService;
    }

    /**
     * Resolver for Runner.internalMetrics.
     * 
     * FIELD-LEVEL AUTHORIZATION:
     * Only users with ADMIN role can see internal metrics.
     * Non-admin users get null (or you could throw an error).
     * 
     * In production:
     * - AuthContext comes from JWT token parsed in the GraphQL interceptor
     * - For the playground, we simulate via X-Role header
     */
    @SchemaMapping(typeName = "Runner", field = "internalMetrics")
    public RunnerMetrics internalMetrics(Runner runner, DataFetchingEnvironment env) {
        GraphQLContext context = env.getGraphQlContext();
        AuthContext authContext = context.get("authContext");

        if (authContext == null) {
            log.warn("No auth context - denying access to internalMetrics");
            return null;
        }

        if (!authContext.isAdmin()) {
            log.info("User {} (role={}) denied access to Runner({}).internalMetrics",
                    authContext.userId(), authContext.role(), runner.id());
            return null;
        }

        log.info("Admin user {} accessing Runner({}).internalMetrics",
                authContext.userId(), runner.id());
        return dataService.getRunnerMetrics(runner.id());
    }
}
