# Pravah Implementation Status

> **Source of truth** for what is built in this repository vs what is documented as the long-term target architecture.  
> Update this file whenever you ship or stub a user-facing capability.  
> **Last updated:** 2026-05-17

---

## How to read this document

| Status | Meaning |
|--------|---------|
| **Implemented** | Runnable code, tests, and (where applicable) UI or API exposed via gateway |
| **Partial** | Core path works; gaps remain vs epic acceptance criteria |
| **Stub** | Spring Boot module boots; no business APIs |
| **Planned** | Described in HLA / ADRs / epics only |

The [High-Level Architecture](architecture/high-level-architecture.md) describes the **target** platform. This document describes the **current repo**.

---

## Summary

| Area | Status | Notes |
|------|--------|-------|
| Documentation (theory, ADRs, product, LLD) | **Complete** | 75 theory chapters, 33 ADRs, 9 LLD docs |
| Engineering foundation | **Partial** | Gradle multi-module, CI, `backend/docker-compose.yml`, local scripts |
| Core control plane | **Partial** | tenant, pipeline, execution, scheduler, gateway |
| Web alpha (EPIC-12) | **Partial** | REST + WebSocket; no GraphQL read model |
| Remaining microservices | **Stub** | agent, connect, metadata, notification, runner-service, graphql |
| Runner fleet + gRPC | **Planned** | `runner/` CLI skeleton; no live runner dispatch |
| Lineage, catalog, AI agent | **Planned** | No Elasticsearch / OpenLineage stack in repo |

**Rough progress vs full product vision (~180 user stories): ~30–35%.**  
**Alpha MVP path (create pipeline → run → logs → cancel → schedule): ~50–55%.**

---

## Backend services

| Service | Port (local) | Status | Capabilities |
|---------|--------------|--------|--------------|
| **gateway** | 8080 | Implemented | Routes REST/WS to services (`application.yml`) |
| **tenant-service** | 8082 | Implemented | Dev auth, JWT/JWKS, users, tenants, roles, API tokens |
| **pipeline-service** | 8083 | Implemented | Pipeline CRUD, YAML validation, connections, secrets, event sourcing + outbox |
| **execution-service** | 8084 | Implemented | Executions, jobs, Kafka consumers, outbox relay, embedded stage executors (echo, SQL, container), WebSocket realtime |
| **scheduler-service** | 8085 | Implemented | Cron schedules API, triggers execution-service |
| **graphql** | 8081 | Stub | Boot app only; UI uses REST |
| **runner-service** | 8086 | Stub | Boot app only |
| **metadata-service** | 8087 | Stub | Boot app only |
| **notification-service** | 8088 | Stub | Boot app only |
| **agent-service** | 8089 | Stub | Boot app only |
| **connect-service** | 8090 | Stub | Boot app only |

**Standalone `backend/runner/`:** Picocli entrypoint skeleton (not wired to production control plane).

**gRPC:** Proto definitions in `libs/proto` (`common`, `execution_service`, `runner_service`). No gRPC servers in services yet; alpha execution uses embedded executors inside `execution-service`.

---

## Shared libraries (`backend/libs`)

| Module | Status | Notes |
|--------|--------|-------|
| **common** | Implemented | Domain events, state machines, value resolution (`UnifiedValueResolver`, `StageOutputReferenceValidator`, `StageOutputSizeGuard`), validators |
| **spring-support** | Implemented | Multitenancy, security helpers |
| **proto** | Partial | Definitions only |
| **test-support** | Implemented | Testcontainers, JWT test issuer |

---

## Execution engine (EPIC-02) — implemented on `develop`

| Story | Status | Evidence |
|-------|--------|----------|
| US-02.01 Manual run | Implemented | `POST /api/v1/executions`, trigger from UI |
| US-02.02 Execution status | Implemented | REST + WebSocket `execution.updated` |
| US-02.03 Job logs | Implemented | Job log API + `RunLogPanel` |
| US-02.04 Cancel | Implemented | `POST /api/v1/executions/{id}/cancel` |
| US-02.06 Retry | Implemented | Retry policy on stages / execution |
| US-02.07 Timeout | Implemented | `JobTimeoutProcessor` |
| US-02.10 Stage data passing | Implemented | `${stages.*.output.*}`, publish validation, size limits, UI output panel |
| US-02.14 SQL stage | Implemented | `SqlEmbeddedStageExecutor` |
| US-02.17 Container stage | Implemented | `ContainerEmbeddedStageExecutor` (Docker) |

**Not yet:** US-02.05 retry from failed stage, US-02.12 checkpointing, US-02.11 artifacts, external runner dispatch, Spark/dbt stages.

---

## Web UI (`web/`)

| Area | Status |
|------|--------|
| Login, shell, dashboard | Implemented |
| Workflows list / detail | Implemented |
| Runs list / detail | Implemented |
| Cancel run | Implemented |
| Schedules on workflow | Implemented |
| Real-time run status | Implemented (WebSocket) |
| Stage output debug panel | Implemented (`RunJobOutputPanel`, US-02.10) |
| GraphQL read model | Planned (ADR-033) |

---

## Local development

| Component | Location |
|-----------|----------|
| Infrastructure (Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog) | `backend/docker-compose.yml` |
| Start Java services | `make local-services` (repo root → `backend/Makefile`) |
| Seed demo data | `make local-seed` |
| Web dev server | `make local-web` or `cd web && npm run dev` |
| Pre-commit (CI parity) | `./scripts/pre-commit.sh` |

See [backend/README.md](../backend/README.md) and [web/README.md](../web/README.md).

---

## Infrastructure not in repo

- Kubernetes / Helm application charts (CI may build container images; charts not maintained here)
- Elasticsearch / OpenLineage pipeline
- HashiCorp Vault deployment (secrets: tenant DB + `env:` refs)
- Production runner fleet management

---

## Documentation maintenance

When you change behavior, update in the **same PR**:

1. This file (if scope changes)
2. Relevant LLD (`docs/lld/`) or ADR
3. Epic acceptance criteria in `docs/product/epics/`
4. Service README (`backend/README.md`, `web/README.md`) if operators need new env vars
5. Root [README.md](../README.md) only if onboarding or status summary changes

See [LLD README — Document Maintenance](lld/README.md#document-maintenance).
