package io.pravah.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GraphQL Service Application Entry Point.
 * <p>
 * Provides a unified GraphQL API that federates data from multiple domain services.
 * Supports real-time subscriptions for pipeline run updates.
 *
 * @see <a href="../../../docs/adr/ADR-015-graphql-api.md">ADR-015: GraphQL API</a>
 */
@SpringBootApplication
public class GraphQLApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphQLApplication.class, args);
    }
}
