# ADR-025: Backfill Strategy & Pipeline Concurrency Policy

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Two related but distinct problems arise in any pipeline orchestration platform:

**Problem 1 — Backfill:**
A pipeline runs daily at 9am, loading the previous day's orders. Due to a bug, the pipeline produced incorrect data for the 15 days between May 1 and May 15. After fixing the bug, the team needs to re-run the pipeline for each of those 15 days to correct the data. This is a **backfill**: running a pipeline for historical date ranges.

Backfill must be:
- **Safe**: re-running for a date that already has data must not double-count. The output must be idempotent.
- **Controlled**: 15 parallel backfill runs should not overwhelm source systems or runners. Backfill should be throttled.
- **Parameterized**: each run must know which date range it is responsible for.

**Problem 2 — Concurrency Policy:**
A cron-based pipeline is scheduled to run every hour. What happens if the 10am run is still executing at 11am when the scheduler fires again? Three valid strategies exist:

- `SKIP`: ignore the new trigger — let the running execution finish first
- `QUEUE`: enqueue the new trigger — run it after the current execution completes
- `CANCEL_AND_RUN`: cancel the current execution and immediately start the new one

Without an explicit policy, the default behavior is undefined. The Scheduler must have clear rules for every pipeline.

---

## Decision

**Backfill:** a dedicated backfill API creates a bounded set of historical pipeline runs with controlled parallelism and explicit date range parameters.

**Concurrency Policy:** every pipeline has a configurable `concurrency_policy` (`SKIP`, `QUEUE`, `CANCEL_AND_RUN`) with `SKIP` as the safe default.

---

### Part 1: Backfill

**Backfill trigger via API:**

```bash
POST /v1/pipelines/{id}/backfill
{
  "start_date": "2026-05-01",
  "end_date":   "2026-05-15",
  "max_parallel_runs": 3,      # at most 3 runs executing simultaneously
  "parameters": {
    "overwrite_existing": true  # re-run even if output already exists for this date
  }
}

Response:
{
  "backfill_id": "backfill-uuid-abc",
  "total_runs": 15,             # one run per day
  "status": "QUEUED",
  "runs": [
    { "run_id": "...", "date": "2026-05-01", "status": "QUEUED" },
    ...
  ]
}
```

**Backfill execution model:**

The Scheduler creates 15 trigger records, each with:
- `scheduled_date`: the logical date the run is responsible for (e.g., `2026-05-01`)
- `parameters`: `{ "business_date": "2026-05-01" }` — injected into the pipeline as a template variable
- `backfill_id`: groups all runs under a single backfill operation
- `priority`: BACKFILL (lower than SCHEDULED, higher than ADHOC in queue)

```
Backfill queue with max_parallel_runs=3:

  ┌──────────────────────────────────────────────────────┐
  │  Queued: May-01, May-02, May-03, ..., May-15         │
  │  Running: May-01, May-02, May-03                     │
  │  Completed: (none yet)                               │
  └──────────────────────────────────────────────────────┘

As each run completes, the next queued run starts (FIFO within backfill):
  Running: May-04, May-05, May-06
  ...
```

**Idempotency in backfill runs:**

Each backfill run has a deterministic idempotency key:

```
idempotency_key = pipeline_id + ":" + scheduled_date + ":" + backfill_id
```

If a backfill run is retried (e.g., it failed due to a transient error), the same idempotency key is used. Destination writes use MERGE/UPSERT keyed on `business_date` — re-running for the same date overwrites previous data for that date, rather than duplicating it.

```sql
-- At destination (Snowflake, BigQuery, PostgreSQL)
MERGE INTO target AS t
USING staging AS s
ON t.order_date = s.order_date        -- natural key for the date partition
   AND t.order_id = s.order_id
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...;
```

**Backfill vs regular run isolation:**

Backfill runs are marked with `run_type = BACKFILL`. This allows:
- Separate monitoring (backfill lag doesn't pollute regular pipeline SLOs)
- Separate runner pools (enterprise tenants can route backfills to dedicated runners)
- Automatic priority deprioritization (regular scheduled runs preempt backfill runs in the queue)

---

### Part 2: Concurrency Policy

**Pipeline configuration:**

```yaml
pipeline:
  id: daily-orders-etl
  schedule: "0 9 * * *"
  concurrency_policy: SKIP     # default
  max_active_runs: 1           # used with QUEUE policy
```

**Policy semantics:**

| Policy | Behavior when new trigger fires and execution is already running |
|--------|----------------------------------------------------------------|
| `SKIP` | The new trigger is discarded. A log entry records the skip. Alert if skips happen repeatedly (pipeline is running too slow). |
| `QUEUE` | The new trigger is enqueued. It starts after the current run completes. `max_active_runs` limits the queue depth — if the queue is full, the new trigger is SKIPPED. |
| `CANCEL_AND_RUN` | The current run is cancelled (kill signal sent to runners). The new trigger starts immediately. Use for idempotent, stateless pipelines where freshness matters more than completeness. |

**Scheduler implementation:**

```java
@Transactional
public void processScheduledTrigger(String pipelineId, Instant scheduledAt) {
    Pipeline pipeline = pipelineRepository.findById(pipelineId);
    List<Execution> activeRuns = executionRepository.findActive(pipelineId);

    if (activeRuns.isEmpty()) {
        // No conflict — start normally
        createExecution(pipeline, scheduledAt, RunType.SCHEDULED);
        return;
    }

    switch (pipeline.getConcurrencyPolicy()) {
        case SKIP -> {
            log.warn("Pipeline {} skipping trigger at {} — active run exists: {}",
                pipelineId, scheduledAt, activeRuns.get(0).getId());
            auditLog.recordSkip(pipelineId, scheduledAt, activeRuns.get(0).getId());
            // Publish metric: pravah_pipeline_trigger_skipped_total
        }
        case QUEUE -> {
            long queuedCount = executionRepository.countQueued(pipelineId);
            if (queuedCount >= pipeline.getMaxActiveRuns() - 1) {
                // Queue is full — degrade to SKIP
                log.warn("Pipeline {} queue full, skipping trigger", pipelineId);
            } else {
                createExecution(pipeline, scheduledAt, RunType.SCHEDULED);
                // This execution will start once activeRuns[0] completes
            }
        }
        case CANCEL_AND_RUN -> {
            for (Execution active : activeRuns) {
                executionService.cancel(active.getId(), CancelReason.SUPERSEDED_BY_NEW_RUN);
            }
            createExecution(pipeline, scheduledAt, RunType.SCHEDULED);
        }
    }
}
```

**Monitoring concurrency policy:**

```
Prometheus metrics:
  pravah_pipeline_trigger_skipped_total{pipeline_id, policy}
  pravah_pipeline_runs_queued{pipeline_id}
  pravah_pipeline_runs_cancelled_by_policy{pipeline_id, policy}

Alert:
  SKIP count > 3 in 1 hour → "Pipeline {name} consistently can't finish within its schedule interval"
  → Action: increase runner capacity, optimize pipeline, or change schedule interval
```

---

## Consequences

### Positive

- **Backfill is a first-class feature**: teams can safely re-run historical date ranges without writing custom scripts or worrying about duplicates. The platform handles idempotency, throttling, and progress tracking.
- **Concurrency policy makes implicit behavior explicit**: every pipeline has a declared behavior for the "trigger fires while running" case. There are no surprises. The `SKIP` default is the safest choice — it never wastes compute on a run that would be superseded.
- **Skip metrics reveal scheduling problems**: a pipeline that consistently skips tells the team the pipeline is slower than its schedule interval. This is actionable operational intelligence.
- **Backfill and regular runs are isolated**: backfill monitoring does not pollute regular SLO dashboards. A 15-day backfill that takes 2 hours does not affect the p99 latency metrics of the regular scheduled runs.

### Negative

- **QUEUE policy can create large backlogs**: if a pipeline is slow for an extended period, the queue grows. A pipeline scheduled every hour that takes 90 minutes per run accumulates one queued run per 90-minute cycle. Left unchecked, this leads to unbounded queue growth.
- **CANCEL_AND_RUN requires idempotent pipelines**: cancelling a mid-run execution means partially-written data at the destination. The next run must be able to safely overwrite that partial state. Not all pipelines support this.
- **Backfill parallelism must be tuned carefully**: too many parallel backfill runs can overwhelm source systems (e.g., a PostgreSQL source that can't handle 10 parallel read queries). The `max_parallel_runs` limit must be set conservatively.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| QUEUE policy backlog grows unboundedly | `max_active_runs` limits the queue depth; oldest queued run is discarded when the limit is reached |
| Backfill runs compete with production runs for runner capacity | Backfill runs have lower priority in the runner assignment queue; production runs are always assigned first |
| CANCEL_AND_RUN leaves partial destination data | Destinations must use MERGE/UPSERT semantics; partial data from cancelled run is overwritten by new run |
| Backfill parameter injection allows template injection | Parameters are validated and sanitized before template substitution; Jinja sandbox prevents code execution |
