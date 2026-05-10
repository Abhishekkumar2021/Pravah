# ADR-033: GraphQL API alongside REST

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah exposes REST APIs for all operations. REST works well for simple CRUD — `GET /v1/pipelines/{id}`, `POST /v1/executions` — but the Pravah UI has requirements that REST handles awkwardly:

**The N+1 problem in the pipeline DAG view:**

The pipeline canvas page needs to display:
- The pipeline definition (DAG structure, step configs)
- The latest execution status for each step
- Upstream lineage for each input dataset
- The runner handling the current execution

With REST this requires 4 separate API calls, each with its own network round-trip. The frontend assembles the response from 4 calls. As the UI adds more widgets, the number of calls grows proportionally.

**Over-fetching for list views:**

The pipeline list page shows: pipeline name, last run status, last run time, step count. `GET /v1/pipelines` returns the full pipeline definition including every step's configuration, DAG edges, PII declarations, and version history. The frontend discards ~95% of the response payload for this view.

**Deeply nested dashboard queries:**

The observability dashboard query: "For tenant X, show all pipelines that have had more than 3 failures in the last 24 hours, including the specific failing step and its error message." With REST, this requires:
1. `GET /v1/executions?status=FAILED&window=24h` — paginated
2. For each failed execution, `GET /v1/executions/{id}/steps` to find the failing step
3. The client aggregates by `pipeline_id`, counts failures, and filters > 3

This is client-side computation that would be trivial server-side.

**The underlying cause:** REST resources are designed around domain entities. The UI is designed around views. The impedance mismatch between entity-oriented REST and view-oriented UIs grows as the UI matures.

GraphQL solves this class of problem: the client describes exactly what data it needs in a single query, and the server fetches and assembles it. The API surface is one endpoint; flexibility lives in the schema.

---

## Decision

**GraphQL for UI-facing queries, REST retained for all mutations and programmatic API access.**

This is not "migrate REST to GraphQL." It is adding GraphQL as a read-optimized query layer that internally delegates to the existing REST/gRPC services. REST remains the authoritative API for all write operations and for programmatic/SDK access.

**Architecture:**

```
Browser / UI
    │ GraphQL query (POST /graphql)
    ▼
API Gateway
    │ Routes to GraphQL Service
    ▼
GraphQL Service (Spring Boot + Spring for GraphQL)
    │ DataFetchers call downstream services
    ├── Pipeline Service REST: GET /v1/pipelines/{id}
    ├── Execution Service REST: GET /v1/executions/latest?pipelineId=X
    ├── Metadata Service REST: GET /v1/lineage/upstream?datasetId=Y
    └── Runner Service REST: GET /v1/runners/{id}
    ▼
Assembled response returned to UI in a single round-trip
```

The GraphQL Service is a thin composition layer. It does not have its own database. It translates GraphQL queries into calls to the existing REST services and assembles the result. DataLoader batching prevents N+1 calls to downstream services.

**Schema excerpt:**

```graphql
type Query {
  pipeline(id: ID!): Pipeline
  pipelines(tenantId: ID!, filter: PipelineFilter, page: PageInput): PipelineConnection
  execution(id: ID!): Execution
  executions(pipelineId: ID!, filter: ExecutionFilter): ExecutionConnection
  runnerFleet(tenantId: ID!): [Runner!]!
}

type Pipeline {
  id: ID!
  name: String!
  description: String
  version: Int!
  steps: [PipelineStep!]!
  latestExecution: Execution           # joined from Execution Service
  lineageUpstream: [Dataset!]          # joined from Metadata Service
  createdAt: DateTime!
  updatedAt: DateTime!
}

type PipelineStep {
  id: ID!
  name: String!
  stepType: StepType!
  dependsOn: [String!]!
  latestJobStatus: JobStatus           # joined from Execution Service
  piiColumns: [PiiColumnConfig!]!
}

type Execution {
  id: ID!
  status: ExecutionStatus!
  triggeredBy: TriggerType!
  startedAt: DateTime
  completedAt: DateTime
  jobs: [Job!]!
  runner: Runner                       # joined from Runner Service
}
```

**UI queries the exact shape it needs:**

```graphql
# Pipeline canvas — single round-trip for all panel data
query PipelineCanvas($pipelineId: ID!) {
  pipeline(id: $pipelineId) {
    id
    name
    version
    steps {
      id
      name
      stepType
      dependsOn
      latestJobStatus
    }
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

**REST is retained for:**
- All mutations: `POST /v1/pipelines`, `PATCH /v1/pipelines/{id}`, `POST /v1/executions/{id}/cancel`
- SDK/programmatic access: Terraform Provider, CI/CD integrations, API clients use REST — predictable, well-understood semantics
- Webhook payloads (always REST callbacks)
- Health and metrics endpoints

**Authentication:**
GraphQL queries go through the API Gateway with the same JWT validation as REST. The `tenant_id` from the JWT is forwarded to all downstream service calls — GraphQL does not bypass any tenancy controls.

**DataLoader batching:**

Without batching, a query for 20 pipelines' latest executions would trigger 20 separate calls to the Execution Service (N+1). DataLoader groups these into a single batched call:

```java
@Component
public class LatestExecutionDataLoader implements BatchLoaderWithContext<String, Execution> {
    @Override
    public CompletionStage<List<Execution>> load(List<String> pipelineIds, BatchLoaderEnvironment env) {
        // One call to Execution Service with all pipeline IDs
        return executionServiceClient.getLatestExecutions(pipelineIds);
    }
}
```

---

## Consequences

### Positive

- **Eliminates N+1 round-trips from the UI**: complex views that previously required 4–8 REST calls now complete in a single query with a single network round-trip.
- **Eliminates over-fetching**: the pipeline list view requests only `id, name, latestExecution.status, updatedAt` — not the full 50KB pipeline definition.
- **UI can evolve without backend changes**: adding a new field to a UI panel requires the field to exist in the schema, but does not require a new REST endpoint or a change to an existing endpoint's response shape.
- **Introspection**: the GraphQL schema is self-documenting. Frontend engineers can explore available types and fields in GraphiQL without reading API docs.
- **No REST API breakage**: existing REST clients (SDK, Terraform Provider, CI integrations) are completely unaffected.

### Negative

- **Two API surfaces to maintain**: schema changes in the GraphQL layer must stay in sync with the underlying REST services. A REST endpoint that removes a field requires a corresponding change in the GraphQL DataFetcher.
- **Caching complexity**: REST responses are cacheable at the HTTP layer (CDN, `Cache-Control` headers). GraphQL queries are POST requests — not cacheable by default. Persisted queries (pre-registered queries with a hash) enable CDN caching for known query shapes.
- **Observability of GraphQL**: a single `/graphql` endpoint handling all queries makes it harder to monitor per-operation latency and error rates. Apollo-style operation naming (`query PipelineCanvas`) must be enforced to enable per-operation metrics.
- **Authorization complexity**: REST endpoints can enforce authorization at the route level. GraphQL field-level authorization (ensuring a user without runner admin access can't query `runner.internalMetrics`) must be enforced in each DataFetcher.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| GraphQL query complexity attack (deeply nested query causing 100+ downstream calls) | Query depth limit (max 10 levels) and complexity limit enforced at the GraphQL layer; malicious queries rejected before evaluation |
| Schema introspection exposes internal field names | Disable introspection in production for non-authenticated requests; enable for authenticated developers |
| DataFetcher bypasses tenant scoping | All DataFetchers receive the `AuthContext` (tenant_id from JWT); downstream REST calls include `X-Tenant-ID` header — enforced in a base DataFetcher class |
| Mutation leakage into GraphQL | Mutations explicitly excluded from the GraphQL schema; all write operations remain REST-only |

---

## Alternatives Considered

### GraphQL for All Operations (Mutations Included)

Use GraphQL exclusively, including for all write operations, and deprecate REST.

Rejected because:
- Programmatic API clients (Terraform Provider, Python SDK, CI/CD integrations) work naturally with REST. Migrating them to GraphQL adds complexity with no benefit for machine-to-machine use cases.
- REST mutation semantics (HTTP verbs, status codes, `Location` headers) are well-understood and interoperable. GraphQL mutations return 200 for both success and application-level errors, complicating error handling in scripts.
- A complete REST-to-GraphQL migration is a multi-month effort with high risk of breaking existing integrations.

### REST with Sparse Fieldsets (JSON:API)

Adopt JSON:API specification, which supports `?fields[pipeline]=id,name,status` to return only requested fields.

Considered but not chosen because:
- Sparse fieldsets solve over-fetching but not the N+1 / relationship traversal problem. Fetching `pipeline + latestExecution + runner` still requires 3 requests.
- JSON:API is a niche specification. Library support in Spring Boot is limited compared to GraphQL.
- Client implementation complexity is similar to GraphQL but with less ecosystem tooling.

### Backend-for-Frontend (BFF) REST Endpoints

Create UI-specific REST endpoints that assemble composite responses: `GET /v1/ui/pipeline-canvas/{id}` returns the pipeline, its latest execution, and the runner in one call.

Rejected because:
- BFF endpoints multiply as the UI grows. Every new UI view potentially requires a new BFF endpoint.
- BFF endpoints couple the backend to specific UI layouts — changing the layout requires a backend change.
- GraphQL provides the same composition capability but in a declarative, schema-driven way that the UI controls.
