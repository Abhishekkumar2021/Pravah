# Detailed Code Review Checks

## Java 21 Features

### Records
```java
// ✅ Use records for immutable data
public record PipelineId(UUID value) {
    public PipelineId {
        Objects.requireNonNull(value);
    }
}

// 🔴 Don't use mutable classes for value objects
public class PipelineId {
    private UUID value;  // Mutable!
    public void setValue(UUID v) { this.value = v; }
}
```

### Sealed Interfaces
```java
// ✅ Exhaustive event handling
public sealed interface PipelineEvent permits Created, Updated, Archived {}

switch (event) {
    case Created c -> handle(c);
    case Updated u -> handle(u);
    case Archived a -> handle(a);
    // Compiler enforces all cases
}

// 🔴 Open hierarchy loses type safety
public interface PipelineEvent {}  // Anyone can implement
```

### Pattern Matching
```java
// ✅ Pattern matching with guards
if (obj instanceof Pipeline p && p.isActive()) {
    process(p);
}

// 🟡 Old style (functional but verbose)
if (obj instanceof Pipeline) {
    Pipeline p = (Pipeline) obj;
    if (p.isActive()) process(p);
}
```

---

## Spring Boot Conventions

### Dependency Injection
```java
// ✅ Constructor injection
@Service
public class PipelineService {
    private final PipelineRepository repo;
    
    public PipelineService(PipelineRepository repo) {
        this.repo = repo;
    }
}

// 🔴 Field injection (hard to test)
@Service
public class PipelineService {
    @Autowired
    private PipelineRepository repo;
}
```

### Transaction Boundaries
```java
// ✅ Service layer transactions
@Service
public class PipelineService {
    @Transactional
    public Pipeline create(CreateCommand cmd) {
        // All DB operations in one TX
    }
}

// 🔴 Repository-level transactions
@Repository
public class PipelineRepository {
    @Transactional  // Too fine-grained
    public Pipeline save(Pipeline p) { ... }
}
```

### Validation
```java
// ✅ Bean Validation on DTOs
public record CreatePipelineRequest(
    @NotBlank String name,
    @NotNull @Valid PipelineDefinition definition
) {}

// 🔴 Manual validation scattered in code
public void create(Request req) {
    if (req.getName() == null || req.getName().isBlank()) {
        throw new IllegalArgumentException("Name required");
    }
    // Repeated everywhere
}
```

---

## Multi-Tenancy Security

### Database Queries
```java
// ✅ Always include tenant
@Query("SELECT p FROM Pipeline p WHERE p.id = :id AND p.tenantId = :tenantId")
Optional<Pipeline> findByIdAndTenantId(PipelineId id, TenantId tenantId);

// 🔴 Missing tenant = cross-tenant data leak
@Query("SELECT p FROM Pipeline p WHERE p.id = :id")
Optional<Pipeline> findById(PipelineId id);
```

### RLS (Row-Level Security)
```java
// ✅ Set tenant context before queries
@Component
public class TenantFilter implements Filter {
    public void doFilter(...) {
        String tenant = extractTenant(request);
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement()
                .execute("SET app.current_tenant = '" + tenant + "'");
        }
    }
}
```

### Logging (no PII/secrets)
```java
// ✅ Safe logging
log.info("Pipeline created", kv("pipelineId", id), kv("tenantId", tenant));

// 🔴 Leaking sensitive data
log.info("User logged in: email={}, password={}", email, password);
log.debug("Request body: {}", requestWithSecrets);
```

---

## Event-Driven Patterns

### Outbox Table
```sql
-- ✅ Outbox entry structure
CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP  -- NULL until published
);
```

### Idempotent Consumer
```java
// ✅ Full idempotency pattern
@KafkaListener(topics = "pravah.pipeline.events")
@Transactional
public void handle(PipelineEvent event, Acknowledgment ack) {
    // 1. Check if processed
    if (processedEvents.existsById(event.eventId())) {
        log.info("Duplicate event", kv("eventId", event.eventId()));
        ack.acknowledge();
        return;
    }
    
    // 2. Process
    processEvent(event);
    
    // 3. Mark processed (same TX)
    processedEvents.save(new ProcessedEvent(event.eventId()));
    
    // 4. Manual ack
    ack.acknowledge();
}
```

### Dead Letter Topic
```java
// ✅ Handle poison messages
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
    return new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(template),
        new FixedBackOff(1000L, 3)  // 3 retries
    );
}
```

---

## State Machine Correctness

### Check Against Documentation
Before approving state changes, verify against `docs/lld/03-state-machines.md`:

**Pipeline States**: DRAFT → ACTIVE → ARCHIVED
**Run States**: PENDING → RUNNING → SUCCEEDED/FAILED/CANCELLED
**Job States**: PENDING → QUEUED → ASSIGNED → RUNNING → SUCCEEDED/FAILED
**Runner States**: OFFLINE → ONLINE → BUSY → DRAINING

### Implementation Pattern
```java
// ✅ Enum-based state machine
public enum RunStatus {
    PENDING {
        @Override
        public RunStatus onStart() { return RUNNING; }
    },
    RUNNING {
        @Override
        public RunStatus onSuccess() { return SUCCEEDED; }
        @Override
        public RunStatus onFailure() { return FAILED; }
    },
    SUCCEEDED,  // Terminal
    FAILED;     // Terminal
    
    // Default throws for invalid transitions
    public RunStatus onStart() { throw illegal("start"); }
    public RunStatus onSuccess() { throw illegal("success"); }
}
```

---

## Testing Requirements

### Unit Tests
- Business logic in domain classes
- State transitions
- Value object validation
- >80% coverage for new code

### Integration Tests
- Database operations with Testcontainers
- Kafka producers/consumers
- gRPC services
- Full request/response cycles

### Test Naming
```java
// ✅ Descriptive names
@Test
void createPipeline_withValidInput_returnsSavedPipeline() {}

@Test
void createPipeline_withDuplicateName_throwsConflictException() {}

// 🔴 Vague names
@Test
void testCreate() {}

@Test
void test1() {}
```

---

## Error Handling

### Domain Exceptions
```java
// ✅ Specific exceptions with context
throw new PipelineNotFoundException(pipelineId);
throw new InvalidStateTransitionException("Run", runId, SUCCEEDED, "start");

// 🔴 Generic exceptions
throw new RuntimeException("Not found");
throw new Exception("Error");
```

### Never Swallow
```java
// 🔴 Critical: Silent failure
try {
    process();
} catch (Exception e) {
    // Nothing - bug hider
}

// 🔴 Bad: printStackTrace
catch (Exception e) {
    e.printStackTrace();
}

// ✅ Log and handle appropriately
catch (SpecificException e) {
    log.error("Processing failed", kv("id", id), e);
    throw new ServiceException("Processing failed", e);
}
```
