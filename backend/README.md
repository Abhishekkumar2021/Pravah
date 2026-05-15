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
│   ├── common/                  # Domain primitives, events, exceptions
│   ├── proto/                   # gRPC/Protobuf definitions
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
├── runner/                      # Standalone runner binary
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── settings.gradle.kts          # Module includes
└── build.gradle.kts             # Root build configuration
```

## Prerequisites

- Java 21 (via SDKMAN or direct install)
- Docker (for Testcontainers and local development)
- PostgreSQL 16 (via Docker or local install)
- Kafka (via Docker)
- Redis (via Docker)

## Quick Start

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Compile only
./gradlew compileJava

# Run a specific service
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

- [High-Level Architecture](../docs/architecture/high-level-architecture.md)
- [API Contracts](../docs/architecture/api-contracts.md)
- [Database ERD](../docs/lld/02-database-erd.md)
- [State Machines](../docs/lld/03-state-machines.md)
- [Design Patterns](../docs/lld/01-design-patterns.md)
