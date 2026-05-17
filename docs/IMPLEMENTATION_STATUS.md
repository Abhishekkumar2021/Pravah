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

**Rough progress vs full product vision (~180 user stories): ~35–40%.**  
**Alpha MVP path (create pipeline → run → logs → cancel → schedule): ~70–75%.**

---

## Backend services

| Service | Port (local) | Status | Capabilities |
|---------|--------------|--------|--------------|
| **gateway** | 8080 | Implemented | Routes REST/WS to services (`application.yml`) |
| **tenant-service** | 8082 | Implemented | Email/password login, JWT/JWKS, users, tenants, roles, API tokens |
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
| US-02.08 Resource requests | Implemented | `config.resources.profile` presets + explicit `memory`/`cpus` |
| US-02.17 Container stage | Implemented | `ContainerEmbeddedStageExecutor` (Docker) |

**Not yet:** US-02.11 artifacts, external runner dispatch, Spark/dbt stages.

**Done (retry / checkpoint):** US-02.05 retry from failed stage (`POST /api/v1/executions/{id}/retry`), US-02.12 checkpoints table + auto-save on stage success + restore on retry + clear on success; run detail UI retry actions and `retryOf` lineage.

**Done (resource requests):** US-02.08 resource profiles (`config.resources.profile: small|medium|large|xlarge`) + explicit `memory`/`cpus` with profile override; container run UI shows image and duration.

---

## Security (EPIC-10)

| Story | Status | Evidence |
|-------|--------|----------|
| US-10.01 Local auth | Partial | Login, register, bcrypt, lockout; password reset stub (no email delivery) |
| US-10.05 Built-in roles | Implemented | Viewer/Editor/Admin/Owner seeded with permissions |
| US-10.08 API tokens | Implemented | Expiration, scopes, hash-at-rest, revoke, `last_used_at` |

---

## UI/UX (EPIC-12 alpha stories)

| Story | Status | Evidence |
|-------|--------|----------|
| US-12.01 Login page | Implemented | Email/password, forgot password link, remember me |
| US-12.02 Global navigation | Implemented | Sidebar, active state, breadcrumbs |
| US-12.03 Dashboard home | Implemented | Recent workflows, active runs, failures, quick actions |
| US-12.04 Workflow list | Implemented | Sortable table, status filter, search, pagination |
| US-12.05 Workflow detail | Implemented | Header, visual DAG, recent runs, schedules |
| US-12.07 Run list | Implemented | Filter, status badges, duration |
| US-12.08 Run detail | Implemented | Per-stage status, expandable logs, retry/cancel |
| US-12.09 Log viewer | Implemented | Syntax highlighting, level filter, search, download, jump to error |

**Not yet:** US-12.06 Visual DAG editor (drag-drop stage creation).

---

## Web UI (`web/`)

| Area | Status | Notes |
|------|--------|-------|
| Login, shell, dashboard | Implemented | Forgot password link (US-10.01), recent failures widget (US-12.03) |
| Workflows list / detail | Implemented | Search by name (US-12.04), visual DAG (US-12.05) |
| Runs list / detail | Implemented | Retry from failed stage (US-02.05), retryOf lineage |
| Cancel run | Implemented | |
| Schedules on workflow | Implemented | |
| Real-time run status | Implemented (WebSocket) | |
| Log viewer | Implemented | Level filter, search, download, jump to error (US-12.09) |
| Stage output debug panel | Implemented | `RunJobOutputPanel` (US-02.10), resource panel |
| GraphQL read model | Planned | ADR-033 |

---

## Local development

| Component | Location |
|-----------|----------|
| Infrastructure (Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog) | `backend/docker-compose.yml` |
| Start Java services | `make local-services` (repo root → `backend/Makefile`) |
| Seed demo data | `make local-seed` |
| Web dev server | `make local-web` or `cd web && npm run dev` |
| Kubernetes (Helm, local) | [deploy/README.md](../deploy/README.md) — `k8s-local-build.sh`, `k8s-local-install.sh` |
| Pre-commit (CI parity) | `./scripts/pre-commit.sh` |

See [backend/README.md](../backend/README.md) and [web/README.md](../web/README.md).

---

## Infrastructure

| Component | Status | Notes |
|-----------|--------|-------|
| Docker Compose (local deps) | Implemented | `backend/docker-compose.yml` |
| Helm umbrella chart (US-09.04) | Implemented (alpha) | `deploy/helm/pravah-platform` — gateway + 4 services; bundled Postgres/Kafka/Redis for local K8s |
| GHCR service images | Implemented | `.github/workflows/deploy.yml` on `main` |
| Argo CD GitOps | Planned | ADR-010; beta |
| Terraform (cloud) | Planned | US-09.06+ |

---

## Infrastructure not in repo
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
