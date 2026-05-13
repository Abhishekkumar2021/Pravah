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

```mermaid
classDiagram
    class Pipeline {
        <<Aggregate Root>>
        -PipelineId id
        -TenantId tenantId
        -ProjectId projectId
        -String name
        -String description
        -PipelineStatus status
        -int currentVersion
        -PipelineDefinition definition
        -Instant createdAt
        -UserId createdBy
        -List~PipelineEvent~ uncommittedEvents
        +publish() void
        +updateDefinition(PipelineDefinition) void
        +archive() void
        +restore() void
        +validate() ValidationResult
        #apply(PipelineEvent) void
        +getUncommittedEvents() List~PipelineEvent~
        +markEventsAsCommitted() void
    }
    
    class PipelineDefinition {
        -List~Stage~ stages
        -Map~String,String~ variables
        -RetryPolicy retry
        -Duration timeout
        +validate() ValidationResult
        +getStage(String) Stage
        +buildDag() DAG
    }
    
    class PipelineVersion {
        -UUID id
        -int version
        -PipelineDefinition definition
        -Instant publishedAt
        -UserId publishedBy
    }
    
    class PipelineEvent {
        <<sealed interface>>
        +pipelineId() PipelineId
        +occurredAt() Instant
    }
    
    class Stage {
        -String id
        -String name
        -StageType type
        -StageConfig config
        -List~String~ dependsOn
        -RetryPolicy retryPolicy
        -Duration timeout
        -ResourceRequirements resources
        -String condition
    }
    
    class PipelineId {
        <<Value Object>>
        -UUID value
        +generate() PipelineId
        +of(String) PipelineId
    }
    
    class PipelineStatus {
        <<enumeration>>
        DRAFT
        ACTIVE
        ARCHIVED
    }
    
    class StageType {
        <<enumeration>>
        SQL
        PYTHON
        DBT
        SPARK
        CONTAINER
    }
    
    Pipeline "1" *-- "1" PipelineDefinition
    Pipeline "1" *-- "*" PipelineVersion
    Pipeline "1" *-- "*" PipelineEvent
    PipelineDefinition "1" *-- "*" Stage
    Pipeline --> PipelineId
    Pipeline --> PipelineStatus
    Stage --> StageType
```

### Java Implementation

```java
// Aggregate Root with Event Sourcing
public class Pipeline {
    private final PipelineId id;
    private TenantId tenantId; // Initialized in apply for Created event
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

```mermaid
classDiagram
    class Execution {
        <<Aggregate Root>>
        -ExecutionId id
        -TenantId tenantId
        -PipelineId pipelineId
        -int pipelineVersion
        -ExecutionStatus status
        -TriggerType triggerType
        -UserId triggeredBy
        -Map~String,Object~ parameters
        -List~Job~ jobs
        -Instant startedAt
        -Instant completedAt
        -String errorMessage
        +start() void
        +cancel() void
        +onJobCompleted(JobId, boolean) void
        +retry() Execution
        +getReadyJobs() List~Job~
        +isComplete() boolean
    }
    
    class Job {
        -JobId id
        -String stageId
        -String stageName
        -JobStatus status
        -RunnerId runnerId
        -int attempt
        -int maxAttempts
        -Instant queuedAt
        -Instant startedAt
        -Instant completedAt
        -JobOutput output
        -String errorMessage
        +queue() void
        +assignTo(RunnerId) void
        +start() void
        +complete(JobOutput) void
        +fail(String) void
        +retry() boolean
        +skip() void
        +canRetry() boolean
    }
    
    class Checkpoint {
        -ExecutionId executionId
        -String stageId
        -Map~String,Object~ state
        -Instant updatedAt
    }
    
    class ExecutionStatus {
        <<enumeration>>
        PENDING
        RUNNING
        SUCCEEDED
        FAILED
        CANCELLED
        RETRYING
    }
    
    class JobStatus {
        <<enumeration>>
        PENDING
        QUEUED
        RUNNING
        SUCCEEDED
        FAILED
        CANCELLED
        SKIPPED
    }
    
    class TriggerType {
        <<enumeration>>
        MANUAL
        SCHEDULED
        EVENT
        API
        WEBHOOK
    }
    
    Execution "1" *-- "*" Job
    Execution "1" *-- "*" Checkpoint
    Execution --> ExecutionStatus
    Execution --> TriggerType
    Job --> JobStatus
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

```mermaid
classDiagram
    class Schedule {
        -ScheduleId id
        -TenantId tenantId
        -PipelineId pipelineId
        -String name
        -CronExpression cronExpression
        -ZoneId timezone
        -Map~String,Object~ parameters
        -boolean isActive
        -CatchupPolicy catchupPolicy
        -Instant nextRunAt
        -Instant lastRunAt
        +pause() void
        +resume() void
        +computeNextRun(Instant) Instant
        +isDue(Instant) boolean
        +getMissedRuns(Instant) List~Instant~
    }
    
    class Trigger {
        <<interface>>
        +shouldFire(TriggerContext) boolean
        +getParameters() Map~String,Object~
    }
    
    class KafkaTrigger {
        -String topic
        -Predicate filter
        -int batchSize
    }
    
    class WebhookTrigger {
        -String token
        -RateLimit rateLimit
    }
    
    class FileSensor {
        -String bucket
        -String prefix
        -String pattern
    }
    
    class CronExpression {
        <<Value Object>>
        -String expression
        +isValid() boolean
        +next(Instant) Instant
        +describe() String
    }
    
    class CatchupPolicy {
        <<enumeration>>
        SKIP
        RUN_ALL
        COALESCE
    }
    
    Trigger <|.. KafkaTrigger
    Trigger <|.. WebhookTrigger
    Trigger <|.. FileSensor
    Schedule --> CronExpression
    Schedule --> CatchupPolicy
```

---

## 4. Runner Service

### Domain Model

```mermaid
classDiagram
    class Runner {
        -RunnerId id
        -TenantId tenantId
        -String hostname
        -String version
        -RunnerStatus status
        -int capacity
        -int activeJobs
        -Set~String~ labels
        -Instant lastHeartbeat
        -RunnerCertificate certificate
        +hasCapacity() boolean
        +matchesLabels(Set~String~) boolean
        +assignJob(JobAssignment) void
        +completeJob(JobId) void
        +drain() void
        +resume() void
        +heartbeat() void
        +markSuspect() void
        +markDead() void
    }
    
    class JobAssignment {
        -AssignmentId id
        -RunnerId runnerId
        -JobId jobId
        -Instant assignedAt
        -Instant acknowledgedAt
        -StageConfig stageConfig
        -List~SecretRef~ secrets
    }
    
    class RunnerCertificate {
        -UUID id
        -String serialNumber
        -String subjectCN
        -Instant issuedAt
        -Instant expiresAt
        -Instant revokedAt
        +isValid() boolean
        +isExpired() boolean
        +revoke(String) void
    }
    
    class RunnerStatus {
        <<enumeration>>
        AVAILABLE
        BUSY
        DRAINING
        DRAINED
        SUSPECT
        DEAD
        DEREGISTERED
    }
    
    Runner "1" *-- "1" RunnerCertificate
    Runner "1" o-- "*" JobAssignment
    Runner --> RunnerStatus
    
```

---

## 5. Tenant Service

### Domain Model

```mermaid
classDiagram
    class Organization {
        -OrgId id
        -String name
        -String slug
        -Tier tier
        -OrgSettings settings
    }
    
    class Team {
        -TeamId id
        -OrgId orgId
        -String name
        -TeamSettings settings
    }
    
    class Project {
        -ProjectId id
        -TeamId teamId
        -String name
        -String description
    }
    
    class User {
        -UserId id
        -OrgId orgId
        -Email email
        -String name
        -String passwordHash
        -String mfaSecret
        -UserStatus status
        +authenticate(String) boolean
        +verifyMfa(String) boolean
        +hasPermission(Permission) boolean
    }
    
    class Role {
        -RoleId id
        -OrgId orgId
        -String name
        -Set~Permission~ permissions
        -boolean isSystem
        +hasPermission(Permission) boolean
        +grant(Permission) void
        +revoke(Permission) void
    }
    
    class TeamMember {
        -UserId userId
        -TeamId teamId
        -RoleId roleId
    }
    
    class Permission {
        <<Value Object>>
        -String resource
        -String action
        +matches(Permission) boolean
        +toString() String
    }
    
    class Tier {
        <<enumeration>>
        FREE
        TEAM
        ENTERPRISE
    }
    
    Organization "1" *-- "*" Team
    Organization "1" *-- "*" User
    Organization "1" *-- "*" Role
    Team "1" *-- "*" Project
    Team "1" *-- "*" TeamMember
    TeamMember --> User
    TeamMember --> Role
    Organization --> Tier
    Role "1" *-- "*" Permission
```

---

## 6. Metadata Service

### Domain Model

```mermaid
classDiagram
    class Dataset {
        -DatasetId id
        -TenantId tenantId
        -String name
        -DatasetType type
        -String sourceSystem
        -String location
        -String description
        -UserId owner
        -Classification classification
        -List~Column~ columns
        -DatasetStatistics statistics
        -QualityScore qualityScore
    }
    
    class Column {
        -ColumnId id
        -String name
        -String dataType
        -String description
        -boolean isNullable
        -boolean isPii
        -PiiType piiType
        -ColumnStatistics statistics
        -List~GlossaryTerm~ glossaryTerms
    }
    
    class LineageEdge {
        -LineageEdgeId id
        -DatasetId sourceDataset
        -DatasetId targetDataset
        -String sourceColumn
        -String targetColumn
        -String transformation
        -PipelineId pipelineId
        -JobId jobId
        -Instant capturedAt
    }
    
    class LineageGraph {
        -Map~DatasetId,Dataset~ datasets
        -List~LineageEdge~ edges
        +getUpstream(DatasetId, int) Set~Dataset~
        +getDownstream(DatasetId, int) Set~Dataset~
        +getPath(DatasetId, DatasetId) List~LineageEdge~
        +getImpact(DatasetId) ImpactAnalysis
    }
    
    class DatasetType {
        <<enumeration>>
        TABLE
        VIEW
        FILE
        STREAM
    }
    
    Dataset "1" *-- "*" Column
    LineageEdge --> Dataset : source
    LineageEdge --> Dataset : target
    LineageGraph "1" *-- "*" Dataset
    LineageGraph "1" *-- "*" LineageEdge
    Dataset --> DatasetType
```

---

## 7. Notification Service

### Domain Model

```mermaid
classDiagram
    class AlertRule {
        -AlertRuleId id
        -TenantId tenantId
        -PipelineId pipelineId
        -String name
        -AlertCondition condition
        -Severity severity
        -List~NotificationChannel~ channels
        -boolean isActive
        +evaluate(ExecutionEvent) boolean
        +fire(ExecutionEvent) Alert
    }
    
    class Alert {
        -AlertId id
        -AlertRuleId ruleId
        -ExecutionId executionId
        -Severity severity
        -String title
        -String message
        -AlertStatus status
        -Instant snoozedUntil
        -Instant createdAt
        -Instant resolvedAt
        +acknowledge() void
        +snooze(Duration) void
        +resolve() void
    }
    
    class NotificationChannel {
        <<interface>>
        +send(Alert) DeliveryResult
        +getType() ChannelType
    }
    
    class SlackChannel {
        -String webhookUrl
        -String channel
    }
    
    class EmailChannel {
        -List~String~ recipients
        -String template
    }
    
    class PagerDutyChannel {
        -String routingKey
        -Severity severity
    }
    
    class Severity {
        <<enumeration>>
        INFO
        WARNING
        ERROR
        CRITICAL
    }
    
    class AlertStatus {
        <<enumeration>>
        ACTIVE
        ACKNOWLEDGED
        SNOOZED
        RESOLVED
    }
    
    AlertRule "1" *-- "*" NotificationChannel
    AlertRule "1" o-- "*" Alert
    NotificationChannel <|.. SlackChannel
    NotificationChannel <|.. EmailChannel
    NotificationChannel <|.. PagerDutyChannel
    AlertRule --> Severity
    Alert --> AlertStatus
```

---

## 8. Agent Service

### Domain Model

```mermaid
classDiagram
    class Observation {
        -ObservationId id
        -TenantId tenantId
        -ExecutionId executionId
        -List~ReasoningStep~ reasoningChain
        -String rootCause
        -BigDecimal confidence
        -Instant createdAt
    }
    
    class ReasoningStep {
        -String thought
        -ToolCall action
        -String observation
    }
    
    class HealingAction {
        -HealingActionId id
        -ObservationId observationId
        -ActionType actionType
        -String description
        -JsonNode proposedChange
        -ActionStatus status
        -UserId appliedBy
        -Instant appliedAt
        -ActionResult result
    }
    
    class AgentTool {
        <<interface>>
        +getName() String
        +getDescription() String
        +getParameters() JsonSchema
        +execute(Map~String,Object~) ToolResult
    }
    
    class GetExecutionLogs {
        +executionId: String
        +stageId: String
        +lastNLines: int
    }
    
    class CheckSchema {
        +datasetId: String
    }
    
    class ProposeFix {
        +description: String
        +change: JsonNode
    }
    
    class ActionStatus {
        <<enumeration>>
        PROPOSED
        APPROVED
        APPLIED
        REJECTED
    }
    
    Observation "1" *-- "*" ReasoningStep
    Observation "1" o-- "*" HealingAction
    AgentTool <|.. GetExecutionLogs
    AgentTool <|.. CheckSchema
    AgentTool <|.. ProposeFix
    HealingAction --> ActionStatus
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
| 1.1 | 2026-05-13 | Engineering | Updated to Mermaid diagrams |
