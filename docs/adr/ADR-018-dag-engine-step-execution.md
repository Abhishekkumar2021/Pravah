# ADR-018: DAG Engine & Step Execution Model

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's core value proposition is pipeline orchestration. A pipeline is not a linear sequence of steps — it is a **Directed Acyclic Graph (DAG)** where:

- Some steps can run in parallel (no dependency between them)
- Some steps must wait for one or more upstream steps to complete
- Some steps branch conditionally based on upstream results
- Some steps fan out into N parallel sub-tasks and fan back in

The Execution Service must answer these questions at runtime:

1. **Which steps can start right now?** (no unmet dependencies)
2. **When a step completes, which new steps become eligible?**
3. **If a step fails, which downstream steps should be cancelled?**
4. **How are concurrency limits respected?** (max N parallel steps per pipeline)
5. **How are step-level and pipeline-level timeouts enforced?**
6. **What does exactly-once mean for a step that is retried?**

None of these questions have answers in the existing architecture. The Execution Service's job state machine (ADR-011 Saga, ADR-004 Outbox) covers individual job lifecycle but not the DAG traversal logic that determines which jobs to create and when.

---

## Decision

The Execution Service implements a **DAG execution engine** that drives pipeline execution through topological ordering, dependency tracking, and event-driven step activation.

**Pipeline structure (stored by Pipeline Service):**

```json
{
  "pipeline_id": "pipeline-abc",
  "steps": [
    { "step_id": "extract",    "type": "JDBC_SOURCE",    "depends_on": [] },
    { "step_id": "validate",   "type": "QUALITY_CHECK",  "depends_on": ["extract"] },
    { "step_id": "transform",  "type": "DUCKDB_TRANSFORM","depends_on": ["validate"] },
    { "step_id": "load_dw",    "type": "SNOWFLAKE_SINK", "depends_on": ["transform"] },
    { "step_id": "load_s3",    "type": "S3_SINK",        "depends_on": ["transform"] },
    { "step_id": "notify",     "type": "NOTIFICATION",   "depends_on": ["load_dw", "load_s3"] }
  ],
  "concurrency_limit": 3,
  "timeout_seconds": 3600
}
```

The DAG for this pipeline:

```
extract → validate → transform ──┬──→ load_dw ──┐
                                  │               ├──→ notify
                                  └──→ load_s3 ──┘
```

`transform` fans out to two parallel sinks. `notify` is a join — it waits for both `load_dw` and `load_s3`.

**Execution state per step:**

Every step in a running pipeline has one of these states:

```
WAITING     — dependencies not yet satisfied
READY       — all dependencies met, eligible for dispatch
RUNNING     — assigned to a runner, executing
SUCCEEDED   — completed successfully
FAILED      — terminal failure (no more retries)
CANCELLED   — upstream failure made this step unreachable
SKIPPED     — conditional branch chose a different path
```

**DAG traversal algorithm (Execution Service):**

```
On PipelineTriggered event:
  1. Load pipeline definition from Pipeline Service
  2. Create Execution record (status: RUNNING)
  3. For each step: create Job record (status: WAITING or READY)
     READY = steps with depends_on: []
  4. Dispatch all READY jobs → publish to Kafka

On JobSucceeded event (job_id = X):
  1. Mark Job X as SUCCEEDED
  2. For each step Y where X ∈ Y.depends_on:
     Check if ALL of Y's dependencies are SUCCEEDED
     If yes: transition Y to READY → dispatch to Kafka
  3. If all steps SUCCEEDED: mark Execution as SUCCEEDED

On JobFailed event (job_id = X, retriesExhausted = true):
  1. Mark Job X as FAILED
  2. Identify all downstream steps (reachable from X in the DAG)
  3. Mark all downstream steps as CANCELLED
  4. Mark Execution as FAILED
  5. Publish ExecutionFailed event (triggers notification, Agent auto-heal)
```

**Concurrency enforcement:**

```java
private boolean canDispatch(Execution execution, Job job) {
    long runningCount = jobRepository.countByExecutionIdAndStatus(
        execution.getId(), JobStatus.RUNNING);
    return runningCount < execution.getConcurrencyLimit();
}
```

If `concurrency_limit = 3` and 3 jobs are already running, READY jobs wait in the `READY` state until a running job completes. The `JobSucceeded` / `JobFailed` handler re-evaluates READY jobs after every completion.

**Retry policy per step:**

```json
{
  "step_id": "load_dw",
  "retry": {
    "max_attempts": 3,
    "backoff": "EXPONENTIAL",
    "initial_delay_seconds": 30,
    "max_delay_seconds": 300,
    "retry_on": ["TRANSIENT_ERROR", "TIMEOUT"],
    "no_retry_on": ["SCHEMA_MISMATCH", "AUTH_FAILURE"]
  }
}
```

Retry is a step-level concern, not a pipeline concern. The runner reports a structured error category. The Execution Service decides whether to retry (create a new `JobAttempt`) or mark as `FAILED`.

**Timeout enforcement:**

```
Step timeout:  enforced by the runner (sends FAILED result after step_timeout_seconds)
Pipeline timeout: enforced by the Execution Service (scheduled task checks all
                  running executions; any older than pipeline_timeout_seconds → FAILED)
```

**Conditional branching:**

```json
{
  "step_id": "branch",
  "type": "CONDITIONAL",
  "conditions": [
    { "expression": "{{ steps.validate.output.row_count > 0 }}", "next_step": "transform" },
    { "expression": "{{ steps.validate.output.row_count == 0 }}", "next_step": "notify_empty" }
  ]
}
```

A `CONDITIONAL` step evaluates expressions against upstream step outputs. Steps on the unchosen branch are marked `SKIPPED`, not `FAILED`.

**Fan-out (Map step):**

```json
{
  "step_id": "process_files",
  "type": "MAP",
  "input_expression": "{{ steps.list_files.output.file_paths }}",
  "map_step": { "type": "DUCKDB_TRANSFORM", ... }
}
```

A `MAP` step dynamically creates N child jobs at runtime — one per element in `input_expression`. The fan-in join waits for all N children to complete. This is how dynamic DAGs are implemented.

**Idempotency on retry:**

Every step execution is identified by `(execution_id, step_id, attempt_number)`. The runner receives this triple as its idempotency key. Re-running a step that already completed (due to a retry after a network blip) returns the previous result without re-executing.

```java
String idempotencyKey = executionId + ":" + stepId + ":" + attemptNumber;
// Runner checks: have I already completed this key?
// If yes: return cached result, don't re-run
```

---

## Consequences

### Positive

- **Correct parallel execution** — steps with no shared dependency run simultaneously without coordination. A 10-step pipeline where 5 steps are independent completes in the time of the slowest single path, not the sum of all steps.
- **Correct dependency semantics** — a step never starts before its dependencies complete. The join wait (waiting for ALL of `load_dw` and `load_s3` before `notify`) is handled by the dependency checker, not by polling or sleeping.
- **Failure scope is minimal** — when a step fails, only its downstream dependents are cancelled. Parallel branches that have already succeeded remain SUCCEEDED. A partial failure is visible and recoverable.
- **Dynamic DAGs via Map step** — fan-out to N parallel tasks is a first-class construct. The number of parallel tasks is determined at runtime, not at pipeline definition time.
- **Audit trail per step** — every `JobAttempt` records the runner, start time, end time, exit code, and error category. The full execution timeline is queryable.

### Negative

- **Topological sort must handle cycles** — the Pipeline Service validates DAGs for cycles on save. A cycle that slips through (bug in validation) will cause the execution engine to deadlock (no steps ever become READY). Cycle detection must run again at execution start as a safety check.
- **Dependency state tracking is a hot write path** — every step completion triggers a dependency check for all successors. At high fan-in (many steps depending on one completion), this is a burst of writes. The database must handle this under load.
- **Conditional branch expression evaluation** — Jinja-style template expressions must be evaluated in a sandboxed context. Arbitrary code execution is a security risk if pipeline authors can inject malicious expressions.
- **Fan-out cardinality must be bounded** — a `MAP` step over an input with 100,000 elements would create 100,000 parallel jobs. Maximum fan-out must be configured and enforced.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| DAG cycle not caught at save time | Re-validate (topological sort) when Execution is created; abort with INVALID_DAG error |
| Expression injection in conditional steps | Expression evaluation runs in a whitelist-only sandbox; no access to system calls, network, or file system |
| Fan-out creates too many jobs | `max_map_cardinality` limit (default: 1000) enforced before creating child jobs |
| Dependency check race condition (two steps complete simultaneously, both trigger the same downstream) | Atomic state transition using PostgreSQL `UPDATE jobs SET status='READY' WHERE status='WAITING' AND id=?`; exactly one winner |
| Pipeline timeout scanner misses an execution | Scanner runs every 60 seconds; alert if scanner hasn't run in >120 seconds |

---

## Alternatives Considered

### Linear Step Execution Only

Execute steps strictly in the order they are defined. No parallelism.

Rejected because it defeats the purpose of a DAG. A pipeline that extracts from 5 sources and loads to 3 destinations would take 5× longer than necessary. The extract steps are independent and must run in parallel.

### Temporal Workflow Engine

Use Temporal (or Conductor) as an external durable workflow engine to manage DAG execution. Pravah would define pipeline executions as Temporal workflows.

Considered for the durable execution requirement. Rejected because:
- Temporal is a separate operational dependency — its own cluster, its own storage (Cassandra or PostgreSQL), its own SDK. Adding Temporal adds significant operational complexity.
- Pravah's DAG execution model is simpler than general-purpose workflow engines. The event-driven, Kafka-backed approach with PostgreSQL for state is sufficient and consistent with the rest of the architecture.
- Durable execution (surviving crashes mid-pipeline) is addressed separately in ADR-024 via checkpointing, not by a workflow engine.

### Apache Airflow as DAG Engine

Embed Airflow's DAG scheduling logic as a library, or run Airflow alongside Pravah.

Rejected because Pravah IS the Airflow replacement. Embedding Airflow's scheduler would create a dependency on its Python-centric architecture, its scheduler-as-single-process design, and its database-centric approach — all of which Pravah is designed to improve upon.
