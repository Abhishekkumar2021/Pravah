# Pravah — Agent Context (living document)

> **Purpose:** Single source for AI agents to resume work without re-scanning the repo.
> **Update when:** A user story ships, release milestone shifts, or architecture changes.
> **Last updated:** 2026-05-16

---

## 1. What Pravah Is

Production-grade **unified data platform** (orchestration + lineage + quality). Built as a portfolio-grade system with full docs (ADRs, LLD, theory, product epics).

- **Stack:** Java 21, Spring Boot 3, PostgreSQL 16 (RLS), Kafka, Redis, React/Vite web UI
- **Patterns:** DDD, event sourcing (pipeline), outbox, saga choreography, state machines — see `docs/lld/`
- **Repo layout:** `backend/` (Gradle monorepo), `web/` (React), `docs/`, `playground/` (exercises)

---

## 2. Documentation Map (read before coding)

| Need | Path |
|------|------|
| Agent/engineering rules | `AGENTS.md`, `.cursor/rules/00–13-*.mdc` |
| Product vision & epics | `docs/product/PRODUCT-VISION.md`, `EPICS-OVERVIEW.md` |
| Release milestones | `docs/product/releases/RELEASE-PLAN.md` |
| User stories (180 total) | `docs/product/epics/EPIC-01..12-*.md` |
| ADRs (34) | `docs/adr/README.md` |
| LLD (implementation truth) | `docs/lld/01`–`06` |
| HLA | `docs/architecture/high-level-architecture.md` |
| Pre-commit (mandatory) | `cd backend && ./gradlew spotlessApply && compileJava compileTestJava && test` |
| PR titles | `type(scope): Subject` — subject **must start with uppercase** |

---

## 3. Target Journey (releases)

```
v0.1-alpha → v0.5-beta → v1.0-GA → v1.5 → v2.0
```

**Current focus:** **v0.1-alpha — "First Light"**  
Goal: workflow created → scheduled → executed → logs in UI → basic auth → local deploy.

Alpha exit criteria (`RELEASE-PLAN.md`):
- [ ] Create workflow via YAML
- [ ] Run workflow manually
- [ ] View logs in UI
- [ ] Schedule on cron
- [ ] Login and basic RBAC
- [ ] Deploy to local K8s

---

## 4. Backend Services

| Service | Status (2026-05-16) | Notes |
|---------|---------------------|-------|
| `gateway` | Active | Routes to pipeline/execution; WebSocket proxy for US-12.10 |
| `pipeline-service` | **Implemented slice** | CRUD, validate, publish/archive, RLS, event-sourced aggregate |
| `execution-service` | **Implemented slice** | POST/GET/list/cancel executions; outbox; job.created worker; **US-12.10 WIP** |
| `tenant-service` | Partial | Users/tenants/JWKS REST; not fully wired to UI auth |
| `scheduler-service` | Stub | Alpha needs US-03.01 cron |
| `runner-service` | Stub | Real K8s/Docker runners later |
| `metadata-service` | Stub | Lineage post-alpha |
| `notification-service` | Stub | |
| `agent-service` | Stub | |
| `connect-service` | Stub | |
| `graphql` | Stub | |

**Local infra:** `backend/docker-compose.yml` — Postgres, Kafka (KRaft), Redis, Kafka UI.

---

## 5. Implementation Progress (by epic)

### EPIC-01 Workflow Definition (alpha subset)
| Story | Status | Evidence |
|-------|--------|----------|
| Pipeline CRUD / versioning slice | Done | PR #38, `PipelineController` |
| Validate definition API | Done | PR #39, `POST /validate` |
| Full YAML upload / stage types | Not done | Alpha US-01.01, 01.03–05, 01.12 |

### EPIC-02 Execution Engine (alpha subset)
| Story | Status | Evidence |
|-------|--------|----------|
| US-02.01 Manual run | Done | PR #40, `POST /api/v1/executions` |
| US-02.04 Cancel | Done | PR #46 |
| Outbox `execution.created` | Done | PR #43 |
| Embedded worker `job.created` | Done | PR #45 |
| US-02.02 Real-time status | In progress | US-12.10 WebSocket |
| US-02.03 Logs | Not done | |
| US-02.06–07, 14, 17 (retry, etc.) | Not done | |

### EPIC-12 UI & UX (alpha subset)
| Story | Status | Evidence |
|-------|--------|----------|
| US-12.01–05 Shell pages | Done (layout; auth placeholder) | PRs #47–48 |
| US-12.07 REST alpha / lists / trigger run | Done | PR #49 |
| US-12.08 Run detail polish | Partial | `RunDetailPage.tsx` |
| US-12.09 Log viewer | Not done | |
| US-12.10 Real-time WebSocket | **Done (branch)** | WS + Redis fan-out; UI hook on run detail, list, dashboard |

### EPIC-03, 09, 10 (alpha blockers — mostly not started)
- Scheduling (cron), full platform deploy, UI login/RBAC — required for alpha exit.

---

## 6. Active Work (as of 2026-05-16)

- **Branch:** `feat/us-12-10-execution-websocket` — US-12.10 implementation ready for review/merge
- **Refs:** US-12.10 (also advances US-02.02)

**Suggested alpha order after merge:**
1. Finish US-12.08 run detail (timeline, job states) + wire WebSocket
2. US-12.09 log viewer (or minimal US-02.03 backend)
3. EPIC-10: wire tenant-service JWT login to UI (US-10.01)
4. EPIC-03: scheduler-service cron (US-03.01)
5. EPIC-01: YAML create/upload path (US-01.01)

---

## 7. Key APIs (implemented)

**Pipeline** (`/api/v1/pipelines`): POST, GET, PUT, validate, publish, archive, restore, versions  
**Execution** (`/api/v1/executions`): POST create, GET list/detail, POST `/{id}/cancel`  
**Tenant** (`/api/v1/...`): users, tenants, JWKS — dev JWT issuance  

**Web:** Vite app in `web/`; dev bearer token for alpha; proxies via gateway.

---

## 8. Engineering Constraints (non-negotiable)

- Check `docs/lld/` before implementing; update docs if design changes intentionally
- Kafka: **outbox only** (no dual writes); idempotent consumers
- Multi-tenant: RLS + tenant context; never trust client tenant id
- State machines: match `docs/lld/03-state-machines.md`; test all transitions
- Tests: unit `*Test.java`, integration `*IT.java`, Testcontainers for Postgres
- No commit without full pre-commit workflow (user must ask to commit)
- Topic naming: `pravah.{service}.{entity}.events`

---

## 9. Git / PR Conventions

- Work on topic branches → PR to `develop` or `main`
- Commit: `type(scope): Description` + `Refs: US-XX.YY`
- Example PR title: `feat(execution-service): Cancel execution API (US-02.04)`

Recent merged work (mainline): EPIC-01 pipeline slice → execution manual run → outbox → job worker → cancel → web shell → executions list → REST alpha path.

---

## 10. Changelog (agent maintenance)

| Date | Change |
|------|--------|
| 2026-05-16 | Initial context file; journey at v0.1-alpha; US-12.10 in progress |
| 2026-05-16 | US-12.10: `ExecutionWebSocketIT`, realtime unit tests, Playwright E2E in `web/e2e/` |
