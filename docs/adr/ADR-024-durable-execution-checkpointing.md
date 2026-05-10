# ADR-024: Durable Execution & Checkpointing

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah executes long-running pipelines. A data warehouse load pipeline might run for 4 hours: extract 500GB from a source database, transform it across multiple steps, and load it into Snowflake. If the runner process crashes at hour 3, what happens?

Without durable execution, the answer is: the entire pipeline re-runs from the beginning. This means:
- 3 hours of wasted compute
- Re-reading 500GB from the source (potentially stale data, or source load)
- Re-loading data that was already written (duplicate detection required)
- Violating exactly-once semantics at the destination

**The problem:** distributed systems fail. Runners crash. Kubernetes kills pods during node maintenance. Network partitions drop connections. Any system that cannot survive these failures requires the entire operation to be atomic — which is only practical for very short operations.

**Durable execution** means a pipeline can survive any single-process or single-node failure and resume from the last successfully completed step, not from the beginning.

This is different from step-level retry (ADR-018): retry restarts the same step. Durable execution persists the progress of the DAG so that when a new runner picks up the work, it starts from step N+1, not step 1.

---

## Decision

Pravah implements durable execution through three complementary mechanisms:

1. **Step-level checkpointing**: each completed step's output is persisted to MinIO (ADR-021) before the next step starts. A new runner can resume from any completed step.
2. **Idempotency keys at every layer**: each step execution is identified by `(execution_id, step_id, attempt_number)`. Retrying a step that already completed returns the previous result without re-executing.
3. **Execution state in PostgreSQL**: the Execution Service persists the DAG state (which steps are SUCCEEDED, RUNNING, WAITING) in `execution_db`. This state survives runner crashes — the DAG progress is in the control plane, not in the runner's memory.

**How a runner crash is handled:**

```
Timeline:
  t=0:00  Execution starts: extract → validate → transform → load_dw → load_s3
  t=0:30  extract completes → output Parquet written to MinIO → step marked SUCCEEDED
  t=1:00  validate completes → output written to MinIO → step marked SUCCEEDED
  t=2:00  transform completes → output written to MinIO → step marked SUCCEEDED
  t=2:30  load_dw starts on Runner-7 → Runner-7 crashes at t=2:45

Control plane detects Runner-7 missed heartbeat:
  t=3:00  Runner-7 marked DISCONNECTED
  t=3:00  load_dw job status: RUNNING → INTERRUPTED
  t=3:00  Execution Service evaluates DAG: load_dw is INTERRUPTED, all inputs are SUCCEEDED
  t=3:00  load_dw re-queued with attempt_number=2
  t=3:05  Runner-12 picks up load_dw (attempt 2)
  t=3:05  Runner-12 downloads output from transform step from MinIO (idempotency: data already there)
  t=3:05  Runner-12 executes load_dw (idempotency: MERGE into Snowflake, not INSERT)
  t=4:00  load_dw attempt 2 succeeds → execution continues
```

The pipeline did not restart from extract. It resumed from load_dw.

**Checkpoint structure in MinIO:**

```
s3://pravah-artifacts/tenants/{tenant}/executions/{exec_id}/
├── steps/
│   ├── extract/
│   │   ├── output/data.parquet      ← step output (persistent checkpoint)
│   │   ├── profile/profile.json     ← row count, schema
│   │   └── .completed               ← sentinel file: step is done
│   ├── validate/
│   │   ├── output/data.parquet
│   │   └── .completed
│   └── transform/
│       ├── output/data.parquet
│       └── .completed
└── manifest.json                    ← links all steps to their outputs
```

The `.completed` sentinel file is written atomically after the Parquet output is fully uploaded. A runner that starts a step first checks: does `.completed` exist for this step? If yes, the step's output is available — skip re-execution and return the existing output location.

**Idempotency at the destination:**

For database destinations, re-running a step that partially succeeded must not produce duplicate rows:

```sql
-- Load step uses MERGE (upsert), not INSERT
-- Idempotency key: (execution_id, step_id, row_id)
MERGE INTO snowflake.dw_orders AS target
USING (SELECT * FROM staging_table) AS source
ON target.order_id = source.order_id
   AND target._pravah_execution_id = source._pravah_execution_id
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...;
```

The `_pravah_execution_id` column in destination tables enables exactly-once guarantees even when a step is retried.

**Flink checkpointing for streaming steps:**

Streaming steps (those using Flink) use Flink's native checkpointing mechanism:

```java
env.enableCheckpointing(60_000);  // checkpoint every 60 seconds
env.getCheckpointConfig().setCheckpointStorage(
    new FileSystemCheckpointStorage("s3://pravah-artifacts/flink-checkpoints/" + jobId)
);
env.getCheckpointConfig().setExternalizedCheckpointCleanup(
    ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
);
```

If a Flink job fails, it restores from the last checkpoint on MinIO and resumes processing from the last committed offset — no data is reprocessed beyond the checkpoint interval.

**Execution state machine in PostgreSQL:**

```sql
-- jobs table — persists DAG state across runner failures
CREATE TABLE jobs (
    id              UUID PRIMARY KEY,
    execution_id    UUID NOT NULL,
    step_id         VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL,   -- WAITING/READY/RUNNING/INTERRUPTED/SUCCEEDED/FAILED/CANCELLED
    attempt_number  INT NOT NULL DEFAULT 1,
    runner_id       UUID,                   -- which runner is currently executing this
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    checkpoint_path VARCHAR(1024),          -- MinIO path of the step's output checkpoint
    idempotency_key VARCHAR(512) NOT NULL   -- execution_id:step_id:attempt_number
);
```

The Execution Service's heartbeat monitor queries:
```sql
SELECT id, runner_id FROM jobs
WHERE status = 'RUNNING'
  AND runner_id IN (SELECT id FROM runners WHERE last_heartbeat < NOW() - INTERVAL '45 seconds');
-- These jobs' runners are dead — transition to INTERRUPTED and re-queue
```

---

## Consequences

### Positive

- **No lost work on runner failure**: a 4-hour pipeline that crashes at hour 3 resumes from hour 3, not hour 0. Each step's checkpoint is durable in MinIO.
- **Exactly-once at the destination**: idempotency keys and MERGE semantics ensure that re-running a step does not produce duplicate data in destination systems.
- **Transparent to pipeline authors**: durable execution is built into the platform. Pipeline authors write SQL transforms; the platform handles checkpointing, idempotency, and recovery automatically.
- **Flink streaming steps are also durable**: Flink's native checkpointing to MinIO provides durable streaming with bounded reprocessing.

### Negative

- **Checkpoint overhead**: writing a Parquet checkpoint to MinIO after each step adds latency. For a 10-step pipeline, each checkpoint write might take 5–30 seconds depending on data volume. For pipelines with many short steps, this overhead is proportionally large.
- **MinIO dependency on the critical path**: if MinIO is unavailable when a step tries to write its checkpoint, the step fails. MinIO must be highly available.
- **Destination idempotency is the developer's responsibility for custom connectors**: Pravah provides idempotency for built-in connectors (JDBC with MERGE, Snowflake COPY, Parquet to MinIO). Custom connector developers must implement idempotency themselves.
- **Checkpoint storage grows with execution count**: old checkpoints must be cleaned up. Lifecycle policies (ADR-021) handle this — step checkpoints expire after 30 days.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| MinIO unavailable during checkpoint write | Step fails; retry (attempt_number+1) tries again; MinIO HA (erasure coding, 4 nodes) minimizes unavailability |
| `.completed` sentinel and Parquet output are not atomic | Sentinel is written AFTER Parquet upload completes (checked via etag verification); partial uploads do not produce sentinel files |
| Flink checkpoint storage grows unboundedly | Checkpoint retention: keep last 3 checkpoints; older ones auto-deleted by Flink |
| Runner picks up a job that a live runner is still executing | Fencing tokens (runner registration) prevent a demoted runner's results from being accepted after another runner has been assigned the same job |

---

## Alternatives Considered

### Re-run from Beginning on Any Failure

Simpler to implement — no checkpoint infrastructure. If a step fails, start the whole pipeline over.

Rejected because:
- For long-running pipelines, this wastes hours of compute on already-completed steps
- It may be impossible to re-read from sources if the data window has advanced (streaming sources, rate-limited APIs)
- Destination systems must handle full re-loads rather than incremental upserts

### Temporal Workflow Engine for Durable Execution

Use Temporal (a workflow engine designed for durable execution) to manage pipeline step orchestration. Temporal natively handles process crashes by persisting workflow state in its own database.

Seriously considered. Rejected because:
- Temporal is a separate service with its own operational footprint (Temporal server + database + SDK). Adding Temporal means two orchestration systems (Pravah's Execution Service + Temporal) — too much overlap.
- Temporal's model (workflow code re-executing deterministically) requires pipeline logic to be written in Temporal's SDK (Go, Java, Python). This is a significant constraint on how pipelines are defined.
- Pravah's combination of DAG state in PostgreSQL + MinIO checkpoints achieves the same durability guarantee with existing infrastructure.
