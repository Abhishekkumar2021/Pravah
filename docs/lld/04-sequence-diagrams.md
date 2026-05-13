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

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Gateway
    participant PipelineSvc as Pipeline Service
    participant DB as Pipeline DB
    participant Kafka
    
    User->>Gateway: POST /graphql<br/>mutation { createPipeline }
    Gateway->>Gateway: Validate JWT<br/>Extract tenant_id
    Gateway->>PipelineSvc: gRPC: CreatePipeline
    
    rect rgb(240, 248, 255)
        Note over PipelineSvc,DB: Transaction
        PipelineSvc->>DB: 1. Validate schema
        PipelineSvc->>DB: 2. Store event: PIPELINE_CREATED
        PipelineSvc->>DB: 3. Project to pipelines table
        PipelineSvc->>DB: 4. Insert to outbox
        PipelineSvc->>DB: COMMIT
    end
    
    PipelineSvc-->>Gateway: Pipeline created
    Gateway-->>User: { pipeline: { id: "..." } }
    
    rect rgb(255, 250, 240)
        Note over PipelineSvc,Kafka: Async Outbox Publisher
        PipelineSvc->>Kafka: Publish pravah.pipeline.events
        PipelineSvc->>DB: Update outbox.published_at
    end
```

### Key Points for Interviews

1. **Event Sourcing**: `PIPELINE_CREATED` event stored first, then projected to read model
2. **Transactional Outbox**: Event + outbox in same transaction → guaranteed delivery to Kafka
3. **Async Publishing**: User gets response immediately; Kafka publish happens async

---

## 2. Trigger Execution

User manually triggers a pipeline run.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Gateway
    participant PipelineSvc as Pipeline Service
    participant ExecutionSvc as Execution Service
    participant Kafka
    
    User->>Gateway: POST /graphql<br/>triggerRun(pipelineId, params)
    Gateway->>PipelineSvc: gRPC: GetPipeline(id)
    PipelineSvc-->>Gateway: Pipeline + current version
    
    Gateway->>ExecutionSvc: REST: POST /executions
    
    rect rgb(240, 248, 255)
        Note over ExecutionSvc: Transaction
        ExecutionSvc->>ExecutionSvc: 1. Create execution (PENDING)
        ExecutionSvc->>ExecutionSvc: 2. Create jobs for each stage (PENDING)
        ExecutionSvc->>ExecutionSvc: 3. Insert to outbox: execution.created
        ExecutionSvc->>ExecutionSvc: COMMIT
    end
    
    ExecutionSvc-->>Gateway: { executionId }
    Gateway-->>User: { run: { id: "..." } }
    
    Note over ExecutionSvc,Kafka: Async
    ExecutionSvc->>Kafka: execution.created
```

**Continuation - Execution Service processes its own event:**

```mermaid
sequenceDiagram
    autonumber
    participant Kafka
    participant ExecutionSvc as Execution Service
    participant RunnerSvc as Runner Service
    
    Kafka->>ExecutionSvc: execution.created (self-consume)
    
    ExecutionSvc->>ExecutionSvc: Evaluate DAG<br/>Find root jobs (no deps)
    ExecutionSvc->>ExecutionSvc: Update jobs: PENDING → QUEUED
    ExecutionSvc->>Kafka: Publish: job.created
    
    Kafka->>RunnerSvc: job.created
    RunnerSvc->>RunnerSvc: Find available runner<br/>with matching labels
    RunnerSvc->>RunnerSvc: Reserve capacity
    RunnerSvc->>Kafka: job.assigned
    
    Kafka->>ExecutionSvc: job.assigned
    ExecutionSvc->>ExecutionSvc: Update job: QUEUED → RUNNING<br/>started_at = now
```

---

## 3. Execute Job

Runner executes a job and reports back.

```mermaid
sequenceDiagram
    autonumber
    participant RunnerSvc as Runner Service
    participant Runner as Runner (On-Prem)
    participant Vault
    participant DuckDB as DuckDB Engine
    participant Kafka
    
    Note over RunnerSvc,Runner: Bidirectional gRPC Stream
    
    RunnerSvc->>Runner: gRPC Stream: JobAssignment
    Runner->>Runner: Parse job config
    
    Runner->>Vault: Fetch secrets (via sidecar)
    Vault-->>Runner: Credentials
    
    Runner->>DuckDB: Execute SQL Stage
    
    rect rgb(240, 248, 255)
        Note over DuckDB: DuckDB Processing
        DuckDB->>DuckDB: 1. Connect to source DB
        DuckDB->>DuckDB: 2. Execute query
        DuckDB->>DuckDB: 3. Transform data
    end
    
    DuckDB-->>Runner: Results
    
    loop Log Streaming
        Runner->>RunnerSvc: gRPC Stream: LogLine (INFO)
        RunnerSvc->>RunnerSvc: Forward to log store
    end
    
    Runner->>RunnerSvc: gRPC Stream: JobStatusUpdate (SUCCEEDED)
    Runner->>RunnerSvc: gRPC Stream: OpenLineageEvent
    
    RunnerSvc->>Kafka: job.completed
    RunnerSvc->>Kafka: lineage.events
```

---

## 4. Complete Execution

Full flow when an execution completes (success or failure).

### Success Path

```mermaid
sequenceDiagram
    autonumber
    participant RunnerSvc as Runner Service
    participant Kafka
    participant ExecutionSvc as Execution Service
    participant NotificationSvc as Notification Service
    
    RunnerSvc->>Kafka: job.completed (last job)
    Kafka->>ExecutionSvc: job.completed
    
    ExecutionSvc->>ExecutionSvc: Update job: RUNNING → SUCCEEDED
    ExecutionSvc->>ExecutionSvc: Check: all jobs complete? YES
    ExecutionSvc->>ExecutionSvc: Update execution: RUNNING → SUCCEEDED
    ExecutionSvc->>Kafka: execution.completed
    
    Kafka->>NotificationSvc: execution.completed
    NotificationSvc->>NotificationSvc: Check alert rules
    Note over NotificationSvc: If SUCCESS, usually no alert
```

### Failure Path

```mermaid
sequenceDiagram
    autonumber
    participant RunnerSvc as Runner Service
    participant Kafka
    participant ExecutionSvc as Execution Service
    participant NotificationSvc as Notification Service
    participant AgentSvc as Agent Service
    
    RunnerSvc->>Kafka: job.completed (FAILED)
    Kafka->>ExecutionSvc: job.completed (FAILED)
    
    alt Retries Available
        ExecutionSvc->>ExecutionSvc: Check retries: attempt < max? YES
        ExecutionSvc->>ExecutionSvc: job.status = QUEUED, attempt++
        ExecutionSvc->>Kafka: job.created (retry)
    else Retries Exhausted
        ExecutionSvc->>ExecutionSvc: execution.status = FAILED
        ExecutionSvc->>Kafka: execution.failed
        
        Kafka->>NotificationSvc: execution.failed
        NotificationSvc->>NotificationSvc: Match alert rules
        NotificationSvc->>NotificationSvc: Send to Slack, Email, PagerDuty
        
        Kafka->>AgentSvc: execution.failed
        Note over AgentSvc: Begin diagnosis (See flow #8)
    end
```

---

## 5. Scheduled Trigger

Scheduler evaluates cron schedules and triggers executions.

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Scheduler Service<br/>(Leader only)
    participant DB as Scheduler DB
    participant ExecutionSvc as Execution Service
    
    loop Every 60 seconds
        Scheduler->>Scheduler: Am I leader?<br/>(check consul/postgres lock)
        
        alt Is Leader
            Scheduler->>DB: SELECT * FROM schedules<br/>WHERE is_active = true<br/>AND next_run_at <= now()<br/>FOR UPDATE SKIP LOCKED
            DB-->>Scheduler: [due schedules]
            
            loop For each schedule
                Scheduler->>ExecutionSvc: POST /executions<br/>{pipelineId, trigger: SCHED}
                ExecutionSvc-->>Scheduler: {executionId}
                
                Scheduler->>DB: UPDATE schedules<br/>SET last_run_at = now(),<br/>next_run_at = compute_next()
                Scheduler->>DB: INSERT INTO schedule_history
            end
        end
    end
```

### Catchup Behavior

When scheduler was down and needs to catch up:

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler as Scheduler Service
    participant DB as Scheduler DB
    participant ExecutionSvc as Execution Service
    
    Note over Scheduler: Scenario: Scheduler down 3 hours<br/>Schedule runs every hour<br/>Catchup policy = 'run_all'
    
    Scheduler->>DB: SELECT schedules<br/>WHERE next_run_at <= now()
    DB-->>Scheduler: schedule with next_run = 3hrs ago
    
    Scheduler->>Scheduler: catchup = run_all
    
    Scheduler->>ExecutionSvc: Trigger for interval T-3h
    Scheduler->>ExecutionSvc: Trigger for interval T-2h
    Scheduler->>ExecutionSvc: Trigger for interval T-1h
    
    Scheduler->>DB: Update next_run to next future interval
```

---

## 6. User Authentication

User logs in and accesses the API.

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Gateway
    participant TenantSvc as Tenant Service
    participant Vault
    participant DB as Database
    
    User->>Gateway: POST /login {email, pwd}
    Gateway->>TenantSvc: gRPC: Authenticate
    
    TenantSvc->>DB: SELECT user WHERE email = ?
    DB-->>TenantSvc: user record
    
    TenantSvc->>TenantSvc: Verify bcrypt hash
    
    opt MFA Enabled
        TenantSvc->>TenantSvc: Verify MFA code
    end
    
    TenantSvc->>Vault: Get signing key
    Vault-->>TenantSvc: signing_key
    
    TenantSvc->>TenantSvc: Sign JWT:<br/>{sub: user_id, org: org_id, exp: +24h}
    TenantSvc->>DB: Create session
    
    TenantSvc-->>Gateway: {token, user}
    Gateway-->>User: {token: "eyJ..."}
```

### Subsequent API Call

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Gateway
    participant PipelineSvc as Pipeline Service
    participant DB as Database
    
    User->>Gateway: GET /pipelines<br/>Authorization: Bearer eyJ...
    
    Gateway->>Gateway: Parse JWT<br/>Verify signature<br/>Check expiry
    Gateway->>Gateway: Extract: user_id, org_id, permissions
    Gateway->>Gateway: Set headers:<br/>X-Tenant-ID, X-User-ID
    
    Gateway->>PipelineSvc: Forward (headers propagated)
    
    PipelineSvc->>DB: SET LOCAL app.current_tenant_id = ?
    PipelineSvc->>DB: SELECT * FROM pipelines<br/>[RLS filters by tenant]
    DB-->>PipelineSvc: Filtered results
    PipelineSvc-->>Gateway: Pipelines
    Gateway-->>User: Pipeline list
```

---

## 7. Lineage Capture

How lineage is captured during job execution.

```mermaid
sequenceDiagram
    autonumber
    participant Executor
    participant DuckDB as DuckDB Engine
    participant Lineage as Lineage Extractor
    participant RunnerSvc as Runner Service
    participant Kafka
    participant MetadataSvc as Metadata Service
    
    Executor->>DuckDB: Execute SQL transform
    
    DuckDB->>Lineage: Parse query
    
    Lineage->>Lineage: Extract:<br/>- Source tables<br/>- Source columns<br/>- Target table<br/>- Target columns<br/>- Transformations
    
    DuckDB->>DuckDB: Execute against source DB
    DuckDB-->>Executor: Results
    
    Executor->>Lineage: Request lineage
    
    Lineage->>Lineage: Build OpenLineage Event
    Note over Lineage: {job, inputs, outputs,<br/>facets: {columnLineage}}
    
    Lineage-->>Executor: OpenLineage event
    
    Executor->>RunnerSvc: gRPC: Send lineage event
    RunnerSvc->>Kafka: Publish: lineage.events
    
    Kafka->>MetadataSvc: lineage.events
    
    MetadataSvc->>MetadataSvc: Parse OpenLineage
    MetadataSvc->>MetadataSvc: Upsert datasets
    MetadataSvc->>MetadataSvc: Create lineage_edges
    MetadataSvc->>MetadataSvc: Detect schema drift?
    
    opt Schema Drift Detected
        MetadataSvc->>Kafka: Publish: schema.drift.detected
    end
```

---

## 8. AI Diagnosis on Failure

Agent Service diagnoses a failed execution using ReAct pattern.

```mermaid
sequenceDiagram
    autonumber
    participant Kafka
    participant AgentSvc as Agent Service
    participant Tools
    participant LLM
    participant DB as Agent DB
    
    Kafka->>AgentSvc: execution.failed
    
    rect rgb(240, 248, 255)
        Note over AgentSvc,LLM: ReAct Loop - Step 1: Gather Context
        
        AgentSvc->>Tools: get_execution_details()
        Tools-->>AgentSvc: {status, stages, failed_stage}
        
        AgentSvc->>Tools: get_execution_logs()
        Tools-->>AgentSvc: [last 50 lines]
        
        AgentSvc->>LLM: "Execution failed. Logs: ...<br/>What's the root cause?"
        LLM-->>AgentSvc: Thought: Error shows "column X not found"<br/>Action: check_schema
    end
    
    rect rgb(255, 250, 240)
        Note over AgentSvc,LLM: ReAct Loop - Step 2: Investigate
        
        AgentSvc->>Tools: check_schema(table)
        Tools-->>AgentSvc: {columns: [A,B,C]} (missing X)
        
        AgentSvc->>Tools: get_schema_history()
        Tools-->>AgentSvc: [X removed 2 days ago]
        
        AgentSvc->>LLM: "Schema changed. X removed.<br/>Suggest fix."
        LLM-->>AgentSvc: Thought: Column X was removed from source<br/>Action: propose_fix "Remove X from query"
    end
    
    AgentSvc->>DB: Save observation
    AgentSvc->>DB: Save proposed fix (PROPOSED status)
    
    AgentSvc->>Kafka: agent.action.requires_approval
    
    Note over Kafka: Notification Service picks up<br/>Sends to Slack for approval
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
| 1.1 | 2026-05-13 | Engineering | Updated to Mermaid diagrams |
