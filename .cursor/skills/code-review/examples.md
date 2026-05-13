# Code Review Examples

## Example 1: Service Class Review

**File under review:** `PipelineService.java`

```java
@Service
public class PipelineService {
    @Autowired
    private PipelineRepository repository;
    
    @Autowired
    private KafkaTemplate<String, Object> kafka;
    
    public Pipeline create(String name, String tenantId) {
        Pipeline p = new Pipeline();
        p.setName(name);
        p.setTenantId(tenantId);
        p.setStatus(PipelineStatus.DRAFT);
        
        Pipeline saved = repository.save(p);
        kafka.send("pipeline.events", new PipelineCreatedEvent(saved.getId()));
        return saved;
    }
}
```

### Review Feedback

🔴 **Critical**: [Line 3-6] Field injection instead of constructor injection
```java
// Fix: Use constructor injection
private final PipelineRepository repository;
private final OutboxRepository outboxRepo;

public PipelineService(PipelineRepository repository, OutboxRepository outboxRepo) {
    this.repository = repository;
    this.outboxRepo = outboxRepo;
}
```

🔴 **Critical**: [Line 15] Dual-write problem - Kafka send outside transaction
Direct Kafka publishing can fail after DB commit, causing data inconsistency.
```java
// Fix: Use outbox pattern
@Transactional
public Pipeline create(CreatePipelineCommand cmd) {
    Pipeline saved = repository.save(pipeline);
    outboxRepo.save(new OutboxEntry("pipeline.events", event));
    return saved;
}
```

🔴 **Critical**: [Line 8] Using primitive String for tenantId
Loses type safety and validation.
```java
// Fix: Use value object
public Pipeline create(String name, TenantId tenantId) { ... }
```

🟡 **Warning**: [Line 9-12] Pipeline created via setters, not factory method
Domain logic should be in the aggregate, not the service.
```java
// Fix: Factory method in Pipeline
Pipeline p = Pipeline.create(tenantId, name, createdBy);
```

🟡 **Warning**: Missing @Transactional on create method
DB operations should be transactional.

🟡 **Warning**: No input validation
Add Bean Validation to a request DTO.

---

## Example 2: Kafka Consumer Review

**File under review:** `RunEventHandler.java`

```java
@Component
public class RunEventHandler {
    @Autowired
    private NotificationService notificationService;
    
    @KafkaListener(topics = "pravah.run.events")
    public void handle(RunEvent event) {
        if (event instanceof RunFailedEvent failed) {
            notificationService.notifyRunFailed(failed.runId());
        }
    }
}
```

### Review Feedback

🔴 **Critical**: [Line 7-11] Non-idempotent consumer
If the same event is delivered twice (Kafka at-least-once), notifications will be sent twice.
```java
// Fix: Add idempotency
@KafkaListener(topics = "pravah.run.events")
@Transactional
public void handle(RunEvent event, Acknowledgment ack) {
    if (processedEvents.existsById(event.eventId())) {
        ack.acknowledge();
        return;
    }
    
    if (event instanceof RunFailedEvent failed) {
        notificationService.notifyRunFailed(failed.runId());
    }
    
    processedEvents.save(new ProcessedEvent(event.eventId()));
    ack.acknowledge();
}
```

🟡 **Warning**: [Line 3] Field injection
Use constructor injection.

🟡 **Warning**: No error handling
Add try-catch with logging and consider DLT configuration.

---

## Example 3: State Transition Review

**File under review:** `Execution.java`

```java
public class Execution {
    private ExecutionStatus status;
    
    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }
    
    public void complete(boolean success) {
        this.status = success ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
    }
}
```

### Review Feedback

🔴 **Critical**: [Line 5-7] Public setState allows invalid transitions
Anyone can set any state, bypassing the state machine.
```java
// Fix: Remove setter, use transition methods
public void start() {
    this.status = this.status.onStart();  // Validates transition
    this.startedAt = Instant.now();
}
```

🔴 **Critical**: [Line 9-11] complete() doesn't validate current state
Can complete from PENDING (invalid) or complete twice.
```java
// Fix: Validate state
public void complete(boolean success) {
    if (status != ExecutionStatus.RUNNING) {
        throw new InvalidStateTransitionException("Execution", id, status, "complete");
    }
    this.status = success ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
    this.completedAt = Instant.now();
}
```

🟡 **Warning**: No domain events published on state change
State changes should emit events for other services.
```java
public void complete(boolean success) {
    // ... validation and state change ...
    if (success) {
        addEvent(new ExecutionSucceededEvent(id, completedAt));
    } else {
        addEvent(new ExecutionFailedEvent(id, failureReason, completedAt));
    }
}
```

---

## Example 4: Repository Review (Good Example)

**File under review:** `JpaPipelineRepository.java`

```java
@Repository
public class JpaPipelineRepository implements PipelineRepository {
    private final PipelineJpaRepository jpaRepo;
    private final PipelineMapper mapper;
    
    public JpaPipelineRepository(PipelineJpaRepository jpaRepo, PipelineMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }
    
    @Override
    public Optional<Pipeline> findByIdAndTenantId(PipelineId id, TenantId tenantId) {
        return jpaRepo.findByIdAndTenantId(id.value(), tenantId.value())
            .map(mapper::toDomain);
    }
    
    @Override
    @Transactional
    public Pipeline save(Pipeline pipeline) {
        var entity = mapper.toEntity(pipeline);
        var saved = jpaRepo.save(entity);
        return mapper.toDomain(saved);
    }
}
```

### Review Feedback

🟢 **Good**: Constructor injection used correctly

🟢 **Good**: Tenant ID included in query (security)

🟢 **Good**: Domain objects returned (not JPA entities)

🟢 **Good**: Mapper separates domain from persistence

🟡 **Suggestion**: [Line 17] Consider removing @Transactional from repository
Transaction should be at service layer for broader scope.

---

## Example 5: Value Object Review (Good Example)

**File under review:** `TenantId.java`

```java
public record TenantId(
    @NotBlank
    @Pattern(regexp = "^[a-z][a-z0-9-]{1,61}[a-z0-9]$")
    String value
) {
    public TenantId {
        Objects.requireNonNull(value, "Tenant ID cannot be null");
        if (!value.matches("^[a-z][a-z0-9-]{1,61}[a-z0-9]$")) {
            throw new IllegalArgumentException("Invalid tenant ID format");
        }
    }
    
    public static TenantId of(String value) {
        return new TenantId(value);
    }
}
```

### Review Feedback

🟢 **Good**: Immutable record

🟢 **Good**: Validation in compact constructor

🟢 **Good**: Factory method for clean API

🟢 **Good**: Bean Validation annotations for framework integration

❓ **Question**: Is the regex duplicated intentionally between annotation and constructor?
Consider extracting to a constant if both are needed.
