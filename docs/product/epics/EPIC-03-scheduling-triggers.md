# EPIC-03: Scheduling & Triggers

## Overview

Define when and how workflows run — from simple cron schedules to complex event-driven triggers, data sensors, and cross-workflow dependencies. This epic makes workflows automated and reactive.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 76 story points  
**Related ADRs:** ADR-002, ADR-006

---

## Goals

1. Schedule workflows on cron expressions
2. Trigger workflows from events (Kafka, webhooks)
3. Trigger workflows when upstream data arrives
4. Handle missed schedules intelligently
5. Support cross-workflow dependencies

---

## User Stories

### US-03.01: Schedule on Cron
**As a** data engineer  
**I want to** schedule my workflow on a cron expression  
**So that** it runs automatically on a schedule

**Acceptance Criteria:**
- [ ] Standard cron syntax (minute, hour, day, month, weekday)
- [ ] Extended syntax for seconds (optional)
- [ ] Timezone specification
- [ ] Human-readable preview ("Runs every day at 9:00 AM EST")
- [ ] Next 10 scheduled runs displayed

**Story Points:** 5  
**Priority:** P0

---

### US-03.02: Schedule Builder UI
**As a** data engineer  
**I want to** build schedules without memorizing cron syntax  
**So that** I don't make syntax errors

**Acceptance Criteria:**
- [ ] Visual schedule builder
- [ ] Presets (hourly, daily, weekly, monthly)
- [ ] Custom interval builder
- [ ] Generate cron from UI
- [ ] Validate before saving

**Story Points:** 5  
**Priority:** P0

---

### US-03.03: Natural Language Schedule
**As a** data engineer  
**I want to** describe my schedule in plain English  
**So that** it's quick to set up

**Acceptance Criteria:**
- [ ] Parse "every day at 9am"
- [ ] Parse "every Monday and Friday at 6pm"
- [ ] Parse "first day of month at midnight"
- [ ] Show interpreted cron for confirmation
- [ ] Suggest corrections for ambiguous input

**Story Points:** 5  
**Priority:** P2

---

### US-03.04: Multiple Schedules
**As a** data engineer  
**I want to** have multiple schedules for one workflow  
**So that** it can run at different times

**Acceptance Criteria:**
- [ ] Add multiple schedule entries
- [ ] Each schedule has optional parameters
- [ ] Enable/disable individual schedules
- [ ] Deduplication if schedules overlap

**Story Points:** 3  
**Priority:** P1

---

### US-03.05: Pause/Resume Schedule
**As a** data engineer  
**I want to** pause and resume scheduling  
**So that** I can temporarily stop runs without deleting the schedule

**Acceptance Criteria:**
- [ ] Pause button stops future scheduled runs
- [ ] Resume button re-enables scheduling
- [ ] Manual runs still allowed when paused
- [ ] Clear visual indicator of paused state
- [ ] Audit log for pause/resume

**Story Points:** 2  
**Priority:** P0

---

### US-03.06: Event Trigger - Kafka
**As a** data engineer  
**I want to** trigger my workflow when a Kafka message arrives  
**So that** I can process events in real-time

**Acceptance Criteria:**
- [ ] Subscribe to Kafka topic
- [ ] Filter by message attributes
- [ ] Message payload available to workflow
- [ ] Batching option (wait for N messages)
- [ ] Dead letter handling for failures

**Story Points:** 8  
**Priority:** P0

---

### US-03.07: Event Trigger - Webhook
**As a** data engineer  
**I want to** trigger my workflow via HTTP webhook  
**So that** external systems can start runs

**Acceptance Criteria:**
- [ ] Unique webhook URL per workflow
- [ ] POST request with JSON body
- [ ] Body available as workflow parameters
- [ ] Authentication (API key in header)
- [ ] Rate limiting

**Story Points:** 5  
**Priority:** P0

---

### US-03.08: Event Trigger - API
**As a** data engineer  
**I want to** trigger workflows via API  
**So that** I can integrate with other systems

**Acceptance Criteria:**
- [ ] POST /workflows/{id}/runs endpoint
- [ ] Pass parameters in request body
- [ ] Returns run ID immediately
- [ ] SDK methods in Python/CLI
- [ ] Async trigger option (fire and forget)

**Story Points:** 3  
**Priority:** P0

---

### US-03.09: Event Trigger - Git Push
**As a** data engineer  
**I want to** trigger on Git push  
**So that** deployments can validate changes

**Acceptance Criteria:**
- [ ] Trigger on push to configured branch
- [ ] Filter by changed files
- [ ] Commit SHA available to workflow
- [ ] Trigger on PR create/merge
- [ ] GitHub/GitLab/Bitbucket support

**Story Points:** 8  
**Priority:** P1

---

### US-03.10: Data Sensor - File Arrival
**As a** data engineer  
**I want to** trigger when a file appears in S3/GCS  
**So that** I can process landing zone data

**Acceptance Criteria:**
- [ ] Monitor S3 prefix for new files
- [ ] Support GCS, Azure Blob
- [ ] File pattern matching (glob)
- [ ] File metadata available to workflow
- [ ] Configurable polling interval

**Story Points:** 8  
**Priority:** P1

---

### US-03.11: Data Sensor - Table Partition
**As a** data engineer  
**I want to** trigger when a table partition is ready  
**So that** I process data as soon as it's available

**Acceptance Criteria:**
- [ ] Monitor warehouse partition metadata
- [ ] Trigger when partition count increases
- [ ] Filter by partition value patterns
- [ ] Support Snowflake, BigQuery, Redshift
- [ ] Debounce rapid partition updates

**Story Points:** 8  
**Priority:** P1

---

### US-03.12: Cross-Workflow Dependency
**As a** data engineer  
**I want to** trigger workflow B after workflow A completes  
**So that** I can chain workflows

**Acceptance Criteria:**
- [ ] Define dependency on other workflow
- [ ] Wait for specific run or latest successful
- [ ] Access upstream workflow outputs
- [ ] Handle upstream failure gracefully
- [ ] Visualization of dependencies

**Story Points:** 8  
**Priority:** P1

---

### US-03.13: Catchup Behavior - Skip
**As a** platform engineer  
**I want to** skip missed scheduled runs  
**So that** we don't create a backlog

**Acceptance Criteria:**
- [ ] Configure catchup=false per schedule
- [ ] Only run next scheduled time
- [ ] Clear indication of skipped runs
- [ ] Default behavior is skip

**Story Points:** 2  
**Priority:** P0

---

### US-03.14: Catchup Behavior - Catch All
**As a** data engineer  
**I want to** run all missed schedules  
**So that** I don't miss any data intervals

**Acceptance Criteria:**
- [ ] Configure catchup=true per schedule
- [ ] Queue all missed intervals
- [ ] Sequential or parallel execution option
- [ ] Maximum catchup limit (e.g., 30 days)

**Story Points:** 3  
**Priority:** P1

---

### US-03.15: Catchup Behavior - Coalesce
**As a** data engineer  
**I want to** coalesce missed schedules into one  
**So that** I catch up efficiently

**Acceptance Criteria:**
- [ ] Configure catchup=coalesce
- [ ] Combine multiple missed into one run
- [ ] Pass combined interval to workflow
- [ ] Only for intervals less than threshold

**Story Points:** 5  
**Priority:** P1

---

## Technical Tasks

### T-03.01: Scheduler Service Design
Design the scheduling service architecture.

**Deliverables:**
- Service interface definitions
- Schedule evaluation algorithm
- Trigger dispatching logic

**Estimate:** 5 points

---

### T-03.02: Cron Parser
Implement cron expression parsing and evaluation.

**Deliverables:**
- Parse standard cron + extensions
- Timezone handling
- Next N occurrences calculation
- Validation with helpful errors

**Estimate:** 5 points

---

### T-03.03: Schedule Storage
Implement schedule persistence and querying.

**Deliverables:**
- Schedule table schema
- Efficient "due schedules" query
- Schedule change history

**Estimate:** 3 points

---

### T-03.04: Schedule Evaluator Job
Build the job that evaluates due schedules.

**Deliverables:**
- Runs every minute
- Leader election for HA
- Triggers runs for due schedules
- Handles clock drift

**Estimate:** 8 points

---

### T-03.05: Kafka Event Consumer
Implement Kafka consumer for event triggers.

**Related ADR:** ADR-002  
**Deliverables:**
- Consumer group per trigger
- Message filtering
- Trigger dispatching
- Error handling

**Estimate:** 8 points

---

### T-03.06: Webhook Endpoint
Implement webhook trigger endpoint.

**Deliverables:**
- URL generation per workflow
- Authentication middleware
- Rate limiting
- Request validation

**Estimate:** 5 points

---

### T-03.07: Data Sensor Framework
Build extensible sensor framework.

**Deliverables:**
- Sensor interface
- Polling mechanism
- State tracking
- Backoff on failures

**Estimate:** 8 points

---

### T-03.08: S3/GCS File Sensor
Implement cloud storage file sensors.

**Deliverables:**
- S3 list objects polling
- GCS equivalent
- New file detection
- Checkpoint for processed files

**Estimate:** 8 points

---

### T-03.09: Warehouse Partition Sensor
Implement warehouse partition sensors.

**Deliverables:**
- Snowflake partition query
- BigQuery equivalent
- Partition change detection
- Connection pooling

**Estimate:** 8 points

---

### T-03.10: Dependency Graph Tracker
Implement cross-workflow dependency tracking.

**Deliverables:**
- Dependency registration
- Completion listener
- Output propagation
- Cycle detection

**Estimate:** 8 points

---

## Dependencies

| Dependency | Epic | Required For |
|------------|------|--------------|
| Workflow Storage | EPIC-01 | All triggers |
| Execution Engine | EPIC-02 | Dispatching runs |
| Kafka Infrastructure | EPIC-09 | Event triggers |
| UI Components | EPIC-12 | Schedule builder |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Missed schedules | Medium | High | Leader election, monitoring |
| Event storm | Low | High | Rate limiting, backpressure |
| Sensor polling load | Medium | Medium | Adaptive polling intervals |
| Clock sync issues | Low | Medium | NTP, tolerance window |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Schedule accuracy | < 30s from expected | Trigger timestamp vs scheduled |
| Event trigger latency | < 5s from event | Event timestamp to run start |
| Sensor detection latency | < polling interval | New file to trigger |
| Missed schedule rate | < 0.1% | Monitor with alerting |

---

## Open Questions

1. Should we support iCal/RFC 5545 format?
2. How do we handle DST transitions?
3. What's the maximum sensor polling frequency?
4. Should cross-workflow deps work across projects?

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
