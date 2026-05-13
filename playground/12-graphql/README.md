# Playground 12: GraphQL with DataLoader and Field-Level Auth

**ADR**: [ADR-033 GraphQL API](../../docs/adr/ADR-033-graphql-api.md)

## What You'll Learn

1. **GraphQL Basics**: Schema definition, queries, nested types
2. **DataLoader Batching**: How to avoid N+1 queries with batch loading
3. **Field-Level Authorization**: Restricting access to specific fields based on role
4. **Spring for GraphQL**: Controllers, schema mapping, interceptors
5. **Cursor-Based Pagination**: Relay-style connections

---

## The Problem DataLoader Solves

Consider this query:

```graphql
query {
    pipelines(tenantId: "acme") {
        edges {
            node {
                id
                name
                latestExecution {    # Needs data from Execution Service
                    status
                    runner {         # Needs data from Runner Service
                        hostname
                    }
                }
            }
        }
    }
}
```

**WITHOUT DataLoader:**
```
1. GET /v1/pipelines?tenantId=acme       → returns 10 pipelines
2. GET /v1/executions/latest?pipelineId=p-1
3. GET /v1/executions/latest?pipelineId=p-2
4. GET /v1/executions/latest?pipelineId=p-3
   ... 7 more calls ...
12. GET /v1/runners/r-1
13. GET /v1/runners/r-2
    ... more calls ...

Total: 1 + N + M REST calls (N+1 problem)
```

**WITH DataLoader:**
```
1. GET /v1/pipelines?tenantId=acme              → returns 10 pipelines
2. GET /v1/executions/latest?pipelineIds=p-1,p-2,p-3,...  (BATCH)
3. GET /v1/runners?ids=r-1,r-2                   (BATCH)

Total: 3 REST calls (fixed)
```

---

## Quick Start

### 1. Build and Run

```bash
cd playground/12-graphql
./gradlew bootRun
```

### 2. Open GraphiQL

Navigate to: http://localhost:8080/graphiql

### 3. Run Test Queries

**Simple pipeline query:**
```graphql
query {
    pipeline(id: "p-1") {
        id
        name
        version
        steps {
            id
            name
            stepType
        }
    }
}
```

**Nested query (triggers DataLoader):**
```graphql
query {
    pipeline(id: "p-1") {
        id
        name
        latestExecution {
            status
            startedAt
            runner {
                hostname
                region
            }
        }
    }
}
```

**List query with pagination:**
```graphql
query {
    pipelines(tenantId: "tenant-acme", first: 2) {
        edges {
            node {
                id
                name
                latestExecution {
                    status
                }
            }
            cursor
        }
        pageInfo {
            hasNextPage
        }
        totalCount
    }
}
```

### 4. Test Field-Level Authorization

**As regular USER (default):**
```graphql
query {
    pipeline(id: "p-1") {
        latestExecution {
            runner {
                hostname
                internalMetrics {   # Will be null
                    cpuPercent
                }
            }
        }
    }
}
```

**As ADMIN (add header `X-Role: ADMIN`):**

In GraphiQL, add HTTP headers (bottom left panel):
```json
{
    "X-Role": "ADMIN",
    "X-User-ID": "admin-user"
}
```

Then the same query returns `internalMetrics`.

---

## Code Walkthrough

### 1. Schema Definition

```graphql
# src/main/resources/graphql/schema.graphqls
type Pipeline {
    id: ID!
    name: String!
    steps: [PipelineStep!]!
    latestExecution: Execution     # Resolved via DataLoader
}

type Runner {
    id: ID!
    hostname: String!
    internalMetrics: RunnerMetrics @auth(requires: ADMIN)  # Field-level auth
}
```

### 2. DataLoader Configuration

```java
// DataLoaderConfig.java
@Override
public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
    registry.register("latestExecutionLoader",
        DataLoaderFactory.newMappedDataLoader(this::loadLatestExecutions));
}

// Called ONCE with ALL pipeline IDs, not once per pipeline
private CompletableFuture<Map<String, Execution>> loadLatestExecutions(Set<String> pipelineIds) {
    return CompletableFuture.supplyAsync(() -> 
        dataService.getLatestExecutionsBatch(pipelineIds));
}
```

### 3. Schema Mapping with DataLoader

```java
// PipelineController.java
@SchemaMapping(typeName = "Pipeline", field = "latestExecution")
public CompletableFuture<Execution> latestExecution(
        Pipeline pipeline,
        DataLoader<String, Execution> dataLoader) {  // Injected by Spring
    
    return dataLoader.load(pipeline.id());  // Queued, not executed immediately
}
```

### 4. Field-Level Authorization

```java
// RunnerController.java
@SchemaMapping(typeName = "Runner", field = "internalMetrics")
public RunnerMetrics internalMetrics(Runner runner, DataFetchingEnvironment env) {
    AuthContext authContext = env.getGraphQlContext().get("authContext");
    
    if (!authContext.isAdmin()) {
        return null;  // Non-admins can't see this field
    }
    
    return dataService.getRunnerMetrics(runner.id());
}
```

---

## Tasks

### Task 1: Observe DataLoader Batching

1. Run the app with `./gradlew bootRun`
2. Execute this query:
   ```graphql
   query {
       pipelines(tenantId: "tenant-acme") {
           edges {
               node {
                   id
                   latestExecution { status }
               }
           }
       }
   }
   ```
3. Check the console logs - you should see ONE batch call:
   ```
   REST CALL (BATCH): GET /v1/executions/latest?pipelineIds=[p-1, p-2]
   ```

### Task 2: Add a New Resolver

Add a resolver for `PipelineStep.latestJobStatus`:

1. The `jobStatusLoader` is already configured
2. Add a `@SchemaMapping` method in `PipelineController`
3. Test with:
   ```graphql
   query {
       pipeline(id: "p-1") {
           steps {
               name
               latestJobStatus
           }
       }
   }
   ```

### Task 3: Implement Tenant Isolation

Modify `pipelines()` query to only return pipelines belonging to the authenticated tenant:

1. Extract `tenantId` from `AuthContext`
2. Filter pipelines by the authenticated tenant
3. Test by setting `X-Tenant-ID` header

### Task 4: Add Error Response for Unauthorized Field

Instead of returning `null` for `internalMetrics`, throw a GraphQL error:

```java
throw new AccessDeniedException("Admin role required for internalMetrics");
```

Configure error handling to return a proper GraphQL error response.

---

## Pravah Usage

In production Pravah, GraphQL is used for:

| Use Case | Why GraphQL |
|----------|-------------|
| Pipeline Canvas | One query fetches pipeline + steps + execution + runner |
| Dashboard | Aggregations that would require multiple REST calls |
| Pipeline List | Only fetch fields shown in the table (no over-fetching) |

REST is retained for:
- All mutations (create pipeline, trigger execution, etc.)
- SDK/CLI access (predictable, well-documented)
- Webhooks

---

## Running Tests

```bash
./gradlew test
```

Tests cover:
- Basic queries
- Nested field resolution
- DataLoader batching (verify via log output)
- Field-level authorization (USER vs ADMIN)

---

## Troubleshooting

### GraphiQL Not Loading

Check the app is running on port 8080: `curl http://localhost:8080/actuator/health`

### DataLoader Not Batching

- Ensure resolver returns `CompletableFuture`, not direct value
- Check DataLoader is registered with correct name
- Verify `DataLoader` parameter is correctly typed

### Field Authorization Not Working

- Check `AuthInterceptor` is adding `authContext` to GraphQL context
- Verify header name matches (`X-Role`, `X-User-ID`)
- Check logs for auth context extraction

---

## Cleanup

Stop the application with `Ctrl+C`.
