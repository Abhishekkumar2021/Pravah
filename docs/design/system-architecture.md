# Pravah — Service API & Event Contracts

> This document defines the REST API surface, gRPC protobuf contracts, and Kafka event schemas for all Pravah services. It is the reference for implementation — what the services expose and what they publish. For the architectural rationale behind each decision, see the [ADR index](../adr/README.md). For the complete service map and data flows, see the [High-Level Architecture](../architecture/high-level-architecture.md).

**Last updated:** 2026-05-10

---

## REST API Surface

All REST endpoints are served through the API Gateway. Base URL: `https://api.pravah.io/v1`.

Authentication: `Authorization: Bearer <jwt>` for user requests, `X-API-Key: <key>` for programmatic access.

Tenant context is extracted from the JWT `tenant_id` claim — never from the URL path.

---

### Pipeline Service

```
GET    /v1/pipelines                          List pipelines for the authenticated tenant
POST   /v1/pipelines                          Create a new pipeline definition
GET    /v1/pipelines/{id}                     Get a single pipeline (latest version)
PUT    /v1/pipelines/{id}                     Replace pipeline definition (creates new version)
PATCH  /v1/pipelines/{id}                     Partial update (name, description, trigger config)
DELETE /v1/pipelines/{id}                     Soft-delete pipeline (retains history)

GET    /v1/pipelines/{id}/versions            List all versions of a pipeline
GET    /v1/pipelines/{id}/versions/{version}  Get a specific version

POST   /v1/pipelines/{id}/trigger             Manually trigger a pipeline
POST   /v1/pipelines/{id}/validate            Validate DAG without saving (dry-run)

GET    /v1/pipelines/{id}/triggers            List trigger configurations
POST   /v1/pipelines/{id}/triggers            Add a trigger (cron, Kafka event, webhook)
DELETE /v1/pipelines/{id}/triggers/{tid}      Remove a trigger
```

**Pipeline definition schema (request body):**

```json
{
  "name": "orders-etl",
  "description": "Loads and transforms the orders table from CRM to warehouse",
  "steps": [
    {
      "id": "extract",
      "name": "Extract Orders",
      "stepType": "JDBC_SOURCE",
      "config": {
        "connectorId": "crm-postgres",
        "query": "SELECT * FROM orders WHERE updated_at > :watermark",
        "outputDataset": "pravah://orders/raw"
      },
      "retryPolicy": {
        "maxAttempts": 3,
        "backoffSeconds": [10, 30, 90]
      },
      "timeoutSeconds": 300,
      "piiColumns": [
        { "name": "customer_email", "piiClass": "EMAIL", "masking": "HASH" },
        { "name": "customer_name",  "piiClass": "NAME",  "masking": "REDACT" }
      ]
    },
    {
      "id": "transform",
      "name": "Normalize and Enrich",
      "stepType": "DUCKDB_SQL",
      "dependsOn": ["extract"],
      "config": {
        "sql": "SELECT order_id, customer_email, order_amount, ...",
        "inputDatasets": ["pravah://orders/raw"],
        "outputDataset": "pravah://orders/normalized"
      }
    },
    {
      "id": "load",
      "name": "Load to Warehouse",
      "stepType": "JDBC_SINK",
      "dependsOn": ["transform"],
      "config": {
        "connectorId": "warehouse-postgres",
        "targetTable": "orders_normalized",
        "writeMode": "UPSERT",
        "upsertKey": ["order_id"]
      }
    }
  ]
}
```

**Trigger configuration schema:**

```json
{
  "type": "CRON",
  "cronExpression": "0 2 * * *",
  "timezone": "UTC",
  "concurrencyPolicy": "SKIP"
}
```

```json
{
  "type": "KAFKA_EVENT",
  "topic": "orders.created",
  "filterExpression": "$.tenant_id == 'acme'",
  "debounceSeconds": 30
}
```

```json
{
  "type": "WEBHOOK",
  "secret": "auto-generated",
  "callbackUrl": "https://api.pravah.io/v1/webhooks/{id}"
}
```

---

### Execution Service

```
GET    /v1/executions                          List executions (paginated, filter by pipeline/status)
GET    /v1/executions/{id}                     Get execution detail (status, steps, timing)
POST   /v1/executions/{id}/cancel              Cancel a running execution
GET    /v1/executions/{id}/jobs                List all jobs for an execution
GET    /v1/executions/{id}/jobs/{jobId}/logs   Stream logs for a specific job step (SSE)
```

**Execution response schema:**

```json
{
  "id": "exec-abc-123",
  "pipelineId": "orders-etl",
  "pipelineVersion": 4,
  "status": "RUNNING",
  "triggeredBy": "CRON",
  "triggeredAt": "2026-05-10T02:00:00Z",
  "startedAt": "2026-05-10T02:00:01Z",
  "completedAt": null,
  "jobs": [
    {
      "id": "job-xyz-456",
      "stepId": "extract",
      "status": "SUCCEEDED",
      "runnerId": "runner-001",
      "startedAt": "2026-05-10T02:00:02Z",
      "completedAt": "2026-05-10T02:00:45Z",
      "durationMs": 43000,
      "attemptNumber": 1
    },
    {
      "id": "job-xyz-457",
      "stepId": "transform",
      "status": "RUNNING",
      "runnerId": "runner-001",
      "startedAt": "2026-05-10T02:00:46Z",
      "completedAt": null,
      "durationMs": null,
      "attemptNumber": 1
    }
  ]
}
```

---

### Scheduler Service

```
GET    /v1/backfills                           List backfill requests for a tenant
POST   /v1/backfills                           Create a backfill request
GET    /v1/backfills/{id}                      Get backfill status (progress, failed windows)
POST   /v1/backfills/{id}/cancel              Cancel a running backfill
```

**Backfill request schema:**

```json
{
  "pipelineId": "orders-etl",
  "startDate": "2026-01-01",
  "endDate": "2026-04-30",
  "granularity": "DAY",
  "maxConcurrentRuns": 3,
  "onConflict": "SKIP"
}
```

---

### Runner Service

```
GET    /v1/runners                             List registered runners for the tenant
GET    /v1/runners/{id}                        Get runner details (status, capacity, labels)
PATCH  /v1/runners/{id}                        Update runner labels or capacity
DELETE /v1/runners/{id}                        Deregister a runner
GET    /v1/runners/{id}/jobs                   List jobs currently assigned to this runner
```

**Runner response schema:**

```json
{
  "id": "runner-001",
  "hostname": "worker-01.acme-corp.local",
  "status": "AVAILABLE",
  "region": "eu-west-1",
  "labels": ["production", "high-memory"],
  "capacity": {
    "maxConcurrentJobs": 4,
    "currentJobs": 2,
    "availableSlots": 2
  },
  "lastHeartbeatAt": "2026-05-10T14:32:10Z",
  "registeredAt": "2026-05-01T09:00:00Z",
  "version": "1.4.2"
}
```

---

### Metadata Service

```
GET    /v1/datasets                            Search data catalog (name, tags, schema)
GET    /v1/datasets/{id}                       Get dataset schema and metadata
GET    /v1/datasets/{id}/lineage/upstream      Column-level upstream lineage graph
GET    /v1/datasets/{id}/lineage/downstream    Column-level downstream lineage graph
GET    /v1/datasets/{id}/quality/summary       Recent data quality check results
POST   /v1/datasets/{id}/schema/snapshot       Trigger manual schema snapshot
```

**Lineage response schema:**

```json
{
  "dataset": "pravah://orders/normalized",
  "upstream": [
    {
      "dataset": "pravah://orders/raw",
      "columns": [
        { "source": "order_id",    "target": "order_id" },
        { "source": "order_amount","target": "order_amount" }
      ],
      "transformedBy": "job-xyz-456",
      "pipelineId": "orders-etl",
      "executionId": "exec-abc-123",
      "at": "2026-05-10T02:00:45Z"
    }
  ]
}
```

---

### Connect Service

```
GET    /v1/connectors                          List configured connectors
POST   /v1/connectors                          Create a connector
GET    /v1/connectors/{id}                     Get connector status and config
PATCH  /v1/connectors/{id}                     Update connector config
DELETE /v1/connectors/{id}                     Delete connector
POST   /v1/connectors/{id}/test               Test connector connectivity
GET    /v1/connectors/{id}/schema             Fetch current source schema
POST   /v1/connectors/{id}/cdc/start          Start CDC capture for this source
POST   /v1/connectors/{id}/cdc/stop           Stop CDC capture
```

**Connector configuration schema:**

```json
{
  "name": "crm-postgres",
  "type": "JDBC_POSTGRES",
  "config": {
    "host": "postgres.acme-corp.internal",
    "port": 5432,
    "database": "crm",
    "schema": "public"
  },
  "credentials": {
    "type": "VAULT_PATH",
    "path": "pravah/connectors/acme/crm-postgres/credentials"
  }
}
```

---

### Tenant Service

```
GET    /v1/tenant                              Get current tenant configuration
PATCH  /v1/tenant                             Update tenant settings (quotas, labels, etc.)

GET    /v1/tenant/users                        List users in the tenant
POST   /v1/tenant/users/{userId}/roles        Assign role to user
DELETE /v1/tenant/users/{userId}/roles/{role} Remove role

GET    /v1/tenant/api-keys                     List API keys (last 4 chars only)
POST   /v1/tenant/api-keys                     Create API key (returned once, then hashed)
DELETE /v1/tenant/api-keys/{id}               Revoke API key

GET    /v1/tenant/sso                          Get SSO configuration
PUT    /v1/tenant/sso                          Configure SSO (OIDC or SAML)
DELETE /v1/tenant/sso                          Remove SSO (revert to email/password)
```

---

### Notification Service

```
GET    /v1/notifications/configs               List notification configurations
POST   /v1/notifications/configs               Create notification rule
PATCH  /v1/notifications/configs/{id}          Update rule
DELETE /v1/notifications/configs/{id}          Remove rule
GET    /v1/notifications/log                   Delivery history (paginated)
```

**Notification rule schema:**

```json
{
  "name": "Pipeline Failure Alert",
  "trigger": "EXECUTION_FAILED",
  "filter": {
    "pipelineIds": ["orders-etl", "inventory-sync"],
    "severities": ["HIGH", "CRITICAL"]
  },
  "channels": [
    { "type": "SLACK",      "webhookUrl": "https://hooks.slack.com/..." },
    { "type": "PAGERDUTY",  "routingKey": "abc123" },
    { "type": "EMAIL",      "recipients": ["oncall@acme-corp.com"] }
  ],
  "deduplicationWindowMinutes": 15,
  "maxAlertsPerHour": 10
}
```

---

### Billing Service

```
GET    /v1/billing/usage                       Current month usage summary
GET    /v1/billing/usage?month=2026-04         Historical month usage
GET    /v1/billing/invoices                    List invoices
GET    /v1/billing/invoices/{id}               Invoice detail (line items)
GET    /v1/billing/quotas                      Current quota limits and consumption
```

**Usage summary schema:**

```json
{
  "tenantId": "acme-corp",
  "period": "2026-05",
  "totalJobMinutes": 18420,
  "totalJobCount": 94300,
  "byPipeline": [
    {
      "pipelineId": "orders-etl",
      "jobMinutes": 12300,
      "jobCount": 62000
    }
  ],
  "quotaUsage": {
    "jobMinutesLimit": 50000,
    "jobMinutesUsed": 18420,
    "percentUsed": 36.8
  }
}
```

---

### Privacy (Right to Erasure)

```
POST   /v1/privacy/erasure-requests           Submit a data subject erasure request
GET    /v1/privacy/erasure-requests           List erasure requests and their status
GET    /v1/privacy/erasure-requests/{id}      Get erasure request detail and completion proof
```

---

## GraphQL Schema (UI Queries)

The GraphQL endpoint (`POST /graphql`) is read-only — all writes go through REST. The schema below covers the primary UI view queries.

```graphql
type Query {
  pipeline(id: ID!): Pipeline
  pipelines(filter: PipelineFilter, page: PageInput): PipelineConnection
  execution(id: ID!): Execution
  executions(pipelineId: ID!, filter: ExecutionFilter): ExecutionConnection
  runner(id: ID!): Runner
  runnerFleet: [Runner!]!
  dataset(id: ID!): Dataset
  datasets(search: String, page: PageInput): DatasetConnection
}

type Pipeline {
  id: ID!
  name: String!
  description: String
  version: Int!
  steps: [PipelineStep!]!
  triggers: [TriggerConfig!]!
  latestExecution: Execution
  executions(limit: Int = 10): [Execution!]!
  lineageUpstream: [DatasetEdge!]!
  createdAt: DateTime!
  updatedAt: DateTime!
}

type PipelineStep {
  id: ID!
  name: String!
  stepType: StepType!
  dependsOn: [String!]!
  retryPolicy: RetryPolicy
  timeoutSeconds: Int
  latestJobStatus: JobStatus
  piiColumns: [PiiColumnConfig!]!
}

type Execution {
  id: ID!
  status: ExecutionStatus!
  triggeredBy: TriggerType!
  triggeredAt: DateTime!
  startedAt: DateTime
  completedAt: DateTime
  durationMs: Int
  jobs: [Job!]!
}

type Job {
  id: ID!
  stepId: String!
  status: JobStatus!
  runner: Runner
  startedAt: DateTime
  completedAt: DateTime
  durationMs: Int
  attemptNumber: Int!
  errorMessage: String
}

type Runner {
  id: ID!
  hostname: String!
  status: RunnerStatus!
  region: String!
  labels: [String!]!
  availableSlots: Int!
  lastHeartbeatAt: DateTime!
}

type Dataset {
  id: ID!
  name: String!
  schema: [ColumnDef!]!
  piiColumns: [String!]!
  lineageUpstream: [DatasetEdge!]!
  lineageDownstream: [DatasetEdge!]!
  qualitySummary: QualitySummary
}

enum ExecutionStatus { INITIALIZING RUNNING SUCCEEDED FAILED CANCELLED }
enum JobStatus       { PENDING ASSIGNED RUNNING SUCCEEDED FAILED CANCELLED }
enum RunnerStatus    { AVAILABLE BUSY SUSPECT DEAD DEREGISTERED }
enum StepType        { JDBC_SOURCE JDBC_SINK DUCKDB_SQL KAFKA_SOURCE KAFKA_SINK PYTHON_SCRIPT REST_API }
enum TriggerType     { CRON KAFKA_EVENT WEBHOOK MANUAL BACKFILL }
```

---

## gRPC Service Definitions

### RunnerGateway (Runner ↔ Runner Service)

```protobuf
syntax = "proto3";
package pravah.runner.v1;

// Runner opens this bidirectional stream on startup. Stream stays open for the
// lifetime of the runner connection. The runner sends messages; the cloud sends
// assignments and control messages back.
service RunnerGateway {
  rpc Connect(stream RunnerMessage) returns (stream ControlMessage);
}

message RunnerMessage {
  string runner_id = 1;
  oneof payload {
    RegisterRequest  register   = 2;
    Heartbeat        heartbeat  = 3;
    JobStatusUpdate  job_update = 4;
    JobResult        job_result = 5;
    LogChunk         log_chunk  = 6;
    LineageEvent     lineage    = 7;
  }
}

message RegisterRequest {
  string hostname        = 1;
  string version         = 2;
  string region          = 3;
  repeated string labels = 4;
  int32  max_concurrent  = 5;
}

message Heartbeat {
  int32 active_job_count = 1;
  int32 available_slots  = 2;
  int64 timestamp_ms     = 3;
}

message JobStatusUpdate {
  string job_id          = 1;
  string execution_id    = 2;
  string status          = 3;   // RUNNING, CHECKPOINT
  int64  progress_pct    = 4;
  bytes  checkpoint_data = 5;   // for durable checkpointing (ADR-024)
}

message JobResult {
  string job_id        = 1;
  string execution_id  = 2;
  bool   success       = 3;
  string error_message = 4;
  int64  duration_ms   = 5;
  int64  rows_processed = 6;
}

message LogChunk {
  string job_id    = 1;
  int64  timestamp = 2;
  string level     = 3;
  string message   = 4;
  string trace_id  = 5;
}

message LineageEvent {
  string job_id          = 1;
  string execution_id    = 2;
  string openlineage_json = 3;   // serialized OpenLineage RunEvent
}

// ──────────────────────────────────────────────────────────────

message ControlMessage {
  oneof payload {
    RegisterAck   ack        = 1;
    JobAssignment assignment = 2;
    CancelJob     cancel     = 3;
    ConfigUpdate  config     = 4;
  }
}

message RegisterAck {
  string runner_id         = 1;
  bool   accepted          = 2;
  string reject_reason     = 3;
  int64  server_timestamp  = 4;
}

message JobAssignment {
  string job_id          = 1;
  string execution_id    = 2;
  string pipeline_id     = 3;
  int32  pipeline_version = 4;
  string step_id         = 5;
  string step_config_json = 6;   // step config (connector, SQL, etc.)
  int64  fencing_token   = 7;    // for deduplication on re-assignment
  int32  timeout_seconds = 8;
  bytes  checkpoint_data = 9;    // resume from checkpoint if retrying
}

message CancelJob {
  string job_id   = 1;
  string reason   = 2;
}

message ConfigUpdate {
  map<string, string> config = 1;
}
```

---

### AgentService (internal, consumed by other services)

```protobuf
syntax = "proto3";
package pravah.agent.v1;

service AgentService {
  // Trigger the agent to triage a pipeline failure
  rpc TriageFailure(TriageRequest) returns (TriageResponse);
  // Get the status of an ongoing triage
  rpc GetTriageStatus(TriageStatusRequest) returns (TriageStatusResponse);
}

message TriageRequest {
  string execution_id  = 1;
  string pipeline_id   = 2;
  string failure_cause = 3;   // SCHEMA_DRIFT, TIMEOUT, TRANSIENT, UNKNOWN
  string tenant_id     = 4;
}

message TriageResponse {
  string triage_id   = 1;
  string status      = 2;   // STARTED, ALREADY_IN_PROGRESS
}

message TriageStatusRequest {
  string triage_id = 1;
}

message TriageStatusResponse {
  string triage_id     = 1;
  string status        = 2;   // IN_PROGRESS, COMPLETED, FAILED
  string conclusion    = 3;   // human-readable triage result
  string proposed_fix  = 4;   // JSON patch for the pipeline definition, if applicable
  bool   auto_applied  = 5;
  bool   requires_approval = 6;
}
```

---

## Kafka Event Schemas

All events are serialized using **Avro** with the Confluent Schema Registry. The schemas below use JSON notation for readability.

### `pravah.pipeline.triggered`

```json
{
  "namespace": "io.pravah.events",
  "type": "record",
  "name": "PipelineTriggered",
  "fields": [
    { "name": "event_id",        "type": "string" },
    { "name": "pipeline_id",     "type": "string" },
    { "name": "pipeline_version","type": "int" },
    { "name": "tenant_id",       "type": "string" },
    { "name": "fencing_token",   "type": "long" },
    { "name": "trigger_type",    "type": "string" },
    { "name": "triggered_at",    "type": "long",   "doc": "epoch ms" },
    { "name": "parameters",      "type": { "type": "map", "values": "string" }, "default": {} }
  ]
}
```

### `pravah.job.created`

```json
{
  "name": "JobCreated",
  "fields": [
    { "name": "event_id",       "type": "string" },
    { "name": "job_id",         "type": "string" },
    { "name": "execution_id",   "type": "string" },
    { "name": "pipeline_id",    "type": "string" },
    { "name": "step_id",        "type": "string" },
    { "name": "tenant_id",      "type": "string" },
    { "name": "priority",       "type": "int",    "default": 5 },
    { "name": "required_labels","type": { "type": "array", "items": "string" }, "default": [] },
    { "name": "pii_restricted_regions", "type": { "type": "array", "items": "string" }, "default": [] },
    { "name": "created_at",     "type": "long" }
  ]
}
```

### `pravah.job.completed`

```json
{
  "name": "JobCompleted",
  "fields": [
    { "name": "event_id",        "type": "string" },
    { "name": "job_id",          "type": "string" },
    { "name": "execution_id",    "type": "string" },
    { "name": "tenant_id",       "type": "string" },
    { "name": "step_id",         "type": "string" },
    { "name": "status",          "type": "string" },
    { "name": "runner_id",       "type": "string" },
    { "name": "duration_ms",     "type": "long" },
    { "name": "rows_processed",  "type": "long",   "default": 0 },
    { "name": "cpu_seconds",     "type": "double", "default": 0.0 },
    { "name": "memory_peak_mb",  "type": "int",    "default": 0 },
    { "name": "error_message",   "type": ["null", "string"], "default": null },
    { "name": "completed_at",    "type": "long" }
  ]
}
```

### `pravah.execution.failed`

```json
{
  "name": "ExecutionFailed",
  "fields": [
    { "name": "event_id",      "type": "string" },
    { "name": "execution_id",  "type": "string" },
    { "name": "pipeline_id",   "type": "string" },
    { "name": "tenant_id",     "type": "string" },
    { "name": "failed_step_id","type": ["null", "string"] },
    { "name": "failure_cause", "type": "string" },
    { "name": "error_message", "type": "string" },
    { "name": "failed_at",     "type": "long" }
  ]
}
```

### `pravah.schema.drift.detected`

```json
{
  "name": "SchemaDriftDetected",
  "fields": [
    { "name": "event_id",       "type": "string" },
    { "name": "dataset_id",     "type": "string" },
    { "name": "pipeline_id",    "type": "string" },
    { "name": "execution_id",   "type": "string" },
    { "name": "tenant_id",      "type": "string" },
    { "name": "drift_type",     "type": "string", "doc": "COLUMN_ADDED | COLUMN_REMOVED | TYPE_CHANGED" },
    { "name": "affected_columns","type": { "type": "array", "items": {
        "type": "record",
        "name": "ColumnDrift",
        "fields": [
          { "name": "column_name",    "type": "string" },
          { "name": "drift_type",     "type": "string" },
          { "name": "previous_type",  "type": ["null", "string"] },
          { "name": "current_type",   "type": ["null", "string"] }
        ]
      }}
    },
    { "name": "detected_at", "type": "long" }
  ]
}
```

### `pravah.data.quality.alert`

```json
{
  "name": "DataQualityAlert",
  "fields": [
    { "name": "event_id",     "type": "string" },
    { "name": "dataset_id",   "type": "string" },
    { "name": "pipeline_id",  "type": "string" },
    { "name": "execution_id", "type": "string" },
    { "name": "tenant_id",    "type": "string" },
    { "name": "rule_id",      "type": "string" },
    { "name": "rule_name",    "type": "string" },
    { "name": "severity",     "type": "string", "doc": "LOW | MEDIUM | HIGH | CRITICAL" },
    { "name": "metric_value", "type": "double" },
    { "name": "threshold",    "type": "double" },
    { "name": "alerted_at",   "type": "long" }
  ]
}
```

---

## Database Schema Summaries

### Pipeline Service (`pipeline_db`)

```sql
-- Core pipeline entity
CREATE TABLE pipelines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    latest_version  INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

-- Immutable version snapshots
CREATE TABLE pipeline_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    tenant_id       VARCHAR(255) NOT NULL,
    version         INT  NOT NULL,
    dag_spec        JSONB NOT NULL,           -- full step definitions, edges, config
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    UNIQUE (pipeline_id, version)
);

-- Outbox for reliable event publishing
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    partition_key   VARCHAR(255),
    payload         JSONB        NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox_events (status, created_at)
    WHERE status = 'PENDING';
```

### Execution Service (`execution_db`)

```sql
-- One row per pipeline trigger
CREATE TABLE executions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    pipeline_id      UUID         NOT NULL,
    pipeline_version INT          NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    triggered_by     VARCHAR(50)  NOT NULL,
    triggered_at     TIMESTAMPTZ  NOT NULL,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    fencing_token    BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE TABLE executions_2026_05 PARTITION OF executions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- One row per step per execution
CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    execution_id    UUID         NOT NULL,
    step_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    runner_id       UUID,
    attempt_number  INT          NOT NULL DEFAULT 1,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    duration_ms     BIGINT,
    rows_processed  BIGINT,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Hot path: find active jobs to monitor
CREATE INDEX idx_jobs_active ON jobs (tenant_id, status, created_at DESC)
    WHERE status IN ('PENDING', 'ASSIGNED', 'RUNNING');

-- Heartbeat timeout: find running jobs whose runner went dead
CREATE INDEX idx_jobs_runner ON jobs (runner_id, status)
    WHERE status = 'RUNNING';
```

---

## Runner Binary — Local Startup Sequence

```
1. Runner binary starts (JAR or Docker container in customer environment)

2. Load configuration from environment / config file:
   - PRAVAH_CLOUD_HOST (e.g. runners.pravah.io:443)
   - PRAVAH_RUNNER_ID  (optional, generated if absent)
   - MAX_CONCURRENT_JOBS (default: 4)
   - LABELS (comma-separated: "production,high-memory")
   - VAULT_ADDR (optional, for dynamic connector credentials)

3. Load mTLS certificate from local path (issued by Vault PKI, 7-day validity)
   If certificate is expired or absent: request new cert from Vault
   using the runner's bootstrap token (one-time-use)

4. Establish gRPC bidirectional stream to Runner Service:
   RunnerGateway.Connect() with mTLS client cert

5. Send RegisterRequest:
   { hostname, version, region, labels, max_concurrent }

6. Await RegisterAck. On accepted=true → runner enters event loop.

7. Event loop:
   - Every 10s: send Heartbeat with active_job_count and available_slots
   - On JobAssignment received: spawn worker thread
   - Worker: load step config → acquire connector credentials from Vault
             → execute step (DuckDB SQL or connector SDK)
             → emit LogChunks (streaming)
             → emit LineageEvent after step completes
             → send JobResult

8. On SIGTERM: complete in-flight jobs, send JobResult, close gRPC stream gracefully.
   New jobs are not accepted after SIGTERM.
```
