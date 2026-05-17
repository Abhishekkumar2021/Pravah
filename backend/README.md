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
├── docker-compose.yml           # Local Postgres, Kafka, Redis, Jaeger, MinIO, Mailhog
├── scripts/local/               # local-up, local-services, seed
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── settings.gradle.kts          # Module includes
└── build.gradle.kts             # Root build configuration
```

**Service implementation status:** see [Implementation Status](../docs/IMPLEMENTATION_STATUS.md). Implemented: gateway, tenant, pipeline, execution, scheduler. Stubs: graphql, runner-service, metadata, notification, agent, connect.

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
make local-services   # tenant, pipeline, execution, scheduler, gateway
make local-seed       # demo data
make local-web        # Vite dev server (../web)
```

Gateway: `http://localhost:8080`

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
- Fixture classes for test data generation

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
| Connect Service | 8090 | - |

## Documentation References

- [Implementation Status](../docs/IMPLEMENTATION_STATUS.md)
- [High-Level Architecture](../docs/architecture/high-level-architecture.md) (target; see status doc for gaps)
- [API Contracts](../docs/architecture/api-contracts.md)
- [Database ERD](../docs/lld/02-database-erd.md)
- [State Machines](../docs/lld/03-state-machines.md)
- [Design Patterns](../docs/lld/01-design-patterns.md)
- [Value resolution](../docs/lld/07-value-resolution.md)
- [Execution WebSocket](../docs/lld/07-execution-realtime-websocket.md)
