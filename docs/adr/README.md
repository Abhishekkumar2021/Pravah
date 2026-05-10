# Architecture Decision Records — Pravah

Architecture Decision Records (ADRs) are short documents that capture a significant architectural decision: the context in which it was made, the decision itself, and the consequences — both positive and negative.

ADRs are immutable. When a decision is reversed or superseded, a new ADR is written and the old one is updated to point to it. This preserves the reasoning history and prevents decisions from being re-litigated without understanding why they were made in the first place.

---

## Format

Each ADR follows this structure:

```
# ADR-NNN: Title

**Status**: Accepted | Deprecated | Superseded by ADR-NNN
**Date**: YYYY-MM-DD

## Context
What is the situation or problem that forced this decision?
What constraints exist?

## Decision
What did we decide to do?

## Consequences
### Positive
### Negative
### Risks & Mitigations

## Alternatives Considered
What did we reject, and why?
```

---

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](ADR-001-microservices-architecture.md) | Microservices Architecture over Monolith | Accepted |
| [ADR-002](ADR-002-kafka-messaging-backbone.md) | Kafka as the Primary Messaging Backbone | Accepted |
| [ADR-003](ADR-003-postgresql-database-per-service.md) | PostgreSQL with Database-per-Service | Accepted |
| [ADR-004](ADR-004-outbox-pattern-event-publishing.md) | Outbox Pattern for Reliable Event Publishing | Accepted |
| [ADR-005](ADR-005-grpc-runner-communication.md) | gRPC for Runner ↔ Cloud Communication | Accepted |
| [ADR-006](ADR-006-pool-multi-tenancy-model.md) | Pool Model for Multi-Tenancy | Accepted |
| [ADR-007](ADR-007-vault-secret-management.md) | HashiCorp Vault for Secret Management | Accepted |
| [ADR-008](ADR-008-mtls-service-to-service.md) | mTLS for Service-to-Service Communication | Accepted |
| [ADR-009](ADR-009-jwt-oauth2-authentication.md) | JWT RS256 + OAuth 2.0 for Authentication | Accepted |
| [ADR-010](ADR-010-kubernetes-helm-argocd.md) | Kubernetes + Helm + Argo CD for Deployment | Accepted |
| [ADR-011](ADR-011-saga-choreography.md) | Saga Pattern (Choreography) for Distributed Transactions | Accepted |
| [ADR-012](ADR-012-redis-caching-locking.md) | Redis for Caching, Distributed Locking & Rate Limiting | Accepted |
| [ADR-013](ADR-013-postgresql-rls-tenant-isolation.md) | PostgreSQL Row-Level Security for Tenant Data Isolation | Accepted |
| [ADR-014](ADR-014-tail-based-sampling.md) | Tail-Based Sampling for Distributed Tracing | Accepted |
| [ADR-015](ADR-015-keda-event-driven-autoscaling.md) | KEDA for Event-Driven Autoscaling | Accepted |
