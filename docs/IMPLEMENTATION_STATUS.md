# Pravah Implementation Status

> **Source of truth** for what is built in this repository vs what is documented as the long-term target architecture.  
> Update this file whenever you ship or stub a user-facing capability.  
> **Last updated:** 2026-05-23

---

## What Kind of Platform Is Pravah?

**Pravah is a workflow orchestrator** (like Airflow, Dagster, Prefect) — **not** an ETL tool with pre-built connectors (like Fivetran, Airbyte).

| You Want To... | How Pravah Handles It |
|----------------|----------------------|
| **Extract** from databases | SQL stage with configured connection |
| **Extract** from APIs | Python or Container stage |
| **Transform** data | Python stage, SQL stage, or Container |
| **Load** to destinations | SQL stage (INSERT), Container stage |
| **Pass data between stages** | `${stages.<id>.output.<path>}` for JSON; `${stages.<id>.artifact.<file>}` for files |
| **CDC / streaming ingestion** | Kafka trigger + Container stage (Connect Service planned) |

### ETL Capabilities Matrix

| Capability | Status | How It Works |
|------------|--------|--------------|
| Query databases (Postgres, MySQL, etc.) | ✅ Implemented | SQL stage + Connections (`pipeline-service`) |
| Run Python scripts | ✅ Implemented | Python stage (auto venv, pip install) |
| Run containers | ✅ Implemented | Container stage (Docker) |
| Pass small data between stages | ✅ Implemented | JSON via `${stages.*.output.*}` with size limits |
| Pass large files between stages | ✅ Implemented | Artifact storage in MinIO via `${stages.*.artifact.*}` |
| Pre-built source connectors | **Partial** | Connect Service: 19+ connector plugins (DB, file, protocol, streaming, SaaS). Google Sheets uses placeholder OAuth; PostgreSQL CDC is config/discovery only (no logical decoding stream yet) |
| dbt transformations | ✅ Implemented | `DbtEmbeddedStageExecutor` runs `dbt run` in project directory |
| Spark jobs | ✅ Implemented | `SparkEmbeddedStageExecutor` runs `spark-submit` for JAR apps |
| Data lineage tracking | 🔮 Planned | Metadata Service with OpenLineage (ADR-019) |

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
| CLI (EPIC-11) | **Implemented** | Go CLI with Cobra; auth, workflow, run, deploy commands; GitHub Action |
| API Documentation | **Implemented** | SpringDoc OpenAPI per service; Swagger UI at `/swagger-ui.html` |
| Remaining microservices | **Partial** | agent (stub), metadata (stub), graphql (stub), connect + runner-service (implemented) |
| Monitoring & alerts (EPIC-04) | **Partial** | notification-service: alert rules, email/Slack/webhook, audit log, in-app bell |
| Runner fleet + gRPC | **Partial** | gRPC server on 9091 with optional TLS/mTLS (`pravah.runner.grpc.tls.*`); runner agent `--tls-*` flags; internal assignment API, `runOn: runner` dispatches resolved `JobSpec` + stage output callback; runner stream auth via registration token on first heartbeat; assignment-bound job status |
| Lineage, catalog, AI agent | **Planned** | No Elasticsearch / OpenLineage stack in repo |

**Rough progress vs full product vision (~180 user stories): ~50–55%.**  
**Alpha MVP path (create pipeline → run → logs → cancel → schedule): ~85–90%.**

---

## Backend services

| Service | Port (local) | Status | Capabilities |
|---------|--------------|--------|--------------|
| **gateway** | 8080 | Implemented | Routes REST/WS, Redis rate limiting (token bucket per-tenant), JWT blocklist, circuit breakers, request logging (ADR-012) |
| **tenant-service** | 8082 | Implemented | Email/password login, JWT/JWKS, password reset email, users, tenants, roles, API tokens, Redis tenant config cache (ADR-012) |
| **pipeline-service** | 8083 | Implemented | Pipeline CRUD, YAML validation, connections, secrets, event sourcing + outbox |
| **execution-service** | 8084 | Implemented | Executions, jobs, Kafka consumers + DLT (`pravah.kafka.execution-events.dlt-topic`), outbox relay, embedded stage executors (echo, SQL, container), WebSocket realtime, `@PreAuthorize` on REST APIs, circuit breaker + retry for inter-service calls (Resilience4j) |
| **scheduler-service** | 8085 | Implemented | Cron schedules API with `catchupPolicy` (`skip`/`run_all`/`coalesce`), multi-fire catchup (`pravah.scheduler.max-catchup-fires`), coalesce combined interval in execution parameters (`_trigger.coalesce`, `pravah.scheduler.coalesce-max-interval`), event triggers (webhook + Kafka US-03.06/US-03.07), Redis webhook rate limiting, claim-first Kafka idempotency + DLT, trigger dispatch outbox retry, circuit breaker + retry (Resilience4j) |
| **graphql** | 8081 | Partial | OAuth2 resource server (JWT via JWKS); no GraphQL schema/resolvers yet — UI uses REST |
| **runner-service** | 8086 | Partial | gRPC bidirectional streaming, REST fleet API with `@PreAuthorize`, gateway route `/api/v1/runners/**`, job assignment with label matching, stale runner detection, runner-agent secret resolution (`POST /api/v1/runners/{runnerId}/jobs/{jobId}/environment-secrets` authenticated with stream token + job assignment check) |
| **metadata-service** | 8087 | Stub | Boot app only |
| **notification-service** | 8088 | Partial | Alert rules CRUD, Kafka consumer + DLT (`pravah.kafka.notification.dlt-topic`), email (SMTP/Thymeleaf), Slack/webhook channels, dedup, audit log API, in-app notifications + preferences API, `@PreAuthorize` on REST APIs |
| **agent-service** | 8089 | Stub | Boot app only |
| **connect-service** | 8091 | Implemented | Connector framework (17+ connectors), connection CRUD, test/discover streams, SaaS (Sheets, Stripe, Airtable, HubSpot), streaming (Kafka, RabbitMQ), CDC (PostgreSQL), Snowflake warehouse |

**Standalone `backend/runner/`:** Picocli agent registers via gRPC (bootstrap secret + tenant metadata), heartbeats with stream token, resolves `secret_environment` via runner-service HTTP using the stream token, executes container/shell/python/sql jobs from resolved pipeline spec.

**gRPC:** Proto definitions in `libs/proto`; `runner-service` serves gRPC on port 9091 (`RunnerGrpcServerLifecycle`). Stages with `runOn: runner` dispatch via `POST /api/v1/internal/runners/assignments` (S2S secret). Requires `RUNNER_SERVICE_BASE_URL`, `PRAVAH_INTERNAL_SERVICE_SECRET`.

---

## Shared libraries (`backend/libs`)

| Module | Status | Notes |
|--------|--------|-------|
| **common** | Implemented | Domain events, state machines, value resolution (`UnifiedValueResolver`, `StageOutputReferenceValidator`, `StageOutputSizeGuard`), validators, `IpCidrMatcher`, `SecretNameValidator` |
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

**Done:** US-02.11 artifacts (MinIO storage, presigned URLs, artifact browser UI, `${stages.*.artifact.*}` resolution).

**Not yet:** Python `requirements_file` from pipeline repo (remote runner uses inline `requirements` list only), connect-service integration tests with Testcontainers.

**Done (retry / checkpoint):** US-02.05 retry from failed stage (`POST /api/v1/executions/{id}/retry`), US-02.12 checkpoints table + auto-save on stage success + restore on retry + clear on success; run detail UI retry actions and `retryOf` lineage.

**Done (resource requests):** US-02.08 resource profiles (`config.resources.profile: small|medium|large|xlarge`) + explicit `memory`/`cpus` with profile override; container run UI shows image and duration.

---

## Scheduling & triggers (EPIC-03) — partial

| Story | Status | Evidence |
|-------|--------|----------|
| US-03.01 Cron schedules | Implemented | Cron parser, schedule evaluation job, leader election |
| US-03.06 Kafka triggers | Implemented | Dynamic Kafka listeners, filter matching, idempotent consumer with dedup table |
| US-03.07 Webhook triggers | Implemented | Public hook endpoint, BCrypt secret validation, Redis per-trigger rate limiting (429 + Retry-After) |

**APIs (via gateway → scheduler-service):**

- Schedules: `GET/POST/PUT/DELETE /api/v1/schedules`
- Triggers: `GET/POST/PUT/DELETE /api/v1/triggers`, `POST /api/v1/triggers/{id}/enable`, `POST /api/v1/triggers/{id}/disable`
- Webhooks: `POST /api/v1/hooks/{triggerId}` (public, no auth required)

**Implemented:** Event trigger UI on workflow **Triggers** tab (`WorkflowTriggersPanel`); `GET /api/v1/triggers/{id}/history`; `POST /api/v1/triggers/{id}/test`; webhook/Kafka dispatch history + `trigger_dispatch_pending` outbox retry; `coalesce` catchup (US-03.15) with combined interval in execution parameters and `pravah.scheduler.coalesce-max-interval` threshold fallback to `run_all`.

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

**Not yet:** SLA/anomaly alerts, PagerDuty, routing rules, snooze, maintenance windows, custom dashboards, metrics pipeline. Example Grafana alert rules for rate-limit fail-open and outbox dead-letter: `deploy/observability/grafana/provisioning/alerting/pravah-platform.yml`.

---

## Resilience & Rate Limiting (Production-Grade)

| Feature | Status | Evidence |
|---------|--------|----------|
| Gateway rate limiting | Implemented | Redis token bucket per-tenant/API-token/IP (ADR-012), HTTP 429 + Retry-After + `X-RateLimit-*` headers; `pravah.ratelimit.fail-open` (default true) with `fail_open` Prometheus outcome |
| Webhook rate limiting | Implemented | Redis token bucket per trigger (`ratelimit:webhook:{id}`), shared Lua script in `libs/common` |
| Rate limit metrics | Implemented | `pravah_ratelimit_requests_total{layer,gateway\|webhook,key_type,outcome}` on `/actuator/prometheus` |
| JWT token revocation | Implemented | Redis blocklist with TTL matching token expiry (ADR-012); optional `JwtBlocklistChecker` in servlet services when Redis is configured |
| Service JWT + internal S2S | Partial | `ApiTenantJwtFilter` skips `/api/v1/internal/**`; `InternalServiceAuthFilter` on internal routes; `@PreAuthorize` on pipeline, execution, scheduler, connect, notification, tenant, runner public APIs; tenant-service refresh JWT in HttpOnly cookie + in-memory access token in web |
| Gateway circuit breaker | Implemented | Resilience4j reactive circuit breaker for all backend routes (LLD-01) |
| Request logging | Implemented | Structured JSON logs with tenant_id, user_id, request_id, duration, route |
| Service circuit breakers | Implemented | Resilience4j on `execution-service` → `pipeline-service` and `scheduler-service` → `execution-service` |
| Retry with backoff | Implemented | Exponential backoff (1s → 2s → 4s) with max 3 attempts for transient failures |
| Tenant config caching | Implemented | Redis cache-aside pattern with 10-minute TTL, invalidation on update (ADR-012) |

**Configuration (environment variables):**

| Variable | Default | Description |
|----------|---------|-------------|
| `PRAVAH_RATE_LIMIT_ENABLED` | `true` | Enable/disable rate limiting |
| `PRAVAH_RATE_LIMIT_RPS` | `100` | Default requests per second per tenant |
| `PRAVAH_RATE_LIMIT_BURST` | `200` | Burst capacity for token bucket |
| `PRAVAH_RATE_LIMIT_API_TOKEN_RPS` | `50` | RPS for API token access (lower) |
| `PRAVAH_CACHE_ENABLED` | `true` | Enable/disable tenant config caching |

**Tests:** `RateLimitGatewayFilterTest`, `RedisRateLimiterIT` (gateway); `WebhookRateLimiterTest`, `RedisTokenBucketRateLimiterIT` (spring-support).

**Requires Redis:** Gateway, scheduler webhooks, and tenant config cache need Redis (`backend/docker-compose.yml` service `redis`, ports `6379`).

**Not yet:** Redis Sentinel HA, per-endpoint rate limits. **Partial:** Gateway IP allowlist filter implemented (`pravah.gateway.ip-allowlist.*`, US-10.16); Helm prod sets `rateLimitFailOpen=false`; Grafana example rules in `deploy/observability/grafana/provisioning/alerting/pravah-platform.yml`.

---

## Security (EPIC-10)

| Story | Status | Evidence |
|-------|--------|----------|
| US-10.01 Local auth | Implemented | Login, signup UI, email verification (pending → verify → active), bcrypt, lockout; password reset via SMTP (Mailhog `1025`) |
| US-10.05 Built-in roles | Implemented | Viewer/Editor/Admin/Owner seeded with permissions |
| US-10.08 API tokens | Implemented | Expiration, scopes, hash-at-rest, revoke, `last_used_at` |
| US-10.14 API rate limiting | Partial | Gateway: per-tenant/token/IP limits, 429, Retry-After, Prometheus metrics; tier limits via tenant cache; no dedicated alert rules yet |
| Tenant bootstrap hardening | Partial | `POST /api/v1/tenants` requires authentication (no public tenant creation); self-service signup uses configured `registrationTenantId` |
| RLS maintenance jobs | Partial | `SystemMaintenanceRlsHelper` + Flyway policies for cross-tenant scheduled work (timeouts, stale runners, artifact cleanup) |
| Scheduler catch-up | Partial | `skip`, `run_all`, and `coalesce` (combined interval in execution params); failed triggers retain `next_run_at` for retry |
| Execution upstream failures | Partial | PENDING jobs blocked by failed upstream are failed so executions do not stay RUNNING indefinitely |
| Remote job completion | Partial | Rejects completion callbacks from a runner other than the assignee |
| SSRF hardening | Partial | `UrlSafetyValidator` on JDBC connection tests and webhook/Slack URLs |
| Pipeline draft YAML | Partial | Draft definition stored as `pipeline_versions` v0; returned on pipeline detail |
| Auth `/me` + `/logout` | Partial | Tenant-service endpoints; logout uses Redis blocklist when configured |
| Metadata/agent JWT | Partial | `SecurityConfig` requires JWT on all routes except actuator health |
| Web session storage | Partial | Access token in `sessionStorage`; profile save calls user API |

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
| Workflows list / detail | Implemented | Search by name (US-12.04), read-only DAG (US-12.05), edit via `WorkflowDAGEditor` (US-12.06), settings tab saves name/description + pipeline draft retry (`retry.max_attempts`) |
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

## CLI (EPIC-11 — Developer Experience)

| Story | Status | Evidence |
|-------|--------|----------|
| US-11.01 Installation | Implemented | GoReleaser config, Homebrew tap, install script |
| US-11.02 Authentication | Implemented | `pravah login`, API token, keyring storage, profiles |
| US-11.03 Workflow Commands | Implemented | list, create, get, run, logs, validate |
| US-11.04 Run Commands | Implemented | list, status, logs, cancel, retry |
| US-11.05 Deploy Commands | Implemented | deploy, diff, dry-run, CI exit codes |
| US-11.10 GitHub Action | Implemented | `.github/actions/deploy/` |
| US-11.12 REST API Docs | Implemented | SpringDoc OpenAPI, Swagger UI per service |

**CLI location:** `cli/` — Go module with Cobra framework.

**Installation:**
```bash
# From source
cd cli && make build && ./bin/pravah --help

# Or install globally
cd cli && make install
```

**Quick start:**
```bash
pravah login
pravah workflow list
pravah workflow run <id> --wait
pravah deploy -f workflows/
```

**Not yet:** Python SDK (US-11.06–11.08), VS Code extension (US-11.09), GitLab CI template (US-11.11), GraphQL API (US-11.13), Webhooks API (US-11.14).

---

## API Documentation (US-11.12)

All implemented services expose OpenAPI 3.0 specifications via SpringDoc:

| Service | Swagger UI | OpenAPI JSON |
|---------|------------|--------------|
| tenant-service | `http://localhost:8082/swagger-ui.html` | `/v3/api-docs` |
| pipeline-service | `http://localhost:8083/swagger-ui.html` | `/v3/api-docs` |
| execution-service | `http://localhost:8084/swagger-ui.html` | `/v3/api-docs` |
| scheduler-service | `http://localhost:8085/swagger-ui.html` | `/v3/api-docs` |
| notification-service | `http://localhost:8088/swagger-ui.html` | `/v3/api-docs` |

**Via gateway:** Access individual service docs directly; gateway does not aggregate specs.

---

## Local development

| Component | Location |
|-----------|----------|
| Infrastructure (Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog, Vault dev) | `backend/docker-compose.yml` |
| Start Java services | `make local-services` (repo root → `backend/Makefile`) |
| Seed demo data | `make local-seed` |
| Web dev server | `make local-web` or `cd web && npm run dev` |
| CLI | `cd cli && make build` or `make install` |
| Kubernetes (Helm, local) | [deploy/README.md](../deploy/README.md) — `k8s-local-build.sh`, `k8s-local-install.sh` |
| Pre-commit (CI parity) | `./scripts/pre-commit.sh` |

See [backend/README.md](../backend/README.md), [web/README.md](../web/README.md), and [cli/README.md](../cli/README.md).

---

## Infrastructure

| Component | Status | Notes |
|-----------|--------|-------|
| Docker Compose (local deps) | Implemented | `backend/docker-compose.yml` |
| Helm umbrella chart (US-09.04) | Implemented (beta) | `deploy/helm/pravah-platform` — 8 services + MinIO/Mailhog/Vault dev (local); prod validation gates (pinned image tag, external deps, fail-closed rate limits, secure cookies, runner bootstrap secret, external egress NP) |
| HashiCorp Vault KV resolver (ADR-007) | Partial | `HttpVaultKvClient` + Spring wiring; pipeline-service `vault:path#key`; token auth (local) + Kubernetes auth (K8s prod); tenant secrets still in Postgres |
| GHCR service images | Implemented | `.github/workflows/deploy.yml` on `main` |
| Argo CD GitOps | Planned | ADR-010; beta |
| Terraform (cloud) | Planned | US-09.06+ |

---

## Infrastructure not in repo
- Elasticsearch / OpenLineage pipeline
- Managed Vault cluster (chart supports `externalVault.address`; operators provision Vault separately)
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
