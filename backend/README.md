# Pravah Backend

This directory contains the backend services of the Pravah data orchestration platform.

## Project Structure

```
backend/
├── buildSrc/                    # Gradle convention plugins
│   └── src/main/kotlin/
│       ├── pravah.java-conventions.gradle.kts
│       ├── pravah.spring-boot-conventions.gradle.kts
│       ├── pravah.grpc-conventions.gradle.kts
│       ├── pravah.jpa-conventions.gradle.kts
│       └── pravah.kafka-conventions.gradle.kts
├── libs/                        # Shared libraries
│   ├── common/                  # Domain primitives, events, value resolution, validators
│   ├── proto/                   # gRPC/Protobuf definitions (partial)
│   ├── spring-support/          # Multitenancy, security
│   └── test-support/            # Test utilities and Testcontainers
├── services/                    # Microservices
│   ├── gateway/                 # API Gateway (Spring Cloud Gateway)
│   ├── graphql/                 # GraphQL API (Spring for GraphQL)
│   ├── tenant-service/          # Multi-tenancy and user management
│   ├── pipeline-service/        # Pipeline CRUD and versioning
│   ├── execution-service/       # Run orchestration and job management
│   ├── scheduler-service/       # Cron-based scheduling
│   ├── runner-service/          # Runner management and job dispatch
│   ├── metadata-service/        # Artifacts and analytics (DuckDB)
│   ├── notification-service/    # Multi-channel notifications
│   ├── agent-service/           # AI-powered assistance
│   └── connect-service/         # External integrations
├── runner/                      # Standalone runner binary (CLI skeleton)
├── docker-compose.yml           # Local Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog, Vault (dev)
├── scripts/local/               # local-up, local-services, seed
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── settings.gradle.kts          # Module includes
└── build.gradle.kts             # Root build configuration
```

**Service implementation status:** see [Implementation Status](../docs/IMPLEMENTATION_STATUS.md). Implemented: gateway, tenant, pipeline, execution, scheduler, notification, connect, runner-service. Partial/stub: graphql, metadata, agent (JWT-secured HTTP stubs).

**Runner dispatch (execution-service):** `PRAVAH_RUNNER_SERVICE_ENABLED` (default `true`), `RUNNER_SERVICE_BASE_URL` (default `http://localhost:8086`), `PRAVAH_INTERNAL_SERVICE_SECRET` for S2S calls to `/api/v1/internal/runners/assignments`. Stages with `runOn: runner` build a resolved `RemoteJobSpecPayload` and assign via runner-service gRPC.

**Runner agent (gRPC 9091):** Register with tenant metadata + bootstrap secret, then authenticate the bidirectional stream with the issued token on the first heartbeat.

```bash
cd backend && ./gradlew :runner:fatJar
java -jar runner/build/libs/runner-*-all.jar \
  --server-url localhost:9091 \
  --tenant-id "<tenant-uuid>" \
  --bootstrap-secret "${PRAVAH_RUNNER_BOOTSTRAP_SECRET}" \
  --name my-runner
# Save printed runner id + token; reconnect with:
#   --runner-id "<uuid>" --token "<token>"
```

Env: `PRAVAH_TENANT_ID`, `PRAVAH_RUNNER_BOOTSTRAP_SECRET` (required for registration; must match `pravah.runner.bootstrap-secret` on runner-service). Config: `pravah.runner.bootstrap-secret` on runner-service.

**Remote job secrets:** `${secret.*}` env refs and SQL passwords are stripped from gRPC `JobSpec.environment` into `secret_environment`. At execution time the runner resolves them with its **stream token** (not the internal service secret):

`POST /api/v1/runners/{runnerId}/jobs/{jobId}/environment-secrets` with `Authorization: Bearer <stream-token>` → runner-service (validates token + job assignment) → execution-service.

Set `--runner-http-url` or `PRAVAH_RUNNER_HTTP_URL` (default `http://localhost:8086`, runner-service HTTP port).

**Runner gRPC TLS (optional):** Set `pravah.runner.grpc.tls.enabled=true` on runner-service with `cert-chain`, `private-key`, and optional `client-ca` for mTLS. When `client-ca` is set, TLS uses optional client auth so `RegisterRunner` can run with server-trust only before Vault PKI issues a cert; `RunnerGrpcIdentityInterceptor` still requires a client certificate with SPIFFE URI SAN on `Connect`. Heartbeat `runner_id` must match the cert. Token auth remains when mTLS is off.

**Runner Vault PKI (optional):** Set `pravah.vault.enabled=true`, `pravah.runner.grpc.tls.enabled=true`, and `pravah.runner.pki.enabled=true` to issue runner client certificates on registration (`RegisterRunnerResponse.mtls`). Configure `issue-path`, `ttl`, and `spiffe-trust-domain`. Local bootstrap: `./scripts/deploy/vault-pki-bootstrap.sh`.

Runner agent: `--tls-enabled`, `--tls-trust-cert`, optional `--tls-client-cert` / `--tls-client-key` (or `PRAVAH_RUNNER_GRPC_TLS_*` env vars). When PKI is enabled server-side, client cert material is returned at registration and applied automatically.

**Scheduler Kafka DLT:** Failed trigger consumption after retries routes to `pravah.scheduler.kafka-trigger.dlt-topic` (default `pravah.scheduler.trigger.dlt`). Override with `PRAVAH_SCHEDULER_KAFKA_TRIGGER_DLT_TOPIC`.

**Scheduler coalesce catchup (US-03.15):** Set `catchupPolicy: coalesce` on a schedule. Missed cron slots within `pravah.scheduler.coalesce-max-interval` (env `PRAVAH_SCHEDULER_COALESCE_MAX_INTERVAL`, default `7d`) fire once; execution parameters include `_trigger.coalesce.{scheduleId, firstScheduledAt, lastScheduledAt, missedSlotCount}`. Spans exceeding the threshold fall back to `run_all`.

**Gateway IP allowlist (US-10.16):** Optional ingress filter via `pravah.gateway.ip-allowlist.enabled` and `pravah.gateway.ip-allowlist.cidrs` (IPv4/IPv6 CIDR or exact IP). Env: `PRAVAH_GATEWAY_IP_ALLOWLIST_ENABLED`, `PRAVAH_GATEWAY_IP_ALLOWLIST_CIDRS`.

**Method security:** Pipeline, execution, scheduler, connect, notification, and tenant REST controllers enforce `@PreAuthorize` via shared `PermissionChecker` (JWT permissions such as `pipelines:*`, `executions:*`, `users:*`, `settings:read`).

Example stage snippet:

```yaml
- id: extract
  type: container
  runOn: runner
  config:
    image: python:3.12-slim
    command: ["python", "-c", "print(1)"]
```

## Prerequisites

- Java 21 (via SDKMAN or direct install)
- Docker (for Testcontainers and local development)
- PostgreSQL 16 (via Docker or local install)
- Kafka (via Docker)
- Redis (via Docker)

## Quick Start

### Local full stack (from repo root)

```bash
make local-setup      # once: copy env templates
make local-up         # docker-compose infra
make local-services   # tenant, pipeline, execution, scheduler, notification, connect, runner, metadata, agent, gateway
make local-seed       # demo data
make local-web        # Vite dev server (../web)
```

Gateway: `http://localhost:8080`

Tenant secrets API (`/api/v1/secrets/**`) is routed to pipeline-service via the gateway (required for Vault Transit stored secrets in K8s).

### API Gateway (rate limiting, JWT blocklist)

The gateway enforces per-tenant, per-API-token, and per-IP rate limits using a **Redis token bucket** (ADR-012). Requires Redis from `docker-compose` (`REDIS_HOST` / `REDIS_PORT`, default `localhost:6379`).

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.ratelimit.enabled` | `PRAVAH_RATE_LIMIT_ENABLED` | `true` | Enable gateway rate limiting |
| `pravah.ratelimit.default-requests-per-second` | `PRAVAH_RATE_LIMIT_RPS` | `100` | Tenant JWT steady-state RPS |
| `pravah.ratelimit.default-burst-capacity` | `PRAVAH_RATE_LIMIT_BURST` | `200` | Tenant burst capacity |
| `pravah.ratelimit.api-token-requests-per-second` | `PRAVAH_RATE_LIMIT_API_TOKEN_RPS` | `50` | API token RPS |
| `pravah.ratelimit.api-token-burst-capacity` | `PRAVAH_RATE_LIMIT_API_TOKEN_BURST` | `100` | API token burst |
| `pravah.ratelimit.fail-open` | `PRAVAH_RATE_LIMIT_FAIL_OPEN` | `true` | Allow traffic when Redis/script errors (`outcome=fail_open` metric when true). Set `false` in production. |

JWT blocklist checks **fail closed** when Redis errors (ADR-012). Revoked tokens are rejected on gateway, servlet APIs, and WebSocket upgrade.

### JWT signing key (tenant-service, ADR-009)

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.security.jwt.signing-key-pem` | `PRAVAH_JWT_SIGNING_KEY` | _(ephemeral key in dev)_ | RSA private key (PEM or JWK JSON); must be stable across tenant-service replicas in production |
| `pravah.security.jwt.require-configured-signing-key` | `PRAVAH_JWT_REQUIRE_CONFIGURED_SIGNING_KEY` | `false` | Fail startup when signing key is missing |
| `pravah.security.jwt.blocklist-required` | `PRAVAH_JWT_BLOCKLIST_REQUIRED` | `false` | Deny JWT auth when Redis blocklist is not configured |

Helm: store key in Secret key `jwt-signing-key`; production overlay sets `jwtRequireConfiguredSigningKey` and `jwtBlocklistRequired`.

Responses when limited: HTTP **429**, `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Limit`. Metrics: `pravah_ratelimit_requests_total` (outcomes: `allowed`, `denied`, `fail_open`) on `/actuator/prometheus`. Outbox dead letters: `pravah_outbox_dead_lettered_total{service,event_type,topic}`.

### Distributed tracing (OpenTelemetry, pathway #11)

All Spring Boot services include Micrometer OTel bridge + OTLP exporter. Traces export when these env vars are set:

| Variable | Purpose |
|----------|---------|
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | OTLP HTTP endpoint (e.g. `http://localhost:4318/v1/traces`) |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | Sample rate `0.0`–`1.0` (default `0.1` in prod) |
| `OTEL_SERVICE_NAME` | Service name in Jaeger (set per deployment) |

**Docker Compose:** Jaeger runs on `localhost:4318` (see `backend/docker-compose.yml`). Before `make local-services`:

```bash
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
export MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces
```

Jaeger UI: http://localhost:16686

**Kubernetes:** Install observability stack (`./scripts/deploy/k8s-local-observability.sh`) and use `values-observability.yaml`. See [deploy/observability/README.md](../deploy/observability/README.md).

### Auth refresh cookie (tenant-service, ADR-009)

Login sets a **HttpOnly** refresh cookie (`pravah_refresh`, path `/api/v1/auth`). The SPA keeps the access token in memory only and calls `POST /api/v1/auth/refresh` with `credentials: include` on startup.

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.auth.refresh-cookie.secure` | `PRAVAH_AUTH_REFRESH_COOKIE_SECURE` | `false` | Set `true` in production (HTTPS) |
| `pravah.auth.refresh-cookie.max-age-seconds` | `PRAVAH_AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS` | `604800` | Refresh cookie TTL (7 days) |

### Scheduler webhooks (Redis rate limiting)

Webhook triggers (`POST /api/v1/hooks/{triggerId}`) use the same Redis token-bucket script as the gateway (`libs/common/src/main/resources/ratelimit/token_bucket.lua`). Per-trigger limit from config `rateLimitPerMinute` (default 60). Requires Redis on scheduler-service (`spring.data.redis.*`).

### Password reset email (US-10.01)

`tenant-service` sends reset links via Spring Mail. Local `docker-compose` includes **Mailhog** (SMTP `1025`, web UI `http://localhost:8025`).

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `spring.mail.host` | `MAIL_HOST` | `localhost` | SMTP host |
| `spring.mail.port` | `MAIL_PORT` | `1025` | SMTP port (Mailhog) |
| `spring.mail.username` | `MAIL_USERNAME` | _(empty)_ | SMTP auth user |
| `spring.mail.password` | `MAIL_PASSWORD` | _(empty)_ | SMTP auth password |

### HashiCorp Vault (ADR-007, pipeline connections)

Optional KV v2 resolver for `vault:path#key` credential references in pipeline connections, and Vault Transit for tenant secrets stored via the secrets API. Disabled by default.

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.vault.enabled` | `PRAVAH_VAULT_ENABLED` | `false` | Enable Vault KV reads and Transit encrypt/decrypt |
| `pravah.vault.address` | `VAULT_ADDR` | `http://localhost:8200` | Vault API URL |
| `pravah.vault.auth.method` | `PRAVAH_VAULT_AUTH_METHOD` | `token` | `token` or `kubernetes` |
| `pravah.vault.auth.token` | `VAULT_TOKEN` | _(empty)_ | Token when method=token |
| `pravah.vault.auth.kubernetes.role` | `PRAVAH_VAULT_K8S_ROLE` | _(empty)_ | K8s auth role in production |

```bash
# After make local-up
chmod +x scripts/vault/init-local-kv.sh scripts/vault/init-local-transit.sh
./scripts/vault/init-local-kv.sh
./scripts/vault/init-local-transit.sh
export PRAVAH_VAULT_ENABLED=true VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=dev-root-token
```

### notification-service (alerts, audit log, in-app notifications)

Database: `pravah_notification` (created by `docker/postgres/init.sql`). Consumes Kafka topic `pravah.execution.execution.events` (`execution.failed`, `execution.completed`, etc.). Idempotent consumer via `processed_events`.

| Property | Env | Default | Description |
|----------|-----|---------|-------------|
| `spring.mail.host` | `SMTP_HOST` | `localhost` | SMTP for alert emails (Mailhog `1025` locally) |
| `spring.mail.port` | `SMTP_PORT` | `1025` | SMTP port |
| `pravah.ui.base-url` | `PRAVAH_UI_BASE_URL` | `http://localhost:5173` | Links in email/Slack alerts |
| `pravah.security.jwt.jwks-url` | `PRAVAH_JWKS_URL` | tenant JWKS | API auth |

**REST (via gateway):** `GET/POST/PUT/DELETE /api/v1/alert-rules`, `GET /api/v1/audit-logs`, `GET/POST /api/v1/notifications`, `GET/PUT /api/v1/notification-preferences`.

### Local Kubernetes (Helm, US-09.04)

Requires Helm 3, `kubectl`, and a cluster (kind/minikube). See [deploy/README.md](../deploy/README.md).

```bash
./scripts/deploy/k8s-local-build.sh    # build + load images (kind)
./scripts/deploy/k8s-local-install.sh  # helm install
./scripts/deploy/k8s-local-smoke.sh pravah  # verify all services healthy
kubectl -n pravah port-forward svc/pravah-gateway 8080:8080
./scripts/deploy/k8s-local-seed.sh pravah
```

Container stages are disabled on K8s (`PRAVAH_CONTAINER_ENABLED=false`); use echo/SQL stages for smoke tests.

### Build and test only

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Integration tests (Docker required)
./gradlew integrationTest

# Run a single service
./gradlew :services:tenant-service:bootRun
```

## Convention Plugins

The `buildSrc` directory contains reusable Gradle plugins:

| Plugin | Description |
|--------|-------------|
| `pravah.java-conventions` | Base Java 21 setup, Lombok, testing |
| `pravah.spring-boot-conventions` | Spring Boot service configuration |
| `pravah.grpc-conventions` | Protobuf/gRPC code generation |
| `pravah.jpa-conventions` | JPA, PostgreSQL, Flyway |
| `pravah.kafka-conventions` | Spring Kafka, Testcontainers |

## Shared Libraries

### libs:common

Domain primitives and cross-cutting concerns:
- Value objects: `TenantId`, `PipelineId`, `RunId`, `JobId`, `RunnerId`
- Domain events: `PipelineEvent`, `RunEvent`, `JobEvent`, etc.
- Exceptions: `EntityNotFoundException`, `InvalidStateTransitionException`, etc.

### libs:proto

gRPC service definitions:
- `runner_service.proto` - Runner registration and job streaming
- `execution_service.proto` - Run and job management
- `common.proto` - Shared message types

### libs:test-support

Test utilities:
- `PostgresContainerExtension` - Shared PostgreSQL container
- `KafkaContainerExtension` - Shared Kafka container
- `RedisContainerExtension` - Shared Redis container (rate limit ITs)
- Fixture classes for test data generation

## Parallel stage execution (US-02.09)

Stages without dependencies can run concurrently, subject to a configurable parallelism cap. The cap is resolved per-execution:
1. `definition.execution.maxParallelStages` (workflow definition) overrides the system default.
2. System default: `pravah.execution.max-parallel-stages` (env: `PRAVAH_EXECUTION_MAX_PARALLEL_STAGES`, default `4`).

Example workflow definition snippet:
```yaml
execution:
  maxParallelStages: 2
stages:
  - id: a
    name: Stage A
  - id: b
    name: Stage B
  - id: c
    name: Stage C
    dependsOn: [a, b]
```
With `maxParallelStages: 2`, stages A and B run in parallel; stage C waits for both.

### Configuration

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.execution.max-parallel-stages` | `PRAVAH_EXECUTION_MAX_PARALLEL_STAGES` | `4` | Max concurrent stages per execution (system default) |
| `pravah.kafka.job-worker.concurrency` | `PRAVAH_JOB_WORKER_CONCURRENCY` | `4` | Kafka consumer threads for parallel job processing |

### Job timing API

The execution detail API (`GET /api/v1/executions/{id}`) returns timing fields on each job for Gantt chart visualization:

```json
{
  "jobs": [
    {
      "id": "...",
      "stageId": "build",
      "stageName": "Build",
      "status": "succeeded",
      "queuedAt": "2026-05-16T10:00:00Z",
      "startedAt": "2026-05-16T10:00:05Z",
      "completedAt": "2026-05-16T10:01:00Z"
    }
  ]
}
```

- `queuedAt`: dependencies satisfied, waiting for worker
- `startedAt`: worker picked up the job
- `completedAt`: execution finished (success, failure, or cancellation)

## Python stage execution (US-02.15)

Embedded Python stages run on the execution-service host using a per-job virtualenv.

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.python.enabled` | `PRAVAH_PYTHON_ENABLED` | `true` | Enable Python stage execution |
| `pravah.python.binary` | `PRAVAH_PYTHON_BINARY` | `python3` | Base interpreter for `venv` creation |

Example stage:

```yaml
stages:
  - id: transform
    type: python
    config:
      script: |
        import json
        print(json.dumps({"rows": 42}))
      requirements:
        - pandas==2.0.0
      env:
        MODE: batch
```

- `context.json` in the job workspace includes execution/job metadata and resolved config.
- Set env `PRAVAH_CONTEXT_PATH` to read it from the script.
- Emit structured output as a final JSON object line or `__PRAVAH_OUTPUT__:{...}` on stdout.

## Stage output and value resolution (US-02.10)

Embedded stages (echo, SQL, container) resolve `${stages.<stageId>.output.<path>}` at run time via `execution-service` (`StageOutputResolverProvider`, `ExecutionStageConfigResolver`). Pipeline publish validates references with `StageOutputReferenceValidator` in `libs:common` (wired from `pipeline-service`).

| Property | Env override | Default | Purpose |
|----------|--------------|---------|---------|
| `pravah.stage.max-output-bytes` | `PRAVAH_STAGE_MAX_OUTPUT_BYTES` | `1048576` (1MB) | Truncate persisted job output above this size |
| `pravah.stage.output-warn-bytes` | `PRAVAH_STAGE_OUTPUT_WARN_BYTES` | `102400` (100KB) | Structured warn log when output exceeds threshold |

See [Value resolution LLD](../docs/lld/07-value-resolution.md).

## Execution real-time (US-12.10)

`execution-service` exposes `GET /ws/v1/executions` for ephemeral `execution.updated` frames (see [LLD](../docs/lld/07-execution-realtime-websocket.md)).

| Variable | Default | Purpose |
|----------|---------|---------|
| `PRAVAH_REALTIME_REDIS_ENABLED` | `true` | Set `false` for single-pod local runs without Redis (tests use this) |
| `PRAVAH_WS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | SPA origins allowed for WebSocket handshake |

Start Redis from `docker-compose.yml` when running execution-service with defaults. Gateway routes `/ws/**` to execution-service.

## Service Ports

| Service | HTTP Port | gRPC Port |
|---------|-----------|-----------|
| Gateway | 8080 | - |
| GraphQL | 8081 | - |
| Tenant Service | 8082 | - |
| Pipeline Service | 8083 | - |
| Execution Service | 8084 | 9090 |
| Scheduler Service | 8085 | - |
| Runner Service | 8086 | 9091 |
| Metadata Service | 8087 | - |
| Notification Service | 8088 | - |
| Agent Service | 8089 | - |
| Connect Service | 8091 | JWT + internal S2S (port 8091 avoids Kafka UI on 8090 locally) |

## API Documentation (US-11.12)

Each implemented service exposes OpenAPI 3.0 documentation via SpringDoc:

| Service | Swagger UI | OpenAPI Spec |
|---------|------------|--------------|
| tenant-service | http://localhost:8082/swagger-ui.html | `/v3/api-docs` |
| pipeline-service | http://localhost:8083/swagger-ui.html | `/v3/api-docs` |
| execution-service | http://localhost:8084/swagger-ui.html | `/v3/api-docs` |
| scheduler-service | http://localhost:8085/swagger-ui.html | `/v3/api-docs` |
| notification-service | http://localhost:8088/swagger-ui.html | `/v3/api-docs` |

Features:
- Interactive "Try it out" for all endpoints
- JWT Bearer authentication configured
- Request/response schemas with examples
- Error response documentation

**Note:** Access Swagger UI directly on the service port; the gateway does not aggregate specs.

## Documentation References

- [Implementation Status](../docs/IMPLEMENTATION_STATUS.md)
- [High-Level Architecture](../docs/architecture/high-level-architecture.md) (target; see status doc for gaps)
- [API Contracts](../docs/architecture/api-contracts.md)
- [Database ERD](../docs/lld/02-database-erd.md)
- [State Machines](../docs/lld/03-state-machines.md)
- [Design Patterns](../docs/lld/01-design-patterns.md)
- [Value resolution](../docs/lld/07-value-resolution.md)
- [Execution WebSocket](../docs/lld/07-execution-realtime-websocket.md)
