# ADR-011: Saga Pattern (Choreography) for Distributed Transactions

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

In a microservices system, some business operations span multiple services and must be treated as logically atomic — either all steps succeed or all steps are rolled back. In Pravah, the most prominent example is **pipeline execution initialization**:

1. **Scheduler** triggers a pipeline and publishes `pipeline.triggered`
2. **Execution Service** creates an `Execution` record and all child `Job` records, then publishes `jobs.created`
3. **Runner Service** checks capacity and reserves runner slots, then publishes `runners.reserved`
4. **Execution Service** transitions jobs to `ASSIGNED` state and dispatches them to runners

If Step 3 fails (no runner capacity available), Steps 1 and 2 must be rolled back — the Execution and Job records must be cleaned up, and the scheduler must know the trigger failed.

In a monolith, this would be a single database transaction: all-or-nothing, with the database handling rollback. In a microservices system with separate databases per service (ADR-003), there is no spanning transaction. Each step commits locally. Failures after a local commit require explicit compensating actions.

**Two approaches exist for distributed saga coordination:**

- **Orchestration**: a central "saga orchestrator" service calls each participant in sequence, handles failures, and issues compensating calls. The orchestrator is the single source of truth for saga state.
- **Choreography**: each service listens to events and reacts by publishing new events. There is no central coordinator. Compensating actions are triggered by failure events.

---

## Decision

**Saga pattern with choreography** for distributed transaction coordination in Pravah.

**Rationale for choreography over orchestration:**

Choreography aligns with Pravah's event-driven architecture (Kafka as the backbone, ADR-002). Services are already publishing events for every state transition. Adding choreography means using those same events as the coordination mechanism — no additional infrastructure, no new service.

Orchestration would require a new "orchestrator service" that knows about every participant and their APIs. This orchestrator becomes a coupling point: every change to a participant's interface requires updating the orchestrator. It also becomes a single point of failure.

**Pipeline execution initialization saga (happy path):**

```
Scheduler ──publish──▶ pravah.pipeline.triggered
                               │
             Execution Service ◀─ consumes
             Creates Execution + Jobs (local tx)
             ──publish──▶ pravah.jobs.created
                               │
             Runner Service ◀─ consumes
             Reserves runner slots (local tx)
             ──publish──▶ pravah.runners.reserved
                               │
             Execution Service ◀─ consumes
             Transitions jobs → ASSIGNED
             ──publish──▶ pravah.jobs.assigned
                               │
             Runner Service ◀─ consumes
             Sends jobs to runners via gRPC
```

**Failure and compensation (Runner Service fails at Step 3):**

```
Runner Service fails to reserve capacity
──publish──▶ pravah.runners.reservation.failed
                    │
   Execution Service ◀─ consumes
   Marks Execution as FAILED
   Deletes orphan Job records (compensating action)
   ──publish──▶ pravah.execution.failed
                    │
   Scheduler ◀─ consumes
   Records failed trigger, schedules retry if configured
```

**Idempotency as a prerequisite:**

Choreography relies on at-least-once delivery from Kafka. Every event handler must be idempotent:

```java
@KafkaListener(topics = "pravah.runners.reserved")
@Transactional
public void onRunnersReserved(RunnersReservedEvent event) {
    // Idempotency check: if already assigned, skip
    if (executionRepository.isAlreadyAssigned(event.getExecutionId())) {
        log.info("Already assigned, skipping duplicate event: {}", event);
        return;
    }
    // ... proceed with assignment
}
```

**Saga state visibility:**

Without a central orchestrator, tracking the state of a distributed saga requires correlating events. Pravah's Execution record acts as the saga state store:

```sql
-- Execution record reflects aggregate saga state
SELECT id, status, pipeline_id, tenant_id,
       triggered_at, assigned_at, started_at, completed_at, failed_at,
       failure_reason
FROM executions
WHERE id = ?;
```

Every saga participant updates the Execution record's status as it processes its step. The Execution record is the observable state of the in-flight saga.

---

## Consequences

### Positive

- **No additional infrastructure**: choreography uses the existing Kafka topics that are already in place for event-driven communication. No new services to operate.
- **Loose coupling**: each participant only knows the events it produces and consumes. The Scheduler does not know that the Runner Service exists — it only publishes `pipeline.triggered` and listens for `execution.failed` or `execution.completed`.
- **Independent deployability**: adding a new participant to the saga (e.g., a notification service that emails the user when a pipeline fails) requires only that the new service subscribes to the relevant events. No changes to existing services.
- **Natural integration with Kafka's at-least-once delivery**: choreography embraces at-least-once semantics by requiring idempotent handlers. This is the right model for Kafka.

### Negative

- **Harder to reason about**: the saga flow is implicit in the event subscriptions. There is no single place to see "here is the sequence of steps for pipeline execution initialization." Tracing a saga requires correlating events across topics and services — Jaeger distributed tracing (Phase 4 theory) is essential.
- **Cycle risk**: if Service A reacts to an event from Service B, and that reaction triggers an event that causes Service B to react again, an infinite loop is possible. Event handlers must be designed carefully to avoid cycles. Idempotency checks and state guards break cycles.
- **Compensating transactions must be designed explicitly**: in a database transaction, rollback is automatic. In a saga, every forward step must have a corresponding compensating action (e.g., "create execution" ↔ "delete execution"). These must be designed, tested, and maintained.
- **Saga completion is eventually consistent**: the outcome of the saga is not known until all participants have processed their events. During the in-flight window, the system is in an intermediate state. This is acceptable for Pravah (pipeline initialization takes seconds) but requires careful UI handling (show "initializing" state, not "failed").

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Compensating event never published (service crashes after local commit) | Outbox pattern (ADR-004) ensures events are published even if the service crashes between the local commit and the Kafka publish |
| Infinite loop between service A and B | State machine guards: each handler checks current state before acting; an event for a step that has already completed is ignored |
| Saga stuck in intermediate state (partner service is down) | Timeout-based recovery: a monitoring job detects executions stuck in `INITIALIZING` for > 5 minutes and triggers a cleanup saga |
| Debugging a failed saga | All saga events carry `execution_id` as a correlation ID; Jaeger traces group all saga events under a single trace; Kibana log search by `execution_id` |

---

## Alternatives Considered

### Saga Orchestration (Central Coordinator)

A dedicated "Pipeline Execution Orchestrator" service drives the saga: calls the Execution Service to create records, then calls the Runner Service to reserve capacity, then handles failure by calling compensating APIs.

Rejected because:
- Creates a new service that must be operated, scaled, and maintained
- The orchestrator must know the API of every participant — tight coupling
- The orchestrator becomes a bottleneck for all pipeline execution initializations
- Does not align with the event-driven, Kafka-first architecture already in place

Choreography is appropriate for Pravah because the saga steps are clearly sequenced, participants are well-defined, and the Kafka event stream is already the coordination medium.

### Two-Phase Commit (2PC)

A distributed protocol that coordinates a single atomic commit across multiple databases.

Rejected definitively. See Phase 1 theory (Two-Phase Commit) for the full analysis. In brief: 2PC blocks all participants if the coordinator crashes, requires all participants to support the XA protocol (PostgreSQL supports it; Kafka does not), and introduces a distributed lock that degrades throughput for all concurrent operations. The Saga pattern, despite requiring explicit compensation logic, produces a more resilient and scalable system.

### Process Manager Pattern

A variant of orchestration where a stateful process manager (stored in a database) tracks saga state and drives transitions. Similar to orchestration but with explicit state storage.

Considered as a middle ground. Not adopted in favor of the simpler choreography approach. If the saga topology becomes significantly more complex (more than 6 participants, complex branching), a process manager would be revisited.
