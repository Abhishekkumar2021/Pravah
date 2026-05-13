package io.pravah.playground.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GraphQL playground.
 * 
 * Tests demonstrate:
 * 1. Basic query execution
 * 2. Nested field resolution (Pipeline -> latestExecution -> runner)
 * 3. DataLoader batching (visible in logs)
 * 4. Field-level authorization
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class GraphQLIT {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void testPipelineQuery() {
        graphQlTester.document("""
            query {
                pipeline(id: "p-1") {
                    id
                    name
                    version
                    tenantId
                }
            }
            """)
                .execute()
                .path("pipeline.id").entity(String.class).isEqualTo("p-1")
                .path("pipeline.name").entity(String.class).isEqualTo("Customer ETL")
                .path("pipeline.version").entity(Integer.class).isEqualTo(3);
    }

    @Test
    void testPipelineWithSteps() {
        graphQlTester.document("""
            query {
                pipeline(id: "p-1") {
                    id
                    name
                    steps {
                        id
                        name
                        stepType
                        dependsOn
                    }
                }
            }
            """)
                .execute()
                .path("pipeline.steps").entityList(Object.class).hasSize(3)
                .path("pipeline.steps[0].name").entity(String.class).isEqualTo("extract_customers")
                .path("pipeline.steps[1].dependsOn[0]").entity(String.class).isEqualTo("s-1");
    }

    @Test
    void testNestedLatestExecution_triggersDataLoader() {
        // This query triggers DataLoader for:
        // 1. Pipeline.latestExecution (1 call batched)
        // 2. Execution.runner (1 call batched)
        // Check logs to verify batch behavior
        graphQlTester.document("""
            query {
                pipeline(id: "p-1") {
                    id
                    name
                    latestExecution {
                        id
                        status
                        runner {
                            id
                            hostname
                            region
                        }
                    }
                }
            }
            """)
                .execute()
                .path("pipeline.latestExecution.id").entity(String.class).isEqualTo("e-1")
                .path("pipeline.latestExecution.status").entity(String.class).isEqualTo("COMPLETED")
                .path("pipeline.latestExecution.runner.hostname").entity(String.class).isEqualTo("runner-us-east-1a");
    }

    @Test
    void testPipelinesListWithPagination() {
        graphQlTester.document("""
            query {
                pipelines(tenantId: "tenant-acme", first: 2) {
                    edges {
                        node {
                            id
                            name
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                        hasPreviousPage
                    }
                    totalCount
                }
            }
            """)
                .execute()
                .path("pipelines.totalCount").entity(Integer.class).isEqualTo(2)
                .path("pipelines.edges").entityList(Object.class).hasSize(2);
    }

    @Test
    void testMultiplePipelines_dataLoaderBatches() {
        // Query multiple pipelines, each requesting latestExecution
        // DataLoader should batch these into ONE service call (check logs)
        graphQlTester.document("""
            query {
                pipelines(tenantId: "tenant-acme") {
                    edges {
                        node {
                            id
                            name
                            latestExecution {
                                id
                                status
                            }
                        }
                    }
                }
            }
            """)
                .execute()
                .path("pipelines.edges").entityList(Object.class).hasSize(2);
    }

    @Test
    void testRunnerMetrics_deniedForNonAdmin() {
        // Without X-Role: ADMIN header, internalMetrics should be null
        graphQlTester.document("""
            query {
                pipeline(id: "p-1") {
                    latestExecution {
                        runner {
                            id
                            hostname
                            internalMetrics {
                                cpuPercent
                                memoryPercent
                            }
                        }
                    }
                }
            }
            """)
                .execute()
                .path("pipeline.latestExecution.runner.id").entity(String.class).isEqualTo("r-1")
                .path("pipeline.latestExecution.runner.internalMetrics").valueIsNull();
    }

    @Test
    void testRunnerMetrics_allowedForAdmin() {
        // With X-Role: ADMIN header, internalMetrics should be visible
        graphQlTester.mutate()
                .header("X-Role", "ADMIN")
                .header("X-User-ID", "admin-user")
                .build()
                .document("""
                    query {
                        pipeline(id: "p-1") {
                            latestExecution {
                                runner {
                                    id
                                    hostname
                                    internalMetrics {
                                        cpuPercent
                                        memoryPercent
                                        activeJobs
                                    }
                                }
                            }
                        }
                    }
                    """)
                .execute()
                .path("pipeline.latestExecution.runner.internalMetrics.cpuPercent")
                    .entity(Double.class).satisfies(cpu -> assertThat(cpu).isGreaterThan(0))
                .path("pipeline.latestExecution.runner.internalMetrics.activeJobs")
                    .entity(Integer.class).isEqualTo(3);
    }

    @Test
    void testExecutionQuery() {
        graphQlTester.document("""
            query {
                execution(id: "e-2") {
                    id
                    status
                    jobs {
                        id
                        stepId
                        status
                        errorMessage
                    }
                }
            }
            """)
                .execute()
                .path("execution.status").entity(String.class).isEqualTo("FAILED")
                .path("execution.jobs").entityList(Object.class).hasSize(3)
                .path("execution.jobs[1].status").entity(String.class).isEqualTo("FAILED")
                .path("execution.jobs[1].errorMessage").entity(String.class)
                    .matches(msg -> msg.contains("OutOfMemoryError"));
    }
}
