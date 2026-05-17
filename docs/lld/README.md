# Low-Level Design (LLD) Documentation

This directory contains detailed low-level design documentation for Pravah. Use these documents for **implementation guidance** and **interview preparation**.

---

## Document Index

| Document | Description | Interview Priority |
|----------|-------------|-------------------|
| [01-design-patterns.md](01-design-patterns.md) | Catalog of 28 design patterns with where/why/how | ⭐⭐⭐ High |
| [02-database-erd.md](02-database-erd.md) | Complete database schema for all 8 services | ⭐⭐⭐ High |
| [03-state-machines.md](03-state-machines.md) | State diagrams for Pipeline, Execution, Job, Runner | ⭐⭐⭐ High |
| [04-sequence-diagrams.md](04-sequence-diagrams.md) | 8 key flows: create pipeline, trigger run, etc. | ⭐⭐⭐ High |
| [05-class-diagrams.md](05-class-diagrams.md) | Domain models for each service | ⭐⭐ Medium |
| [06-scalability-failure-analysis.md](06-scalability-failure-analysis.md) | Bottlenecks, failure modes, DR strategy | ⭐⭐⭐ High |
| [07-value-resolution.md](07-value-resolution.md) | Unified `${var.*}`, `${secret.*}`, `${stages.*.output.*}` resolution (US-02.10) | ⭐⭐⭐ High |
| [07-execution-realtime-websocket.md](07-execution-realtime-websocket.md) | Browser WebSocket fan-out for execution status (US-12.10) | ⭐⭐ Medium |

---

## Quick Reference

### Top Interview Topics

1. **Event Sourcing + Outbox Pattern** → [Design Patterns](01-design-patterns.md#1-event-sourcing)
2. **Saga Choreography** → [Design Patterns](01-design-patterns.md#3-saga-choreography)
3. **Execution State Machine** → [State Machines](03-state-machines.md#2-execution-state-machine)
4. **Job Execution Flow** → [Sequence Diagrams](04-sequence-diagrams.md#3-execute-job)
5. **Multi-tenancy (RLS)** → [Database ERD](02-database-erd.md#multi-tenancy-pattern)
6. **Failure Handling** → [Scalability Analysis](06-scalability-failure-analysis.md#failure-modes)

### System Design Interview Prep

**"Design a data pipeline orchestration system"**

1. Start with [HLA](../architecture/high-level-architecture.md) for the 50,000ft view
2. Dive into [Sequence Diagrams](04-sequence-diagrams.md) for key flows
3. Discuss [State Machines](03-state-machines.md) for correctness
4. Address [Scalability](06-scalability-failure-analysis.md) concerns
5. Explain [Design Patterns](01-design-patterns.md) choices

---

## Document Details

### 01 - Design Patterns

**28 patterns** organized by category:

| Category | Patterns |
|----------|----------|
| Distributed Systems | Event Sourcing, Outbox, Saga, CQRS |
| Resilience | Circuit Breaker, Retry, Bulkhead |
| Messaging | Competing Consumers, Idempotent Consumer, DLQ |
| DDD | Aggregate, Repository, Domain Events, Value Object |
| Behavioral | Strategy, State Machine, Factory |

**For each pattern:**
- What it is
- Where it's used in Pravah
- Why we chose it
- How it's implemented
- Trade-offs
- Interview questions

### 02 - Database ERD

**8 database schemas:**

1. `tenant_db` — Organizations, users, roles, permissions
2. `pipeline_db` — Event-sourced pipelines, versions, connections
3. `execution_db` — Executions, jobs, logs, checkpoints
4. `scheduler_db` — Schedules, triggers, webhooks
5. `runner_db` — Runner fleet, certificates, assignments
6. `metadata_db` — Datasets, lineage, quality scores
7. `notification_db` — Alert rules, alerts, deliveries
8. `agent_db` — AI observations, healing actions

**Includes:**
- Full SQL DDL
- Indexes
- Row-Level Security policies
- JSON schema examples

### 03 - State Machines

**6 state machines:**

1. **Pipeline** — DRAFT → ACTIVE → ARCHIVED
2. **Execution** — PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED
3. **Job** — PENDING → QUEUED → RUNNING → SUCCEEDED/FAILED
4. **Runner** — AVAILABLE → BUSY → DRAINING → DEAD
5. **Schedule** — ACTIVE ↔ PAUSED
6. **Alert** — ACTIVE → ACKNOWLEDGED → RESOLVED

**Includes:**
- Mermaid state diagrams
- Transition tables
- Java implementation code
- Interview questions

### 04 - Sequence Diagrams

**8 key flows:**

1. Create Pipeline
2. Trigger Execution
3. Execute Job
4. Complete Execution (success + failure paths)
5. Scheduled Trigger
6. User Authentication
7. Lineage Capture
8. AI Diagnosis

**Includes:**
- Mermaid sequence diagrams
- Service interactions
- Kafka event flows
- Error handling paths

### 05 - Class Diagrams

**Domain models for 8 services:**

1. Pipeline Service — Pipeline, Stage, Version, Event
2. Execution Service — Execution, Job, Checkpoint
3. Scheduler Service — Schedule, Trigger, CronExpression
4. Runner Service — Runner, Assignment, Certificate
5. Tenant Service — Org, Team, User, Role, Permission
6. Metadata Service — Dataset, Column, LineageEdge
7. Notification Service — AlertRule, Alert, Channel
8. Agent Service — Observation, HealingAction, Tool

**Includes:**
- Mermaid class diagrams
- Java code examples
- Value objects
- Repository patterns

### 06 - Scalability & Failure Analysis

**Sections:**

1. **Scalability Analysis**
   - Horizontal scaling model
   - Service-specific scaling triggers
   - Performance targets

2. **Bottleneck Analysis**
   - Database connections (PgBouncer)
   - Kafka partitions
   - Scheduler leader
   - Log ingestion

3. **Failure Modes**
   - 10 failure scenarios
   - Detection, mitigation, recovery for each

4. **Disaster Recovery**
   - Backup strategy
   - DR runbook
   - RTO/RPO targets

5. **Capacity Planning**
   - Small/Medium/Large sizing
   - Cost estimates

---

## Related Documentation

| Document | Purpose |
|----------|---------|
| [Implementation Status](../IMPLEMENTATION_STATUS.md) | What is built in the repo vs target LLD |
| [Architecture Decision Records](../adr/) | Why we made specific technical choices |
| [High-Level Architecture](../architecture/high-level-architecture.md) | System overview, service responsibilities |
| [API & Event Contracts](../design/system-architecture.md) | REST, GraphQL, gRPC, Kafka schemas |
| [Product Documentation](../product/) | Epics, user stories, release plan |
| [Theory Curriculum](../theory/) | Learning materials for concepts |

---

## How to Use These Documents

### For Implementation

1. Read the relevant **class diagram** for domain model
2. Check **database schema** for table structure
3. Follow **sequence diagram** for service interactions
4. Implement **state machine** for entity lifecycle
5. Apply **design patterns** as documented

### For Interview Prep

1. **Day 1**: Read design patterns, understand why each is used
2. **Day 2**: Study state machines, practice drawing from memory
3. **Day 3**: Walk through sequence diagrams, explain each step
4. **Day 4**: Review scalability analysis, prepare failure scenarios
5. **Day 5**: Mock interview — design Pravah from scratch

### For Code Review

Reference these documents when reviewing:
- Does the code follow the documented patterns?
- Does the state machine match the diagram?
- Are failure modes handled as specified?

---

## Document Maintenance

When implementation differs from documentation:

1. **If intentional**: Update the document with the reason
2. **If accidental**: Fix the code to match the design
3. **If improvement**: Document the new approach
4. **Always**: Update [Implementation Status](../IMPLEMENTATION_STATUS.md) when shipping or removing a user-facing capability

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Engineering | Initial LLD documentation |
