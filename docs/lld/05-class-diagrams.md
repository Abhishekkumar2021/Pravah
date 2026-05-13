# Class Diagrams & Domain Models

This document contains class diagrams for each service's domain model. These show the core entities, their relationships, and key methods.

---

## Overview

| Service | Key Entities | Pattern |
|---------|--------------|---------|
| [Pipeline Service](#1-pipeline-service) | Pipeline, Stage, Version, Event | Event Sourcing, Aggregate |
| [Execution Service](#2-execution-service) | Execution, Job, Checkpoint | State Machine |
| [Scheduler Service](#3-scheduler-service) | Schedule, Trigger, EventTrigger | Strategy |
| [Runner Service](#4-runner-service) | Runner, Assignment, Certificate | State Machine |
| [Tenant Service](#5-tenant-service) | Org, Team, User, Role | Hierarchical |
| [Metadata Service](#6-metadata-service) | Dataset, Column, LineageEdge | Graph |
| [Notification Service](#7-notification-service) | AlertRule, Alert, Channel | Observer |
| [Agent Service](#8-agent-service) | Observation, HealingAction | ReAct Agent |

---

## 1. Pipeline Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          PIPELINE SERVICE DOMAIN                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    <<Aggregate Root>>                                │    │
│  │                       Pipeline                                       │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: PipelineId                                                     │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - projectId: ProjectId                                               │    │
│  │ - name: String                                                       │    │
│  │ - description: String                                                │    │
│  │ - status: PipelineStatus                                             │    │
│  │ - currentVersion: int                                                │    │
│  │ - definition: PipelineDefinition                                     │    │
│  │ - createdAt: Instant                                                 │    │
│  │ - createdBy: UserId                                                  │    │
│  │ - uncommittedEvents: List<PipelineEvent>                             │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + publish(): void                                                    │    │
│  │ + updateDefinition(def: PipelineDefinition): void                    │    │
│  │ + archive(): void                                                    │    │
│  │ + restore(): void                                                    │    │
│  │ + validate(): ValidationResult                                       │    │
│  │ # apply(event: PipelineEvent): void                                  │    │
│  │ + getUncommittedEvents(): List<PipelineEvent>                        │    │
│  │ + markEventsAsCommitted(): void                                      │    │
│  └───────────────────────────┬─────────────────────────────────────────┘    │
│                              │                                               │
│              ┌───────────────┼───────────────┐                              │
│              │               │               │                              │
│              ▼               ▼               ▼                              │
│  ┌───────────────────┐ ┌───────────────┐ ┌───────────────────────┐         │
│  │ PipelineDefinition│ │PipelineVersion│ │    PipelineEvent      │         │
│  ├───────────────────┤ ├───────────────┤ ├───────────────────────┤         │
│  │ - stages: List    │ │ - id: UUID    │ │ <<sealed>>            │         │
│  │ - variables: Map  │ │ - version: int│ │                       │         │
│  │ - retry: Retry    │ │ - definition  │ │ + Created             │         │
│  │ - timeout: Duration│ │ - publishedAt │ │ + Updated             │         │
│  ├───────────────────┤ │ - publishedBy │ │ + Published           │         │
│  │ + validate()      │ └───────────────┘ │ + Archived            │         │
│  │ + getStage(id)    │                   │ + Restored            │         │
│  │ + buildDag()      │                   └───────────────────────┘         │
│  └─────────┬─────────┘                                                      │
│            │                                                                 │
│            ▼                                                                 │
│  ┌───────────────────────────────────────────────────────────────────┐     │
│  │                           Stage                                    │     │
│  ├───────────────────────────────────────────────────────────────────┤     │
│  │ - id: String                                                       │     │
│  │ - name: String                                                     │     │
│  │ - type: StageType                                                  │     │
│  │ - config: StageConfig                                              │     │
│  │ - dependsOn: List<String>                                          │     │
│  │ - retryPolicy: RetryPolicy                                         │     │
│  │ - timeout: Duration                                                │     │
│  │ - resources: ResourceRequirements                                  │     │
│  │ - condition: String (optional skip condition)                      │     │
│  └───────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐    │
│  │   <<enum>>         │  │   <<enum>>         │  │  <<value object>>  │    │
│  │   StageType        │  │  PipelineStatus    │  │     PipelineId     │    │
│  ├────────────────────┤  ├────────────────────┤  ├────────────────────┤    │
│  │ SQL               │  │ DRAFT              │  │ - value: UUID      │    │
│  │ PYTHON            │  │ ACTIVE             │  ├────────────────────┤    │
│  │ DBT               │  │ ARCHIVED           │  │ + generate(): Id   │    │
│  │ SPARK             │  └────────────────────┘  │ + of(String): Id   │    │
│  │ CONTAINER         │                          └────────────────────┘    │
│  └────────────────────┘                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Java Implementation

```java
// Aggregate Root with Event Sourcing
public class Pipeline {
    private final PipelineId id;
    private final TenantId tenantId;
    private String name;
    private PipelineStatus status;
    private int currentVersion;
    private PipelineDefinition definition;
    
    private final List<PipelineEvent> uncommittedEvents = new ArrayList<>();
    
    // Factory method for creation
    public static Pipeline create(TenantId tenantId, ProjectId projectId, 
                                   String name, UserId createdBy) {
        Pipeline pipeline = new Pipeline(PipelineId.generate(), tenantId);
        pipeline.apply(new PipelineEvent.Created(
            pipeline.id, tenantId, projectId, name, createdBy, Instant.now()
        ));
        return pipeline;
    }
    
    // Reconstitute from events
    public static Pipeline fromEvents(PipelineId id, List<PipelineEvent> events) {
        Pipeline pipeline = new Pipeline(id, null);
        events.forEach(pipeline::apply);
        pipeline.uncommittedEvents.clear(); // These are already persisted
        return pipeline;
    }
    
    public void publish() {
        if (status != PipelineStatus.DRAFT && status != PipelineStatus.ACTIVE) {
            throw new IllegalStateException("Cannot publish in state: " + status);
        }
        ValidationResult result = definition.validate();
        if (!result.isValid()) {
            throw new ValidationException(result.getErrors());
        }
        apply(new PipelineEvent.Published(id, currentVersion + 1, Instant.now()));
    }
    
    private void apply(PipelineEvent event) {
        // Apply state change
        switch (event) {
            case PipelineEvent.Created e -> {
                this.tenantId = e.tenantId();
                this.name = e.name();
                this.status = PipelineStatus.DRAFT;
                this.currentVersion = 0;
            }
            case PipelineEvent.Published e -> {
                this.status = PipelineStatus.ACTIVE;
                this.currentVersion = e.version();
            }
            case PipelineEvent.Archived e -> this.status = PipelineStatus.ARCHIVED;
            // ... other events
        }
        uncommittedEvents.add(event);
    }
}

// Sealed interface for events
public sealed interface PipelineEvent {
    PipelineId pipelineId();
    Instant occurredAt();
    
    record Created(PipelineId pipelineId, TenantId tenantId, ProjectId projectId,
                   String name, UserId createdBy, Instant occurredAt) 
        implements PipelineEvent {}
    
    record Updated(PipelineId pipelineId, PipelineDefinition definition, 
                   UserId updatedBy, Instant occurredAt) 
        implements PipelineEvent {}
    
    record Published(PipelineId pipelineId, int version, Instant occurredAt) 
        implements PipelineEvent {}
    
    record Archived(PipelineId pipelineId, UserId archivedBy, Instant occurredAt) 
        implements PipelineEvent {}
}

// Value Object
public record PipelineId(UUID value) {
    public static PipelineId generate() {
        return new PipelineId(UUID.randomUUID());
    }
    
    public static PipelineId of(String value) {
        return new PipelineId(UUID.fromString(value));
    }
}
```

---

## 2. Execution Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EXECUTION SERVICE DOMAIN                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    <<Aggregate Root>>                                │    │
│  │                       Execution                                      │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: ExecutionId                                                    │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - pipelineId: PipelineId                                             │    │
│  │ - pipelineVersion: int                                               │    │
│  │ - status: ExecutionStatus                                            │    │
│  │ - triggerType: TriggerType                                           │    │
│  │ - triggeredBy: UserId (nullable)                                     │    │
│  │ - parameters: Map<String, Object>                                    │    │
│  │ - jobs: List<Job>                                                    │    │
│  │ - startedAt: Instant                                                 │    │
│  │ - completedAt: Instant                                               │    │
│  │ - errorMessage: String                                               │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + start(): void                                                      │    │
│  │ + cancel(): void                                                     │    │
│  │ + onJobCompleted(jobId: JobId, success: boolean): void               │    │
│  │ + retry(): Execution                                                 │    │
│  │ + getReadyJobs(): List<Job>                                          │    │
│  │ + isComplete(): boolean                                              │    │
│  └───────────────────────────┬─────────────────────────────────────────┘    │
│                              │                                               │
│                              │ 1:N                                           │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                            Job                                       │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: JobId                                                          │    │
│  │ - stageId: String                                                    │    │
│  │ - stageName: String                                                  │    │
│  │ - status: JobStatus                                                  │    │
│  │ - runnerId: RunnerId (nullable)                                      │    │
│  │ - attempt: int                                                       │    │
│  │ - maxAttempts: int                                                   │    │
│  │ - queuedAt: Instant                                                  │    │
│  │ - startedAt: Instant                                                 │    │
│  │ - completedAt: Instant                                               │    │
│  │ - output: JobOutput                                                  │    │
│  │ - errorMessage: String                                               │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + queue(): void                                                      │    │
│  │ + assignTo(runnerId: RunnerId): void                                 │    │
│  │ + start(): void                                                      │    │
│  │ + complete(output: JobOutput): void                                  │    │
│  │ + fail(error: String): void                                          │    │
│  │ + retry(): boolean                                                   │    │
│  │ + skip(): void                                                       │    │
│  │ + canRetry(): boolean                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐    │
│  │   <<enum>>         │  │   <<enum>>         │  │   <<enum>>         │    │
│  │ ExecutionStatus    │  │    JobStatus       │  │   TriggerType      │    │
│  ├────────────────────┤  ├────────────────────┤  ├────────────────────┤    │
│  │ PENDING            │  │ PENDING            │  │ MANUAL             │    │
│  │ RUNNING            │  │ QUEUED             │  │ SCHEDULED          │    │
│  │ SUCCEEDED          │  │ RUNNING            │  │ EVENT              │    │
│  │ FAILED             │  │ SUCCEEDED          │  │ API                │    │
│  │ CANCELLED          │  │ FAILED             │  │ WEBHOOK            │    │
│  │ RETRYING           │  │ CANCELLED          │  └────────────────────┘    │
│  └────────────────────┘  │ SKIPPED            │                            │
│                          └────────────────────┘                            │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         Checkpoint                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - executionId: ExecutionId                                           │    │
│  │ - stageId: String                                                    │    │
│  │ - state: Map<String, Object>                                         │    │
│  │ - updatedAt: Instant                                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Java Implementation

```java
public class Execution {
    private final ExecutionId id;
    private ExecutionStatus status = ExecutionStatus.PENDING;
    private final List<Job> jobs = new ArrayList<>();
    
    public void start() {
        if (status != ExecutionStatus.PENDING) {
            throw new IllegalStateException("Cannot start execution in state: " + status);
        }
        status = ExecutionStatus.RUNNING;
        startedAt = Instant.now();
        
        // Queue jobs with no dependencies
        getReadyJobs().forEach(Job::queue);
    }
    
    public List<Job> getReadyJobs() {
        return jobs.stream()
            .filter(job -> job.getStatus() == JobStatus.PENDING)
            .filter(this::allDependenciesSatisfied)
            .toList();
    }
    
    private boolean allDependenciesSatisfied(Job job) {
        return job.getDependencies().stream()
            .allMatch(depStageId -> jobs.stream()
                .filter(j -> j.getStageId().equals(depStageId))
                .allMatch(j -> j.getStatus() == JobStatus.SUCCEEDED));
    }
    
    public void onJobCompleted(JobId jobId, boolean success) {
        Job job = findJob(jobId);
        
        if (!success && job.canRetry()) {
            job.retry();
            return;
        }
        
        if (!success) {
            status = ExecutionStatus.FAILED;
            completedAt = Instant.now();
            return;
        }
        
        // Check if all jobs complete
        if (jobs.stream().allMatch(j -> j.getStatus() == JobStatus.SUCCEEDED)) {
            status = ExecutionStatus.SUCCEEDED;
            completedAt = Instant.now();
        } else {
            // Queue newly ready jobs
            getReadyJobs().forEach(Job::queue);
        }
    }
}

public class Job {
    private JobStatus status = JobStatus.PENDING;
    private int attempt = 1;
    private final int maxAttempts;
    
    public boolean canRetry() {
        return attempt < maxAttempts;
    }
    
    public void retry() {
        if (!canRetry()) {
            throw new IllegalStateException("Max retry attempts reached");
        }
        attempt++;
        status = JobStatus.QUEUED;
        queuedAt = Instant.now();
    }
    
    public void fail(String error) {
        status = JobStatus.FAILED;
        errorMessage = error;
        completedAt = Instant.now();
    }
}
```

---

## 3. Scheduler Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SCHEDULER SERVICE DOMAIN                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         Schedule                                     │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: ScheduleId                                                     │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - pipelineId: PipelineId                                             │    │
│  │ - name: String                                                       │    │
│  │ - cronExpression: CronExpression                                     │    │
│  │ - timezone: ZoneId                                                   │    │
│  │ - parameters: Map<String, Object>                                    │    │
│  │ - isActive: boolean                                                  │    │
│  │ - catchupPolicy: CatchupPolicy                                       │    │
│  │ - nextRunAt: Instant                                                 │    │
│  │ - lastRunAt: Instant                                                 │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + pause(): void                                                      │    │
│  │ + resume(): void                                                     │    │
│  │ + computeNextRun(from: Instant): Instant                             │    │
│  │ + isDue(now: Instant): boolean                                       │    │
│  │ + getMissedRuns(now: Instant): List<Instant>                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      <<interface>>                                   │    │
│  │                         Trigger                                      │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + shouldFire(context: TriggerContext): boolean                       │    │
│  │ + getParameters(): Map<String, Object>                               │    │
│  └───────────────────────────┬─────────────────────────────────────────┘    │
│                              │                                               │
│              ┌───────────────┼───────────────┐                              │
│              │               │               │                              │
│              ▼               ▼               ▼                              │
│  ┌───────────────────┐ ┌───────────────┐ ┌───────────────────┐             │
│  │  KafkaTrigger     │ │ WebhookTrigger│ │   FileSensor      │             │
│  ├───────────────────┤ ├───────────────┤ ├───────────────────┤             │
│  │ - topic: String   │ │ - token: String│ │ - bucket: String │             │
│  │ - filter: Predicate│ │ - rateLimit   │ │ - prefix: String │             │
│  │ - batchSize: int  │ └───────────────┘ │ - pattern: String │             │
│  └───────────────────┘                   └───────────────────┘             │
│                                                                              │
│  ┌────────────────────┐  ┌────────────────────┐                            │
│  │ <<value object>>   │  │   <<enum>>         │                            │
│  │  CronExpression    │  │  CatchupPolicy     │                            │
│  ├────────────────────┤  ├────────────────────┤                            │
│  │ - expression: String│  │ SKIP              │                            │
│  ├────────────────────┤  │ RUN_ALL            │                            │
│  │ + isValid(): bool  │  │ COALESCE           │                            │
│  │ + next(Instant): Instant                   │                            │
│  │ + describe(): String                       │                            │
│  └────────────────────┘  └────────────────────┘                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Runner Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RUNNER SERVICE DOMAIN                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                          Runner                                      │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: RunnerId                                                       │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - hostname: String                                                   │    │
│  │ - version: String                                                    │    │
│  │ - status: RunnerStatus                                               │    │
│  │ - capacity: int                                                      │    │
│  │ - activeJobs: int                                                    │    │
│  │ - labels: Set<String>                                                │    │
│  │ - lastHeartbeat: Instant                                             │    │
│  │ - certificate: RunnerCertificate                                     │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + hasCapacity(): boolean                                             │    │
│  │ + matchesLabels(required: Set<String>): boolean                      │    │
│  │ + assignJob(job: JobAssignment): void                                │    │
│  │ + completeJob(jobId: JobId): void                                    │    │
│  │ + drain(): void                                                      │    │
│  │ + resume(): void                                                     │    │
│  │ + heartbeat(): void                                                  │    │
│  │ + markSuspect(): void                                                │    │
│  │ + markDead(): void                                                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      JobAssignment                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: AssignmentId                                                   │    │
│  │ - runnerId: RunnerId                                                 │    │
│  │ - jobId: JobId                                                       │    │
│  │ - assignedAt: Instant                                                │    │
│  │ - acknowledgedAt: Instant                                            │    │
│  │ - stageConfig: StageConfig                                           │    │
│  │ - secrets: List<SecretRef>                                           │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    RunnerCertificate                                 │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: UUID                                                           │    │
│  │ - serialNumber: String                                               │    │
│  │ - subjectCN: String                                                  │    │
│  │ - issuedAt: Instant                                                  │    │
│  │ - expiresAt: Instant                                                 │    │
│  │ - revokedAt: Instant (nullable)                                      │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + isValid(): boolean                                                 │    │
│  │ + isExpired(): boolean                                               │    │
│  │ + revoke(reason: String): void                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────┐                                                     │
│  │   <<enum>>         │                                                     │
│  │  RunnerStatus      │                                                     │
│  ├────────────────────┤                                                     │
│  │ AVAILABLE          │  ◀── Can accept jobs                               │
│  │ BUSY               │  ◀── At max capacity                               │
│  │ DRAINING           │  ◀── Admin requested drain                         │
│  │ DRAINED            │  ◀── Drain complete                                │
│  │ SUSPECT            │  ◀── Missed 1-2 heartbeats                         │
│  │ DEAD               │  ◀── Missed 3+ heartbeats                          │
│  │ DEREGISTERED       │  ◀── Explicitly removed                            │
│  └────────────────────┘                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Tenant Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TENANT SERVICE DOMAIN                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                           Organization                                       │
│                               │                                              │
│                               │ 1:N                                          │
│                ┌──────────────┼──────────────┐                              │
│                ▼              ▼              ▼                              │
│             Team           User           Role                              │
│               │              │              │                              │
│               │ 1:N          │              │                              │
│               ▼              │              │                              │
│           Project            │              │                              │
│                              │              │                              │
│                              └──────┬───────┘                              │
│                                     │                                       │
│                                     ▼                                       │
│                              TeamMember                                     │
│                           (User + Team + Role)                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                       Organization                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: OrgId                                                          │    │
│  │ - name: String                                                       │    │
│  │ - slug: String                                                       │    │
│  │ - tier: Tier (FREE, TEAM, ENTERPRISE)                                │    │
│  │ - settings: OrgSettings                                              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           User                                       │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: UserId                                                         │    │
│  │ - orgId: OrgId                                                       │    │
│  │ - email: Email                                                       │    │
│  │ - name: String                                                       │    │
│  │ - passwordHash: String                                               │    │
│  │ - mfaSecret: String (encrypted)                                      │    │
│  │ - status: UserStatus                                                 │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + authenticate(password: String): boolean                            │    │
│  │ + verifyMfa(code: String): boolean                                   │    │
│  │ + hasPermission(permission: Permission): boolean                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           Role                                       │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: RoleId                                                         │    │
│  │ - orgId: OrgId (null for system roles)                               │    │
│  │ - name: String                                                       │    │
│  │ - permissions: Set<Permission>                                       │    │
│  │ - isSystem: boolean                                                  │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + hasPermission(p: Permission): boolean                              │    │
│  │ + grant(p: Permission): void                                         │    │
│  │ + revoke(p: Permission): void                                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌────────────────────┐                                                     │
│  │ <<value object>>   │                                                     │
│  │    Permission      │                                                     │
│  ├────────────────────┤                                                     │
│  │ - resource: String │  e.g., "pipelines", "executions"                   │
│  │ - action: String   │  e.g., "read", "write", "delete", "*"              │
│  ├────────────────────┤                                                     │
│  │ + matches(required): boolean                                             │
│  │ + toString(): "pipelines:read"                                           │
│  └────────────────────┘                                                     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Metadata Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         METADATA SERVICE DOMAIN                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                          Dataset                                     │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: DatasetId                                                      │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - name: String                                                       │    │
│  │ - type: DatasetType (TABLE, VIEW, FILE, STREAM)                      │    │
│  │ - sourceSystem: String                                               │    │
│  │ - location: String (fully qualified name)                            │    │
│  │ - description: String                                                │    │
│  │ - owner: UserId                                                      │    │
│  │ - classification: Classification                                     │    │
│  │ - columns: List<Column>                                              │    │
│  │ - statistics: DatasetStatistics                                      │    │
│  │ - qualityScore: QualityScore                                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           Column                                     │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: ColumnId                                                       │    │
│  │ - name: String                                                       │    │
│  │ - dataType: String                                                   │    │
│  │ - description: String                                                │    │
│  │ - isNullable: boolean                                                │    │
│  │ - isPii: boolean                                                     │    │
│  │ - piiType: PiiType                                                   │    │
│  │ - statistics: ColumnStatistics                                       │    │
│  │ - glossaryTerms: List<GlossaryTerm>                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                       LineageEdge                                    │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: LineageEdgeId                                                  │    │
│  │ - sourceDataset: DatasetId                                           │    │
│  │ - targetDataset: DatasetId                                           │    │
│  │ - sourceColumn: String (nullable, for column-level)                  │    │
│  │ - targetColumn: String (nullable)                                    │    │
│  │ - transformation: String                                             │    │
│  │ - pipelineId: PipelineId                                             │    │
│  │ - jobId: JobId                                                       │    │
│  │ - capturedAt: Instant                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                       LineageGraph                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - datasets: Map<DatasetId, Dataset>                                  │    │
│  │ - edges: List<LineageEdge>                                           │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + getUpstream(dataset: DatasetId, depth: int): Set<Dataset>          │    │
│  │ + getDownstream(dataset: DatasetId, depth: int): Set<Dataset>        │    │
│  │ + getPath(from: DatasetId, to: DatasetId): List<LineageEdge>         │    │
│  │ + getImpact(dataset: DatasetId): ImpactAnalysis                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Notification Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       NOTIFICATION SERVICE DOMAIN                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        AlertRule                                     │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: AlertRuleId                                                    │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - pipelineId: PipelineId (nullable for global)                       │    │
│  │ - name: String                                                       │    │
│  │ - condition: AlertCondition                                          │    │
│  │ - severity: Severity                                                 │    │
│  │ - channels: List<NotificationChannel>                                │    │
│  │ - isActive: boolean                                                  │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + evaluate(event: ExecutionEvent): boolean                           │    │
│  │ + fire(event: ExecutionEvent): Alert                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                          Alert                                       │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: AlertId                                                        │    │
│  │ - ruleId: AlertRuleId                                                │    │
│  │ - executionId: ExecutionId                                           │    │
│  │ - severity: Severity                                                 │    │
│  │ - title: String                                                      │    │
│  │ - message: String                                                    │    │
│  │ - status: AlertStatus                                                │    │
│  │ - snoozedUntil: Instant                                              │    │
│  │ - createdAt: Instant                                                 │    │
│  │ - resolvedAt: Instant                                                │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + acknowledge(): void                                                │    │
│  │ + snooze(duration: Duration): void                                   │    │
│  │ + resolve(): void                                                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                  <<interface>>                                       │    │
│  │               NotificationChannel                                    │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + send(alert: Alert): DeliveryResult                                 │    │
│  │ + getType(): ChannelType                                             │    │
│  └───────────────────────────┬─────────────────────────────────────────┘    │
│                              │                                               │
│        ┌─────────────────────┼─────────────────────┐                        │
│        ▼                     ▼                     ▼                        │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐                │
│  │ SlackChannel │     │ EmailChannel │     │PagerDutyChannel│              │
│  ├──────────────┤     ├──────────────┤     ├──────────────┤                │
│  │ - webhookUrl │     │ - recipients │     │ - routingKey │                │
│  │ - channel    │     │ - template   │     │ - severity   │                │
│  └──────────────┘     └──────────────┘     └──────────────┘                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Agent Service

### Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AGENT SERVICE DOMAIN                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                       Observation                                    │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: ObservationId                                                  │    │
│  │ - tenantId: TenantId                                                 │    │
│  │ - executionId: ExecutionId                                           │    │
│  │ - reasoningChain: List<ReasoningStep>                                │    │
│  │ - rootCause: String                                                  │    │
│  │ - confidence: BigDecimal (0.0 - 1.0)                                 │    │
│  │ - createdAt: Instant                                                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      ReasoningStep                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - thought: String                                                    │    │
│  │ - action: ToolCall                                                   │    │
│  │ - observation: String                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      HealingAction                                   │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ - id: HealingActionId                                                │    │
│  │ - observationId: ObservationId                                       │    │
│  │ - actionType: ActionType                                             │    │
│  │ - description: String                                                │    │
│  │ - proposedChange: JsonNode                                           │    │
│  │ - status: ActionStatus (PROPOSED, APPROVED, APPLIED, REJECTED)       │    │
│  │ - appliedBy: UserId                                                  │    │
│  │ - appliedAt: Instant                                                 │    │
│  │ - result: ActionResult                                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                  <<interface>>                                       │    │
│  │                    AgentTool                                         │    │
│  ├─────────────────────────────────────────────────────────────────────┤    │
│  │ + getName(): String                                                  │    │
│  │ + getDescription(): String                                           │    │
│  │ + getParameters(): JsonSchema                                        │    │
│  │ + execute(params: Map<String, Object>): ToolResult                   │    │
│  └───────────────────────────┬─────────────────────────────────────────┘    │
│                              │                                               │
│        ┌─────────────────────┼─────────────────────┐                        │
│        ▼                     ▼                     ▼                        │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │
│  │GetExecutionLogs  │ │  CheckSchema     │ │  ProposeFix      │            │
│  │                  │ │                  │ │                  │            │
│  │ params:          │ │ params:          │ │ params:          │            │
│  │ - executionId    │ │ - datasetId      │ │ - description    │            │
│  │ - stageId        │ │                  │ │ - change         │            │
│  │ - lastNLines     │ │ returns:         │ │                  │            │
│  │                  │ │ - columns        │ │ returns:         │            │
│  │ returns:         │ │ - history        │ │ - actionId       │            │
│  │ - log lines      │ └──────────────────┘ └──────────────────┘            │
│  └──────────────────┘                                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Common Patterns Across Services

### Repository Pattern

```java
public interface PipelineRepository {
    Optional<Pipeline> findById(PipelineId id);
    Pipeline save(Pipeline pipeline);
    void delete(PipelineId id);
    List<Pipeline> findByProjectId(ProjectId projectId, Pageable pageable);
    List<Pipeline> findByTenantId(TenantId tenantId, Pageable pageable);
}
```

### Domain Service Pattern

```java
@Service
public class ExecutionDomainService {
    private final ExecutionRepository executionRepository;
    private final PipelineRepository pipelineRepository;
    private final EventPublisher eventPublisher;
    
    public Execution triggerExecution(PipelineId pipelineId, 
                                       Map<String, Object> params,
                                       TriggerType triggerType,
                                       UserId triggeredBy) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
            .orElseThrow(() -> new PipelineNotFoundException(pipelineId));
        
        Execution execution = Execution.create(
            pipeline, params, triggerType, triggeredBy
        );
        
        execution = executionRepository.save(execution);
        eventPublisher.publish(new ExecutionCreatedEvent(execution));
        
        return execution;
    }
}
```

---

## Interview Questions

**Q: "Why use Value Objects for IDs?"**
> Type safety. `PipelineId` is not accidentally assignable to `ExecutionId`. Method signatures are self-documenting. Parsing and validation happen once at construction.

**Q: "Explain the Aggregate Root pattern."**
> Pipeline is an aggregate root. All modifications to stages, variables, etc. go through Pipeline. This ensures invariants are maintained. We never persist a Stage directly — we persist the Pipeline which contains stages.

**Q: "How do you handle concurrency?"**
> Optimistic locking with version numbers. The repository checks the version on update. If another transaction modified the entity, we get an `OptimisticLockException` and retry.

**Q: "Why sealed interfaces for events?"**
> Compile-time exhaustiveness checking. When pattern matching on `PipelineEvent`, the compiler ensures we handle all cases. Adding a new event type forces us to update all handlers.

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Engineering | Initial class diagrams |
