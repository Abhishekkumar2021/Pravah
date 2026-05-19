# Pravah Implementation Status

> **Source of truth** for what is built in this repository vs what is documented as the long-term target architecture.  
> Update this file whenever you ship or stub a user-facing capability.  
> **Last updated:** 2026-05-18

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
| Web alpha (EPIC-12) | **Partial** | REST + WebSocket, visual DAG editor (US-12.06); no GraphQL read model |
| Remaining microservices | **Stub** | agent, connect, metadata, runner-service, graphql |
| Monitoring & alerts (EPIC-04) | **Partial** | notification-service: alert rules, email/Slack/webhook, audit log, in-app bell |
| Runner fleet + gRPC | **Planned** | `runner/` CLI skeleton; no live runner dispatch |
| Lineage, catalog, AI agent | **Planned** | No Elasticsearch / OpenLineage stack in repo |

**Rough progress vs full product vision (~180 user stories): ~35–40%.**  
**Alpha MVP path (create pipeline → run → logs → cancel → schedule): ~70–75%.**

---

## Backend services

| Service | Port (local) | Status | Capabilities |
|---------|--------------|--------|--------------|
| **gateway** | 8080 | Implemented | Routes REST/WS to services (`application.yml`) |
| **tenant-service** | 8082 | Implemented | Email/password login, JWT/JWKS, password reset email, users, tenants, roles, API tokens |
| **pipeline-service** | 8083 | Implemented | Pipeline CRUD, YAML validation, connections, secrets, event sourcing + outbox |
| **execution-service** | 8084 | Implemented | Executions, jobs, Kafka consumers, outbox relay, embedded stage executors (echo, SQL, container), WebSocket realtime |
| **scheduler-service** | 8085 | Implemented | Cron schedules API, event triggers (webhook + Kafka US-03.06/US-03.07), rate limiting, idempotent Kafka consumers |
| **graphql** | 8081 | Stub | Boot app only; UI uses REST |
| **runner-service** | 8086 | Stub | Boot app only |
| **metadata-service** | 8087 | Stub | Boot app only |
| **notification-service** | 8088 | Partial | Alert rules CRUD, Kafka consumer (`pravah.execution.execution.events` incl. `execution.failed`/`execution.completed`), email (SMTP/Thymeleaf), Slack/webhook channels, dedup, audit log API, in-app notifications + preferences API |
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
| US-02.09 Parallel execution | Implemented | `ExecutionParallelismPolicy`, Kafka worker concurrency, `maxParallelStages` per workflow, timing-based Gantt chart |
| US-02.15 Python stage | Partial | `PythonEmbeddedStageExecutor`, per-job venv, `requirements`, structured stdout JSON; no remote script artifacts |

**Not yet:** US-02.11 artifacts, external runner dispatch, Spark/dbt stages, Python `requirements_file` from pipeline repo.

**Done (retry / checkpoint):** US-02.05 retry from failed stage (`POST /api/v1/executions/{id}/retry`), US-02.12 checkpoints table + auto-save on stage success + restore on retry + clear on success; run detail UI retry actions and `retryOf` lineage.

**Done (resource requests):** US-02.08 resource profiles (`config.resources.profile: small|medium|large|xlarge`) + explicit `memory`/`cpus` with profile override; container run UI shows image and duration.

---

## Scheduling & triggers (EPIC-03) — partial

| Story | Status | Evidence |
|-------|--------|----------|
| US-03.01 Cron schedules | Implemented | Cron parser, schedule evaluation job, leader election |
| US-03.06 Kafka triggers | Implemented | Dynamic Kafka listeners, filter matching, idempotent consumer with dedup table |
| US-03.07 Webhook triggers | Implemented | Public hook endpoint, BCrypt secret validation, per-trigger rate limiting |

**APIs (via gateway → scheduler-service):**

- Schedules: `GET/POST/PUT/DELETE /api/v1/schedules`
- Triggers: `GET/POST/PUT/DELETE /api/v1/triggers`, `POST /api/v1/triggers/{id}/enable`, `POST /api/v1/triggers/{id}/disable`
- Webhooks: `POST /api/v1/hooks/{triggerId}` (public, no auth required)

**Not yet:** event trigger UI, trigger history/logs, manual test trigger, webhook retry on failure.

---

## Monitoring & alerting (EPIC-04) — Sprint 1 (partial)

| Story | Status | Evidence |
|-------|--------|----------|
| US-04.04 Alert on failure | Partial | Kafka listener → `AlertDispatchService`; per-workflow/tenant rules; email/Slack/webhook; environment filter in rule conditions (requires event payload `environment`) |
| US-04.07 Email channel | Partial | `EmailChannel` + `alert-email.html` template; links to run/workflow in UI |
| US-04.08 Slack channel | Partial | Incoming webhook + rich blocks/buttons; no thread/ack |
| US-04.10 Webhook channel | Partial | JSON POST, custom headers, retry on 5xx |
| US-04.12 Alert deduplication | Partial | Per-rule `dedup_window_seconds` + `alert_history` dedup key |
| US-04.15 In-app notification center | Partial | Bell dropdown (`NotificationBell`), `/app/notifications`, mark read; `/app/notification-preferences` for email/in-app toggles and quiet hours |
| US-04.19 Audit log view | Partial | Append-only `audit_log` table + `GET /api/v1/audit-logs` + `/app/audit-log` UI; no CSV export |

**APIs (via gateway → notification-service):** `GET/POST/PUT/DELETE /api/v1/alert-rules`, `GET /api/v1/audit-logs`, `GET/POST /api/v1/notifications`, `GET/PUT /api/v1/notification-preferences`.

**Not yet:** SLA/anomaly alerts, PagerDuty, routing rules, snooze, maintenance windows, custom dashboards, metrics pipeline.

---

## Security (EPIC-10)

| Story | Status | Evidence |
|-------|--------|----------|
| US-10.01 Local auth | Implemented | Login, signup UI, email verification (pending → verify → active), bcrypt, lockout; password reset via SMTP (Mailhog `1025`) |
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
| US-12.05 Workflow detail | Implemented | Header, visual DAG, recent runs, schedules, Alerts tab (per-workflow rules) |
| US-12.07 Run list | Implemented | Filter, status badges, duration |
| US-12.08 Run detail | Implemented | Per-stage status, expandable logs, retry/cancel |
| US-12.09 Log viewer | Implemented | Syntax highlighting, level filter, search, download, jump to error |
| US-12.06 Visual DAG editor | Implemented (alpha) | `WorkflowDAGEditor` — React Flow, palette add, connect handles, config panel, undo/redo, YAML export, minimap |
| US-12.16 Connection management | Implemented | `/app/connections` — list, create/edit, test (`POST /api/v1/connections/{id}/test`), masked credential refs |
| US-04.04 / US-04.15 Alerts UI | Partial | `/app/alert-rules`, workflow **Alerts** tab, bell + `/app/notifications`, `/app/notification-preferences` |
| US-04.19 Audit log UI | Partial | `/app/audit-log` — filter by action/resource, pagination |

---

## Web UI (`web/`)

| Area | Status | Notes |
|------|--------|-------|
| Login, shell, dashboard | Implemented | Forgot password link (US-10.01), recent failures widget (US-12.03) |
| Workflows list / detail | Implemented | Search by name (US-12.04), read-only DAG (US-12.05), edit via `WorkflowDAGEditor` (US-12.06) |
| Signup / verify email | Implemented | `/signup`, `/verify-email?token=…`, resend verification API (US-10.01) |
| Forgot / reset password | Implemented | Forgot-password + reset-token pages; Mailhog UI `http://localhost:8025` (US-10.01) |
| Runs list / detail | Implemented | Retry from failed stage (US-02.05), retryOf lineage |
| Cancel run | Implemented | |
| Schedules on workflow | Implemented | |
| Connections | Implemented | `/app/connections` — postgres CRUD + test; passwords as `env:` / vault / `${secret.*}` refs only (US-12.16) |
| Alert rules | Partial | `/app/alert-rules` and workflow **Alerts** tab — CRUD, email/Slack/webhook, event filters (US-04.04, US-04.07, US-04.08) |
| Audit log | Partial | `/app/audit-log` — paginated list, action/resource filters (US-04.19) |
| Notification center | Partial | Bell + unread count + `/app/notifications` + `/app/notification-preferences` (US-04.15) |
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
