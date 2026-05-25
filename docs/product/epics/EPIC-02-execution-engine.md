# EPIC-02: Execution Engine

## Overview

The execution engine is the heart of Pravah — it takes workflow definitions and runs them reliably, at scale, with full observability. This epic covers the runner infrastructure, stage execution, state management, and failure handling.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 144 story points  
**Related ADRs:** ADR-004, ADR-005, ADR-010, ADR-011, ADR-015

---

## Goals

1. Execute workflows reliably with exactly-once semantics
2. Scale horizontally to handle 1000+ concurrent runs
3. Support multiple execution environments (K8s, Docker)
4. Handle failures gracefully with checkpointing and retry
5. Pass data between stages efficiently

---

## User Stories

### US-02.01: Trigger Manual Run
**As a** data engineer  
**I want to** manually trigger a workflow run  
**So that** I can test or run on-demand

**Acceptance Criteria:**
- [ ] "Run" button in workflow detail page
- [ ] Optionally override parameters
- [ ] Run starts within 5 seconds
- [ ] Redirects to run detail view
- [ ] Shows "triggered by user" in audit

**Story Points:** 3  
**Priority:** P0

---

### US-02.02: View Run Status
**As a** data engineer  
**I want to** see the status of a running workflow  
**So that** I know if it's working

**Acceptance Criteria:**
- [ ] Real-time status updates (WebSocket)
- [ ] Stage-by-stage progress
- [ ] Duration for each stage
- [ ] Overall run duration
- [ ] Status: Pending, Running, Succeeded, Failed, Cancelled

**Story Points:** 5  
**Priority:** P0

---

### US-02.03: View Run Logs
**As a** data engineer  
**I want to** see logs from my running workflow  
**So that** I can debug issues

**Acceptance Criteria:**
- [ ] Streaming logs in real-time
- [ ] Logs grouped by stage
- [ ] Log level filtering (INFO, WARN, ERROR)
- [ ] Search within logs
- [ ] Download full logs
- [ ] Logs retained for configured period

**Story Points:** 8  
**Priority:** P0

---

### US-02.04: Cancel Running Workflow
**As a** data engineer  
**I want to** cancel a running workflow  
**So that** I can stop a bad run

**Acceptance Criteria:**
- [ ] "Cancel" button visible during run
- [ ] Confirmation dialog
- [ ] Graceful shutdown (SIGTERM first)
- [ ] Force kill option after timeout
- [ ] All running stages stopped
- [ ] Status marked as "Cancelled"

**Story Points:** 5  
**Priority:** P0

---

### US-02.05: Retry Failed Stage
**As a** data engineer  
**I want to** retry a failed stage  
**So that** I don't have to re-run the entire workflow

**Acceptance Criteria:**
- [ ] "Retry from here" button on failed stage
- [ ] Reuses successful upstream results
- [ ] Creates new run with retry reference
- [ ] Retry count visible in UI

**Story Points:** 8  
**Priority:** P0

---

### US-02.06: Configure Auto-Retry
**As a** data engineer  
**I want to** configure automatic retries  
**So that** transient failures recover without intervention

**Acceptance Criteria:**
- [ ] Retry count per stage (default: 3)
- [ ] Retry delay (fixed or exponential backoff)
- [ ] Retry conditions (exit codes, error patterns)
- [ ] Maximum retry duration
- [ ] Retry attempts visible in run detail

**Story Points:** 5  
**Priority:** P0

---

### US-02.07: Configure Stage Timeout
**As a** data engineer  
**I want to** set timeouts on stages  
**So that** hung processes don't run forever

**Acceptance Criteria:**
- [ ] Per-stage timeout configuration
- [ ] Workflow-level default timeout
- [ ] Smart timeout suggestions from history
- [ ] Warning before timeout triggers
- [ ] Timeout shows as specific failure reason

**Story Points:** 5  
**Priority:** P0

---

### US-02.08: Configure Resource Requests
**As a** data engineer  
**I want to** specify CPU and memory for stages  
**So that** they get appropriate resources

**Acceptance Criteria:**
- [ ] CPU request/limit per stage
- [ ] Memory request/limit per stage
- [ ] Preset profiles (small, medium, large)
- [ ] Custom resource specifications
- [ ] Resource usage visible in run detail

**Story Points:** 5  
**Priority:** P0

---

### US-02.09: Parallel Stage Execution
**As a** data engineer  
**I want to** independent stages to run in parallel  
**So that** my workflow completes faster

**Acceptance Criteria:**
- [x] Stages without dependencies run concurrently
- [x] Configurable parallelism limit per workflow (`definition.execution.maxParallelStages`)
- [ ] Resource-aware scheduling (deferred to US-02.20)
- [x] Gantt chart shows parallel execution (`RunStageGantt` with proportional timing bars)
- [x] Stage start time reflects actual scheduling (API returns `queuedAt`, `startedAt`, `completedAt`)

**Story Points:** 8  
**Priority:** P0

---

### US-02.10: Stage Data Passing - XCom
**As a** data engineer  
**I want to** pass small data between stages  
**So that** downstream stages can use upstream results

**Acceptance Criteria:**
- [x] Return values stored in job `output` (execution DB)
- [x] Size limit (1MB default, `pravah.stage.max-output-bytes`)
- [x] Access via `${stages.<stageId>.output.<key>}`
- [x] Visible in UI for debugging (`RunJobOutputPanel` on run detail)
- [x] Warnings for large payloads (`pravah.stage.output-warn-bytes`)
- [x] Publish-time validation (`StageOutputReferenceValidator`)

**Story Points:** 5  
**Priority:** P0

---

### US-02.11: Stage Data Passing - Artifacts
**As a** data engineer  
**I want to** pass large files between stages  
**So that** I can share datasets

**Acceptance Criteria:**
- [ ] Artifact storage (S3, GCS, etc.)
- [ ] Versioned artifacts
- [ ] Automatic cleanup with retention policy
- [ ] Browse artifacts in UI
- [ ] Download artifacts

**Story Points:** 8  
**Priority:** P1

---

### US-02.12: Checkpointing
**As a** data engineer  
**I want to** workflows to checkpoint progress  
**So that** retries don't repeat successful work

**Acceptance Criteria:**
- [ ] Automatic checkpoint after each stage
- [ ] Resume from last checkpoint on retry
- [ ] Clear checkpoint on successful completion
- [ ] Manual clear checkpoint option
- [ ] Checkpoint storage configurable

**Story Points:** 8  
**Priority:** P0

---

### US-02.13: Incremental Processing
**As a** data engineer  
**I want to** process only new data  
**So that** I don't reprocess unchanged records

**Acceptance Criteria:**
- [ ] Track last processed timestamp/offset
- [ ] Provide incremental context to stages
- [ ] Reset incremental state option
- [ ] Backfill respects incremental state
- [ ] Incremental status visible per workflow

**Story Points:** 8  
**Priority:** P1

---

### US-02.14: Run SQL Stage
**As a** data engineer  
**I want to** run SQL against my warehouse  
**So that** I can transform data

**Acceptance Criteria:**
- [ ] Connection to configured warehouse
- [ ] Variable substitution in SQL
- [ ] Query results available to downstream
- [ ] Row count in stage output
- [ ] Query duration tracked

**Story Points:** 5  
**Priority:** P0

---

### US-02.15: Run Python Stage
**As a** data engineer  
**I want to** run Python scripts  
**So that** I can do custom transformations

**Acceptance Criteria:**
- [x] Python 3.9+ support (`python3` / `config.python_version` publish validation)
- [x] Requirements via `config.requirements` list (written to `requirements.txt` in job workspace)
- [x] Virtual environment per stage (temp workspace + `venv`)
- [x] Access to stage context (`context.json` + `PRAVAH_CONTEXT_PATH` env)
- [x] Structured output return (JSON line or `__PRAVAH_OUTPUT__:` prefix on stdout)

**Story Points:** 8  
**Priority:** P0

---

### US-02.16: Run dbt Stage
**As a** data engineer  
**I want to** run dbt models  
**So that** I can use dbt within my workflows

**Acceptance Criteria:**
- [ ] Specify models to run
- [ ] Pass dbt vars
- [ ] Capture dbt output and test results
- [ ] Model status in stage result
- [ ] Native lineage extraction

**Story Points:** 8  
**Priority:** P1

---

### US-02.17: Run Custom Container
**As a** data engineer  
**I want to** run any Docker image  
**So that** I can use any tool

**Acceptance Criteria:**
- [ ] Specify image and tag
- [ ] Mount secrets and config
- [ ] Capture stdout/stderr as logs
- [ ] Exit code determines success/failure
- [ ] Resource limits enforced

**Story Points:** 8  
**Priority:** P0

---

### US-02.18: Run Spark Stage
**As a** data engineer  
**I want to** submit Spark jobs  
**So that** I can process large datasets

**Acceptance Criteria:**
- [ ] Submit to Spark on K8s
- [ ] Configure driver/executor resources
- [ ] Capture Spark logs
- [ ] Track Spark application status
- [ ] Link to Spark UI

**Story Points:** 13  
**Priority:** P1

---

### US-02.19: Deduplication on Replay
**As a** data engineer  
**I want to** replay workflows without duplicating data  
**So that** retries are safe

**Acceptance Criteria:**
- [ ] Idempotency key per run
- [ ] Deduplication helpers for common sinks
- [ ] Warning if stage isn't idempotent
- [ ] Documentation for making stages idempotent

**Story Points:** 8  
**Priority:** P1

---

### US-02.20: Resource-Aware Scheduling
**As a** platform engineer  
**I want to** the scheduler to respect cluster capacity  
**So that** we don't overload resources

**Acceptance Criteria:**
- [ ] Track available cluster resources
- [ ] Queue runs when capacity insufficient
- [ ] Priority-based queuing
- [ ] Queue position visible to users
- [ ] Estimated start time

**Story Points:** 13  
**Priority:** P0

---

### US-02.21: Runner Health Monitoring
**As a** platform engineer  
**I want to** monitor runner health  
**So that** I know when there are problems

**Acceptance Criteria:**
- [ ] Heartbeat from each runner
- [ ] CPU/memory metrics per runner
- [ ] Active/queued jobs per runner
- [ ] Alert on unhealthy runners
- [ ] Auto-remove dead runners

**Story Points:** 8  
**Priority:** P0

---

### US-02.22: Graceful Runner Drain
**As a** platform engineer  
**I want to** drain a runner for maintenance  
**So that** running jobs aren't killed

**Acceptance Criteria:**
- [ ] "Drain" command via API/CLI
- [ ] No new jobs scheduled
- [ ] Current jobs complete normally
- [ ] Status shows "draining"
- [ ] Auto-restore after maintenance

**Story Points:** 5  
**Priority:** P1

---

## Technical Tasks

### T-02.01: Execution Service Design
Design the core execution service architecture.

**Related ADR:** ADR-010  
**Deliverables:**
- Service interface definitions
- State machine for run lifecycle
- Event contracts for run events

**Estimate:** 8 points

---

### T-02.02: Runner Agent Implementation
Implement the runner agent that executes stages.

**Related ADR:** ADR-005  
**Deliverables:**
- gRPC communication with control plane
- Stage execution in containers
- Log streaming
- Health reporting

**Estimate:** 21 points

---

### T-02.03: Kubernetes Operator
Build K8s operator for managing runner pods.

**Related ADR:** ADR-010  
**Deliverables:**
- CRD for runner configuration
- Pod scheduling logic
- Resource management
- Auto-scaling hooks

**Estimate:** 21 points

---

### T-02.04: Outbox Pattern Implementation
Implement transactional outbox for reliable event publishing.

**Related ADR:** ADR-004  
**Deliverables:**
- Outbox table schema
- Polling publisher
- Exactly-once delivery guarantees

**Estimate:** 8 points

---

### T-02.05: Saga Orchestration
Implement saga pattern for multi-stage coordination.

**Related ADR:** ADR-011  
**Deliverables:**
- Saga state machine
- Compensation handlers
- Recovery from partial failures

**Estimate:** 13 points

---

### T-02.06: KEDA Integration
Configure KEDA for event-driven scaling.

**Related ADR:** ADR-015  
**Deliverables:**
- ScaledObject definitions
- Kafka lag-based scaling
- Queue-based scaling

**Estimate:** 8 points

---

### T-02.07: Log Aggregation Pipeline
Build log collection and streaming infrastructure.

**Deliverables:**
- Fluent Bit sidecar configuration
- Log storage (Elasticsearch)
- WebSocket streaming endpoint
- Log retention policy

**Estimate:** 13 points

---

### T-02.08: Artifact Storage Service
Implement artifact upload/download service.

**Deliverables:**
- S3-compatible storage abstraction
- Presigned URL generation
- Cleanup job for retention
- UI integration

**Estimate:** 8 points

---

### T-02.09: Checkpoint Storage
Implement checkpoint storage and recovery.

**Deliverables:**
- Checkpoint serialization format
- Storage backend (Redis or PostgreSQL)
- Recovery logic on restart

**Estimate:** 5 points

---

### T-02.10: SQL Stage Executor
Implement SQL stage execution.

**Deliverables:**
- Connection pool management
- Query execution with timeout
- Result serialization
- Warehouse-specific optimizations

**Estimate:** 8 points

---

### T-02.11: Python Stage Executor
Implement Python stage execution.

**Deliverables:**
- Virtual environment management
- Dependency installation
- Context injection
- Output capture

**Estimate:** 8 points

---

### T-02.12: Container Stage Executor
Implement generic container execution.

**Deliverables:**
- Docker/containerd runtime
- Volume mounts for inputs
- Environment variable injection
- Graceful termination

**Estimate:** 8 points

---

## Dependencies

| Dependency | Epic | Required For |
|------------|------|--------------|
| Workflow Definition | EPIC-01 | All execution |
| Kubernetes Cluster | EPIC-09 | Runner deployment |
| Kafka | EPIC-09 | Event streaming |
| PostgreSQL | EPIC-09 | State storage |
| Secret Management | EPIC-10 | Credential injection |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| K8s operator complexity | High | High | Start simple, iterate |
| Log volume at scale | Medium | Medium | Sampling, retention limits |
| Checkpoint corruption | Low | High | Checksums, backup |
| Resource contention | Medium | High | Proper limits, monitoring |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Run start latency | < 10 seconds | P95 from trigger to first stage |
| Successful run rate | > 99% | Exclude user errors |
| Log availability | 100% | Logs visible within 5s |
| Checkpoint reliability | 100% | Zero lost checkpoints |
| Runner uptime | 99.9% | Per-runner availability |

---

## Open Questions

1. Should we support Windows containers?
2. How do we handle GPU workloads?
3. What's the maximum stage log size before truncation?
4. Should incremental state be shared across workflow versions?

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
