# Sequence Diagrams

This document contains detailed sequence diagrams for key flows in Pravah. These are essential for understanding how services interact and for interview discussions.

---

## Overview of Key Flows

| Flow | Services Involved | Complexity |
|------|-------------------|------------|
| [1. Create Pipeline](#1-create-pipeline) | Gateway, Pipeline Service, Kafka | Simple |
| [2. Trigger Execution](#2-trigger-execution) | Gateway, Pipeline, Execution, Kafka | Medium |
| [3. Execute Job](#3-execute-job) | Execution, Runner Service, Runner | Complex |
| [4. Complete Execution](#4-complete-execution) | Runner, Execution, Notification, Agent | Complex |
| [5. Scheduled Trigger](#5-scheduled-trigger) | Scheduler, Execution, Kafka | Medium |
| [6. User Authentication](#6-user-authentication) | Gateway, Tenant Service, Vault | Medium |
| [7. Lineage Capture](#7-lineage-capture) | Runner, Metadata Service | Medium |
| [8. AI Diagnosis](#8-ai-diagnosis-on-failure) | Agent Service, LLM, Notification | Complex |

---

## 1. Create Pipeline

User creates a new pipeline through the UI.

```
┌──────┐          ┌─────────┐          ┌──────────────┐          ┌───────┐
│ User │          │ Gateway │          │Pipeline Svc  │          │ Kafka │
└──┬───┘          └────┬────┘          └──────┬───────┘          └───┬───┘
   │                   │                      │                      │
   │  POST /graphql    │                      │                      │
   │  mutation {       │                      │                      │
   │    createPipeline │                      │                      │
   │  }                │                      │                      │
   │──────────────────▶│                      │                      │
   │                   │                      │                      │
   │                   │  Validate JWT        │                      │
   │                   │  Extract tenant_id   │                      │
   │                   │                      │                      │
   │                   │  gRPC: CreatePipeline│                      │
   │                   │─────────────────────▶│                      │
   │                   │                      │                      │
   │                   │                      │  BEGIN TRANSACTION   │
   │                   │                      │                      │
   │                   │                      │  1. Validate schema  │
   │                   │                      │                      │
   │                   │                      │  2. Store event:     │
   │                   │                      │     PIPELINE_CREATED │
   │                   │                      │     in pipeline_events│
   │                   │                      │                      │
   │                   │                      │  3. Project to       │
   │                   │                      │     pipelines table  │
   │                   │                      │                      │
   │                   │                      │  4. Insert to outbox │
   │                   │                      │                      │
   │                   │                      │  COMMIT              │
   │                   │                      │                      │
   │                   │  Pipeline created    │                      │
   │                   │◀─────────────────────│                      │
   │                   │                      │                      │
   │  { pipeline: {    │                      │                      │
   │      id: "..."    │                      │                      │
   │    }              │                      │                      │
   │  }                │                      │                      │
   │◀──────────────────│                      │                      │
   │                   │                      │                      │
   │                   │                      │  ┌───────────────────┐
   │                   │                      │  │ Outbox Publisher  │
   │                   │                      │  │ (async, polling)  │
   │                   │                      │  └─────────┬─────────┘
   │                   │                      │            │
   │                   │                      │            │ Publish
   │                   │                      │            │ pravah.pipeline.events
   │                   │                      │            │──────────────────────▶│
   │                   │                      │            │                       │
   │                   │                      │            │ Update outbox.published_at
   │                   │                      │◀───────────│                       │
```

### Key Points for Interviews

1. **Event Sourcing**: `PIPELINE_CREATED` event stored first, then projected to read model
2. **Transactional Outbox**: Event + outbox in same transaction → guaranteed delivery to Kafka
3. **Async Publishing**: User gets response immediately; Kafka publish happens async

---

## 2. Trigger Execution

User manually triggers a pipeline run.

```
┌──────┐       ┌─────────┐       ┌────────────┐       ┌─────────────┐       ┌───────┐
│ User │       │ Gateway │       │Pipeline Svc│       │Execution Svc│       │ Kafka │
└──┬───┘       └────┬────┘       └─────┬──────┘       └──────┬──────┘       └───┬───┘
   │                │                  │                     │                  │
   │ POST /graphql  │                  │                     │                  │
   │ triggerRun(    │                  │                     │                  │
   │   pipelineId,  │                  │                     │                  │
   │   params)      │                  │                     │                  │
   │───────────────▶│                  │                     │                  │
   │                │                  │                     │                  │
   │                │ gRPC:            │                     │                  │
   │                │ GetPipeline(id)  │                     │                  │
   │                │─────────────────▶│                     │                  │
   │                │                  │                     │                  │
   │                │ Pipeline +       │                     │                  │
   │                │ current version  │                     │                  │
   │                │◀─────────────────│                     │                  │
   │                │                  │                     │                  │
   │                │ REST: POST /executions                 │                  │
   │                │ {pipelineId, version, params}          │                  │
   │                │───────────────────────────────────────▶│                  │
   │                │                  │                     │                  │
   │                │                  │                     │  BEGIN TX        │
   │                │                  │                     │                  │
   │                │                  │                     │  1. Create       │
   │                │                  │                     │     execution    │
   │                │                  │                     │     (PENDING)    │
   │                │                  │                     │                  │
   │                │                  │                     │  2. Create jobs  │
   │                │                  │                     │     for each     │
   │                │                  │                     │     stage        │
   │                │                  │                     │     (PENDING)    │
   │                │                  │                     │                  │
   │                │                  │                     │  3. Insert to    │
   │                │                  │                     │     outbox:      │
   │                │                  │                     │   execution.created
   │                │                  │                     │                  │
   │                │                  │                     │  COMMIT          │
   │                │                  │                     │                  │
   │                │ { executionId }                        │                  │
   │                │◀───────────────────────────────────────│                  │
   │                │                  │                     │                  │
   │ { run: {       │                  │                     │                  │
   │     id: "..."  │                  │                     │                  │
   │   }            │                  │                     │                  │
   │ }              │                  │                     │                  │
   │◀───────────────│                  │                     │                  │
   │                │                  │                     │                  │
   │                │                  │                     │  [Outbox Publisher]
   │                │                  │                     │        │         │
   │                │                  │                     │        │Publish  │
   │                │                  │                     │        │────────▶│
   │                │                  │                     │        │         │
```

**Continuation - Execution Service processes its own event:**

```
┌─────────────┐       ┌───────┐       ┌─────────────┐
│Execution Svc│       │ Kafka │       │Runner Service│
└──────┬──────┘       └───┬───┘       └──────┬──────┘
       │                  │                  │
       │◀─────────────────│                  │
       │ execution.created│                  │
       │ (self-consume)   │                  │
       │                  │                  │
       │ Evaluate DAG     │                  │
       │ Find root jobs   │                  │
       │ (no dependencies)│                  │
       │                  │                  │
       │ Update jobs:     │                  │
       │ PENDING → QUEUED │                  │
       │                  │                  │
       │ Publish:         │                  │
       │ job.created      │                  │
       │─────────────────▶│                  │
       │                  │                  │
       │                  │ job.created      │
       │                  │─────────────────▶│
       │                  │                  │
       │                  │                  │ Find available
       │                  │                  │ runner with
       │                  │                  │ matching labels
       │                  │                  │
       │                  │                  │ Reserve capacity
       │                  │                  │
       │                  │ job.assigned     │
       │                  │◀─────────────────│
       │                  │                  │
       │ job.assigned     │                  │
       │◀─────────────────│                  │
       │                  │                  │
       │ Update job:      │                  │
       │ QUEUED → RUNNING │                  │
       │ started_at = now │                  │
```

---

## 3. Execute Job

Runner executes a job and reports back.

```
┌──────────────┐       ┌───────┐       ┌──────────────────────────────────────────────┐
│Runner Service│       │ Kafka │       │              Runner (On-Prem)                 │
└──────┬───────┘       └───┬───┘       │ ┌─────────┐  ┌──────────┐  ┌───────────────┐ │
       │                   │           │ │gRPC Conn│  │ Executor │  │ DuckDB Engine │ │
       │                   │           │ └────┬────┘  └────┬─────┘  └───────┬───────┘ │
       │                   │           └──────┼───────────┼─────────────────┼─────────┘
       │                   │                  │           │                 │
       │ job.assigned      │                  │           │                 │
       │ (from prev step)  │                  │           │                 │
       │                   │                  │           │                 │
       │ gRPC Stream:      │                  │           │                 │
       │ JobAssignment     │                  │           │                 │
       │─────────────────────────────────────▶│           │                 │
       │                   │                  │           │                 │
       │                   │                  │ Parse job │                 │
       │                   │                  │ config    │                 │
       │                   │                  │──────────▶│                 │
       │                   │                  │           │                 │
       │                   │                  │           │ Fetch secrets   │
       │                   │                  │           │ from Vault      │
       │                   │                  │           │ (via sidecar)   │
       │                   │                  │           │                 │
       │                   │                  │           │ Execute stage   │
       │                   │                  │           │                 │
       │                   │                  │           │ [SQL Stage]     │
       │                   │                  │           │────────────────▶│
       │                   │                  │           │                 │
       │                   │                  │           │ DuckDB:         │
       │                   │                  │           │ 1. Connect to   │
       │                   │                  │           │    source DB    │
       │                   │                  │           │ 2. Execute query│
       │                   │                  │           │ 3. Transform    │
       │                   │                  │           │                 │
       │                   │                  │           │ Results         │
       │                   │                  │           │◀────────────────│
       │                   │                  │           │                 │
       │ gRPC Stream:      │                  │           │                 │
       │ LogLine (INFO)    │                  │           │                 │
       │◀─────────────────────────────────────│           │                 │
       │                   │                  │           │                 │
       │ [Forward logs     │                  │           │                 │
       │  to log store]    │                  │           │                 │
       │                   │                  │           │                 │
       │ gRPC Stream:      │                  │           │                 │
       │ LogLine (INFO)    │                  │           │                 │
       │◀─────────────────────────────────────│  ...      │                 │
       │                   │                  │           │                 │
       │                   │                  │           │ [On success]    │
       │                   │                  │           │                 │
       │ gRPC Stream:      │                  │           │                 │
       │ JobStatusUpdate   │                  │           │                 │
       │ (SUCCEEDED)       │                  │           │                 │
       │◀─────────────────────────────────────│           │                 │
       │                   │                  │           │                 │
       │ gRPC Stream:      │                  │           │                 │
       │ OpenLineageEvent  │                  │           │                 │
       │◀─────────────────────────────────────│           │                 │
       │                   │                  │           │                 │
       │ Publish:          │                  │           │                 │
       │ job.completed     │                  │           │                 │
       │──────────────────▶│                  │           │                 │
       │                   │                  │           │                 │
       │ Publish:          │                  │           │                 │
       │ lineage.events    │                  │           │                 │
       │──────────────────▶│                  │           │                 │
```

---

## 4. Complete Execution

Full flow when an execution completes (success or failure).

```
┌──────────────┐    ┌───────┐    ┌─────────────┐    ┌─────────────┐    ┌───────────┐
│Runner Service│    │ Kafka │    │Execution Svc│    │Notification │    │Agent Svc  │
└──────┬───────┘    └───┬───┘    └──────┬──────┘    └──────┬──────┘    └─────┬─────┘
       │                │               │                  │                 │
       │ job.completed  │               │                  │                 │
       │ (last job)     │               │                  │                 │
       │───────────────▶│               │                  │                 │
       │                │               │                  │                 │
       │                │ job.completed │                  │                 │
       │                │──────────────▶│                  │                 │
       │                │               │                  │                 │
       │                │               │ Update job:      │                 │
       │                │               │ RUNNING→SUCCEEDED│                 │
       │                │               │                  │                 │
       │                │               │ Check: all jobs  │                 │
       │                │               │ complete?        │                 │
       │                │               │ YES              │                 │
       │                │               │                  │                 │
       │                │               │ Update execution:│                 │
       │                │               │ RUNNING→SUCCEEDED│                 │
       │                │               │                  │                 │
       │                │               │ Outbox: publish  │                 │
       │                │               │ execution.       │                 │
       │                │               │ completed        │                 │
       │                │               │                  │                 │
       │                │◀──────────────│                  │                 │
       │                │ execution.    │                  │                 │
       │                │ completed     │                  │                 │
       │                │               │                  │                 │
       │                │──────────────────────────────────▶│                 │
       │                │               │                  │                 │
       │                │               │                  │ Check alert     │
       │                │               │                  │ rules           │
       │                │               │                  │                 │
       │                │               │                  │ [If SUCCESS,    │
       │                │               │                  │  usually no     │
       │                │               │                  │  alert]         │


═══════════════════════════════════════════════════════════════════════════════════
                              FAILURE PATH
═══════════════════════════════════════════════════════════════════════════════════

       │                │               │                  │                 │
       │ job.completed  │               │                  │                 │
       │ (FAILED)       │               │                  │                 │
       │───────────────▶│               │                  │                 │
       │                │──────────────▶│                  │                 │
       │                │               │                  │                 │
       │                │               │ Check retries:   │                 │
       │                │               │ attempt < max?   │                 │
       │                │               │                  │                 │
       │                │               │ [If YES: retry]  │                 │
       │                │               │ job.status=QUEUED│                 │
       │                │               │ attempt++        │                 │
       │                │◀──────────────│                  │                 │
       │                │ job.created   │                  │                 │
       │                │ (retry)       │                  │                 │
       │                │               │                  │                 │
       │                │               │ [If NO: failed]  │                 │
       │                │               │                  │                 │
       │                │               │ execution.status │                 │
       │                │               │ = FAILED         │                 │
       │                │               │                  │                 │
       │                │◀──────────────│                  │                 │
       │                │ execution.    │                  │                 │
       │                │ failed        │                  │                 │
       │                │               │                  │                 │
       │                │──────────────────────────────────▶│                 │
       │                │               │                  │                 │
       │                │               │                  │ Match alert     │
       │                │               │                  │ rules           │
       │                │               │                  │                 │
       │                │               │                  │ Send to Slack,  │
       │                │               │                  │ Email, PagerDuty│
       │                │               │                  │                 │
       │                │─────────────────────────────────────────────────────▶│
       │                │               │                  │                 │
       │                │               │                  │                 │ Begin
       │                │               │                  │                 │ diagnosis
       │                │               │                  │                 │
       │                │               │                  │                 │ (See flow #8)
```

---

## 5. Scheduled Trigger

Scheduler evaluates cron schedules and triggers executions.

```
┌───────────────┐    ┌──────────────┐    ┌─────────────┐    ┌───────┐
│Scheduler Svc  │    │Scheduler DB  │    │Execution Svc│    │ Kafka │
│(Leader only)  │    │              │    │             │    │       │
└───────┬───────┘    └──────┬───────┘    └──────┬──────┘    └───┬───┘
        │                   │                   │               │
        │ [Every 60 seconds]│                   │               │
        │                   │                   │               │
        │ Am I leader?      │                   │               │
        │───────────────────│                   │               │
        │                   │                   │               │
        │ [Check consul/    │                   │               │
        │  postgres lock]   │                   │               │
        │                   │                   │               │
        │ YES, I'm leader   │                   │               │
        │                   │                   │               │
        │ SELECT * FROM     │                   │               │
        │ schedules WHERE   │                   │               │
        │ is_active = true  │                   │               │
        │ AND next_run_at   │                   │               │
        │   <= now()        │                   │               │
        │ FOR UPDATE        │                   │               │
        │ SKIP LOCKED       │                   │               │
        │──────────────────▶│                   │               │
        │                   │                   │               │
        │ [3 due schedules] │                   │               │
        │◀──────────────────│                   │               │
        │                   │                   │               │
        │ For each schedule:│                   │               │
        │                   │                   │               │
        │ POST /executions  │                   │               │
        │ {pipelineId,      │                   │               │
        │  trigger: SCHED}  │                   │               │
        │──────────────────────────────────────▶│               │
        │                   │                   │               │
        │ {executionId}     │                   │               │
        │◀──────────────────────────────────────│               │
        │                   │                   │               │
        │ UPDATE schedules  │                   │               │
        │ SET               │                   │               │
        │  last_run_at=now()│                   │               │
        │  next_run_at=     │                   │               │
        │   compute_next()  │                   │               │
        │ WHERE id = ?      │                   │               │
        │──────────────────▶│                   │               │
        │                   │                   │               │
        │ INSERT INTO       │                   │               │
        │ schedule_history  │                   │               │
        │ (triggered)       │                   │               │
        │──────────────────▶│                   │               │
        │                   │                   │               │
        │ [Next schedule...repeat]              │               │
        │                   │                   │               │


════════════════════════════════════════════════════════════════════════════
                    CATCHUP BEHAVIOR
════════════════════════════════════════════════════════════════════════════

Scenario: Scheduler was down for 3 hours
          Schedule runs every hour
          Catchup policy = 'run_all'

        │                   │                   │               │
        │ SELECT schedules  │                   │               │
        │ WHERE next_run_at │                   │               │
        │   <= now()        │                   │               │
        │──────────────────▶│                   │               │
        │                   │                   │               │
        │ schedule with     │                   │               │
        │ next_run = 3hrs ago                   │               │
        │◀──────────────────│                   │               │
        │                   │                   │               │
        │ catchup = run_all │                   │               │
        │                   │                   │               │
        │ Trigger execution │                   │               │
        │ for interval T-3h │                   │               │
        │──────────────────────────────────────▶│               │
        │                   │                   │               │
        │ Trigger execution │                   │               │
        │ for interval T-2h │                   │               │
        │──────────────────────────────────────▶│               │
        │                   │                   │               │
        │ Trigger execution │                   │               │
        │ for interval T-1h │                   │               │
        │──────────────────────────────────────▶│               │
        │                   │                   │               │
        │ Update next_run   │                   │               │
        │ to next future    │                   │               │
        │ interval          │                   │               │
```

---

## 6. User Authentication

User logs in and accesses the API.

```
┌──────┐    ┌─────────┐    ┌────────────┐    ┌───────┐    ┌───────────┐
│ User │    │ Gateway │    │Tenant Svc  │    │ Vault │    │ Database  │
└──┬───┘    └────┬────┘    └─────┬──────┘    └───┬───┘    └─────┬─────┘
   │             │               │               │               │
   │ POST /login │               │               │               │
   │ {email, pwd}│               │               │               │
   │────────────▶│               │               │               │
   │             │               │               │               │
   │             │ gRPC:         │               │               │
   │             │ Authenticate  │               │               │
   │             │──────────────▶│               │               │
   │             │               │               │               │
   │             │               │ SELECT user   │               │
   │             │               │ WHERE email=? │               │
   │             │               │──────────────────────────────▶│
   │             │               │               │               │
   │             │               │ user record   │               │
   │             │               │◀──────────────────────────────│
   │             │               │               │               │
   │             │               │ Verify bcrypt │               │
   │             │               │ hash          │               │
   │             │               │               │               │
   │             │               │ [If MFA       │               │
   │             │               │  enabled...]  │               │
   │             │               │               │               │
   │             │               │ Generate JWT  │               │
   │             │               │               │               │
   │             │               │ Get signing   │               │
   │             │               │ key from Vault│               │
   │             │               │──────────────▶│               │
   │             │               │               │               │
   │             │               │ signing_key   │               │
   │             │               │◀──────────────│               │
   │             │               │               │               │
   │             │               │ Sign JWT:     │               │
   │             │               │ {sub: user_id,│               │
   │             │               │  org: org_id, │               │
   │             │               │  exp: +24h}   │               │
   │             │               │               │               │
   │             │               │ Create session│               │
   │             │               │──────────────────────────────▶│
   │             │               │               │               │
   │             │ {token, user} │               │               │
   │             │◀──────────────│               │               │
   │             │               │               │               │
   │ {token: "eyJ..."}          │               │               │
   │◀────────────│               │               │               │


═══════════════════════════════════════════════════════════════════════════════
                    SUBSEQUENT API CALL
═══════════════════════════════════════════════════════════════════════════════

   │             │               │               │               │
   │ GET /pipelines              │               │               │
   │ Authorization:              │               │               │
   │   Bearer eyJ...│               │               │               │
   │────────────▶│               │               │               │
   │             │               │               │               │
   │             │ Parse JWT     │               │               │
   │             │ Verify signature               │               │
   │             │ Check expiry  │               │               │
   │             │               │               │               │
   │             │ Extract:      │               │               │
   │             │ - user_id     │               │               │
   │             │ - org_id      │               │               │
   │             │ - permissions │               │               │
   │             │               │               │               │
   │             │ Set context:  │               │               │
   │             │ X-Tenant-ID   │               │               │
   │             │ X-User-ID     │               │               │
   │             │               │               │               │
   │             │ Forward to Pipeline Service   │               │
   │             │ (headers propagated)          │               │
   │             │               │               │               │
   │             │               │ SET LOCAL     │               │
   │             │               │ app.current_  │               │
   │             │               │ tenant_id = ? │               │
   │             │               │──────────────────────────────▶│
   │             │               │               │               │
   │             │               │ SELECT *      │               │
   │             │               │ FROM pipelines│               │
   │             │               │ [RLS filters  │               │
   │             │               │  by tenant]   │               │
```

---

## 7. Lineage Capture

How lineage is captured during job execution.

```
┌────────────────────────────────────────┐    ┌───────┐    ┌──────────────┐
│              Runner                     │    │ Kafka │    │Metadata Svc  │
│ ┌──────────┐  ┌──────────┐  ┌────────┐ │    │       │    │              │
│ │ Executor │  │  DuckDB  │  │Lineage │ │    │       │    │              │
│ │          │  │  Engine  │  │Extractor│ │    │       │    │              │
│ └────┬─────┘  └────┬─────┘  └───┬────┘ │    └───┬───┘    └──────┬───────┘
       │             │            │       │        │               │
       │ Execute SQL │            │       │        │               │
       │ transform   │            │       │        │               │
       │────────────▶│            │       │        │               │
       │             │            │       │        │               │
       │             │ Parse query│       │        │               │
       │             │────────────────────▶       │               │
       │             │            │       │        │               │
       │             │            │ Extract:      │               │
       │             │            │ - Source tables               │
       │             │            │ - Source columns              │
       │             │            │ - Target table                │
       │             │            │ - Target columns              │
       │             │            │ - Transformations             │
       │             │            │       │        │               │
       │             │ Execute    │       │        │               │
       │             │ against    │       │        │               │
       │             │ source DB  │       │        │               │
       │             │            │       │        │               │
       │ Results     │            │       │        │               │
       │◀────────────│            │       │        │               │
       │             │            │       │        │               │
       │ Request     │            │       │        │               │
       │ lineage     │            │       │        │               │
       │────────────────────────────────▶│        │               │
       │             │            │       │        │               │
       │             │            │ OpenLineage   │               │
       │             │            │ Event:        │               │
       │             │            │ {             │               │
       │             │            │  job: {...},  │               │
       │             │            │  inputs: [...],               │
       │             │            │  outputs: [...],              │
       │             │            │  facets: {    │               │
       │             │            │   columnLineage: {...}        │
       │             │            │  }            │               │
       │             │            │ }             │               │
       │             │            │       │        │               │
       │ OpenLineage │            │       │        │               │
       │ event       │            │       │        │               │
       │◀────────────────────────────────│        │               │
       │             │            │       │        │               │
       │                                  │        │               │
       │ gRPC: Send lineage event         │        │               │
       │──────────────────────────────────────────▶│               │
       │                                  │        │               │
       │                                  │        │ Publish:      │
       │                                  │        │ lineage.events│
       │                                  │        │──────────────▶│
       │                                  │        │               │
       │                                  │        │ lineage.events│
       │                                  │        │              ▼│
       │                                  │        │               │
       │                                  │        │ Parse         │
       │                                  │        │ OpenLineage   │
       │                                  │        │               │
       │                                  │        │ Upsert        │
       │                                  │        │ datasets      │
       │                                  │        │               │
       │                                  │        │ Create        │
       │                                  │        │ lineage_edges │
       │                                  │        │               │
       │                                  │        │ Detect schema │
       │                                  │        │ drift?        │
       │                                  │        │               │
       │                                  │        │ [If drift     │
       │                                  │        │  detected]    │
       │                                  │        │               │
       │                                  │        │ Publish:      │
       │                                  │        │ schema.drift. │
       │                                  │        │ detected      │
       │                                  │        │◀──────────────│
```

---

## 8. AI Diagnosis on Failure

Agent Service diagnoses a failed execution.

```
┌───────────┐    ┌───────┐    ┌───────────────────────────────────────────────────┐
│Agent Svc  │    │ Kafka │    │              Agent Service (ReAct Loop)            │
│(Consumer) │    │       │    │ ┌──────────┐  ┌───────────┐  ┌────────┐  ┌──────┐ │
└─────┬─────┘    └───┬───┘    │ │Orchestrator│ │   Tools   │  │  LLM   │  │ DB   │ │
      │              │        │ └─────┬──────┘  └─────┬─────┘  └───┬────┘  └──┬───┘ │
      │              │        └───────┼──────────────┼────────────┼─────────┼──────┘
      │              │                │              │            │         │
      │ execution.   │                │              │            │         │
      │ failed       │                │              │            │         │
      │◀─────────────│                │              │            │         │
      │              │                │              │            │         │
      │ Start        │                │              │            │         │
      │ diagnosis    │                │              │            │         │
      │─────────────────────────────▶│              │            │         │
      │              │                │              │            │         │
      │              │                │ Step 1: Gather context    │         │
      │              │                │              │            │         │
      │              │                │ get_execution_details()   │         │
      │              │                │─────────────▶│            │         │
      │              │                │              │            │         │
      │              │                │ {status,     │            │         │
      │              │                │  stages,     │            │         │
      │              │                │  failed_stage}            │         │
      │              │                │◀─────────────│            │         │
      │              │                │              │            │         │
      │              │                │ get_execution_logs()      │         │
      │              │                │─────────────▶│            │         │
      │              │                │              │            │         │
      │              │                │ [last 50 lines]           │         │
      │              │                │◀─────────────│            │         │
      │              │                │              │            │         │
      │              │                │ Prompt LLM:  │            │         │
      │              │                │ "Execution failed.        │         │
      │              │                │  Logs: ...   │            │         │
      │              │                │  What's the root cause?"  │         │
      │              │                │─────────────────────────▶│         │
      │              │                │              │            │         │
      │              │                │              │ Thought:   │         │
      │              │                │              │ Error shows│         │
      │              │                │              │ "column X  │         │
      │              │                │              │ not found" │         │
      │              │                │              │            │         │
      │              │                │              │ Action:    │         │
      │              │                │              │ check_schema│        │
      │              │                │◀─────────────────────────│         │
      │              │                │              │            │         │
      │              │                │ check_schema(table)       │         │
      │              │                │─────────────▶│            │         │
      │              │                │              │            │         │
      │              │                │ {columns: [A,B,C]}        │         │
      │              │                │ (missing X)  │            │         │
      │              │                │◀─────────────│            │         │
      │              │                │              │            │         │
      │              │                │ get_schema_history()      │         │
      │              │                │─────────────▶│            │         │
      │              │                │              │            │         │
      │              │                │ [X removed 2 days ago]    │         │
      │              │                │◀─────────────│            │         │
      │              │                │              │            │         │
      │              │                │ Prompt LLM:  │            │         │
      │              │                │ "Schema changed.          │         │
      │              │                │  X removed.  │            │         │
      │              │                │  Suggest fix."            │         │
      │              │                │─────────────────────────▶│         │
      │              │                │              │            │         │
      │              │                │              │ Thought:   │         │
      │              │                │              │ Column X was│        │
      │              │                │              │ removed from│        │
      │              │                │              │ source.     │        │
      │              │                │              │            │         │
      │              │                │              │ Action:    │         │
      │              │                │              │ propose_fix│         │
      │              │                │              │ "Remove X  │         │
      │              │                │              │  from query"│        │
      │              │                │◀─────────────────────────│         │
      │              │                │              │            │         │
      │              │                │ Save observation          │         │
      │              │                │──────────────────────────────────▶│
      │              │                │              │            │         │
      │              │                │ Save proposed fix         │         │
      │              │                │ (PROPOSED status)         │         │
      │              │                │──────────────────────────────────▶│
      │              │                │              │            │         │
      │ Diagnosis    │                │              │            │         │
      │ complete     │                │              │            │         │
      │◀─────────────────────────────│              │            │         │
      │              │                │              │            │         │
      │ Publish:     │                │              │            │         │
      │ agent.action.│                │              │            │         │
      │ requires_    │                │              │            │         │
      │ approval     │                │              │            │         │
      │─────────────▶│                │              │            │         │
      │              │                │              │            │         │
      │              │ [Notification Service picks up]            │         │
      │              │ [Sends to Slack for approval]              │         │
```

---

## Interview Tips

**Q: "Walk me through what happens when a user runs a pipeline."**
> Start with diagram #2. Emphasize:
> 1. Request goes through Gateway (auth, routing)
> 2. Pipeline Service returns definition
> 3. Execution Service creates execution + jobs atomically
> 4. Outbox ensures Kafka publish
> 5. Events flow through saga pattern
> 6. Runner receives job via gRPC stream

**Q: "How do you handle failures?"**
> Reference diagram #4. Key points:
> 1. Retries at job level (configurable, exponential backoff)
> 2. After retry exhaustion, execution marked FAILED
> 3. Failure event triggers notification + AI diagnosis
> 4. AI agent uses tools to investigate and propose fixes

**Q: "Explain your event-driven architecture."**
> All diagrams show Kafka as the backbone. Key patterns:
> 1. Outbox ensures consistency
> 2. Saga choreography for distributed transactions
> 3. Idempotent consumers with event ID tracking
> 4. Dead letter topics for poison messages

**Q: "How do you ensure exactly-once processing?"**
> Combination of:
> 1. Transactional outbox (DB + event atomic)
> 2. Idempotent consumers (check processed_events table)
> 3. Idempotent stage execution (idempotency keys)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Engineering | Initial sequence diagrams |
