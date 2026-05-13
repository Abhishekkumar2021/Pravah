---
name: code-review
description: Review Pravah code for correctness, security, and adherence to project standards. Use when reviewing PRs, examining code changes, or when the user asks for a code review. Enforces ADRs, LLD patterns, state machine correctness, and Java 21/Spring Boot conventions.
---

# Code Review for Pravah

## Quick Start

When reviewing code, check in this order:

1. **Documentation alignment** - Does the code match ADRs and LLD?
2. **Correctness** - Logic errors, edge cases, state machine violations
3. **Security** - Multi-tenancy isolation, auth, input validation
4. **Patterns** - DDD, outbox, idempotency
5. **Quality** - Tests, logging, error handling

## Review Checklist

Copy and complete this checklist:

```
## Code Review: [PR/File Name]

### Documentation Alignment
- [ ] Change aligns with `docs/adr/` decisions
- [ ] Follows patterns in `docs/lld/01-design-patterns.md`
- [ ] State transitions match `docs/lld/03-state-machines.md`
- [ ] Database changes match `docs/lld/02-database-erd.md`

### Correctness
- [ ] Logic handles edge cases
- [ ] State machine transitions are valid (no illegal transitions)
- [ ] Null safety (no NPEs)
- [ ] Concurrency handled (optimistic locking where needed)

### Security (Critical for multi-tenant SaaS)
- [ ] Tenant isolation enforced (RLS or explicit tenant_id checks)
- [ ] No cross-tenant data leakage
- [ ] Input validated with Bean Validation
- [ ] No secrets in code or logs

### Patterns
- [ ] Events published via Outbox pattern (not direct Kafka)
- [ ] Kafka consumers are idempotent (check processed_events table)
- [ ] Value objects used (not primitive obsession)
- [ ] Domain logic in aggregates (not services)

### Code Quality
- [ ] Unit tests for business logic
- [ ] Integration tests for DB/Kafka
- [ ] Structured logging with key-value pairs
- [ ] No empty catch blocks
- [ ] No TODO without issue reference

### Java 21 / Spring Boot
- [ ] Records for DTOs and value objects
- [ ] Sealed interfaces for events
- [ ] Constructor injection (no field @Autowired)
- [ ] @Transactional at service layer
```

## Feedback Format

Use severity levels:

| Level | Emoji | Meaning |
|-------|-------|---------|
| Critical | 🔴 | Must fix before merge - bugs, security, data integrity |
| Warning | 🟡 | Should fix - patterns, maintainability, missing tests |
| Suggestion | 🟢 | Consider - style, minor improvements |
| Question | ❓ | Clarification needed |

**Format:**
```
🔴 **Critical**: [File:Line] Brief description
Explanation of why this is critical and how to fix.

🟡 **Warning**: [File:Line] Brief description  
Explanation and suggested fix.
```

## Common Issues

### State Machine Violations
```java
// 🔴 Critical: Invalid state transition
execution.setState(RUNNING);  // Never use setters!

// ✅ Correct: Use domain methods
execution.start();  // Validates transition internally
```

### Outbox Pattern Violations
```java
// 🔴 Critical: Dual-write problem
pipelineRepo.save(pipeline);
kafkaTemplate.send(event);  // Can fail after DB commit!

// ✅ Correct: Same transaction
@Transactional
void create() {
    pipelineRepo.save(pipeline);
    outboxRepo.save(new OutboxEntry(...));  // Same TX
}
```

### Missing Tenant Isolation
```java
// 🔴 Critical: No tenant check
return pipelineRepo.findById(id);

// ✅ Correct: Enforce tenant
return pipelineRepo.findByIdAndTenantId(id, TenantContext.current());
```

### Non-Idempotent Consumer
```java
// 🔴 Critical: Will process duplicates
@KafkaListener(topics = "events")
void handle(Event e) {
    process(e);  // No deduplication!
}

// ✅ Correct: Idempotent
void handle(Event e) {
    if (processedEvents.existsById(e.eventId())) return;
    process(e);
    processedEvents.save(e.eventId());
}
```

## Additional Resources

For detailed standards, see:
- [Pravah ADRs](docs/adr/) - Architecture decisions
- [State Machines](docs/lld/03-state-machines.md) - Valid transitions
- [Design Patterns](docs/lld/01-design-patterns.md) - DDD patterns
- [Cursor Rules](.cursor/rules/) - Coding standards
