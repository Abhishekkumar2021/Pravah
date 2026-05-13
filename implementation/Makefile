# ============================================================================
# Pravah Makefile
# Common development commands
# ============================================================================

.PHONY: help build test clean format lint docker-up docker-down

# Default target
help:
	@echo "Pravah Development Commands"
	@echo ""
	@echo "Build & Test:"
	@echo "  make build        - Build all modules"
	@echo "  make test         - Run all tests"
	@echo "  make test-unit    - Run unit tests only"
	@echo "  make test-int     - Run integration tests only"
	@echo "  make clean        - Clean build artifacts"
	@echo ""
	@echo "Code Quality:"
	@echo "  make format       - Format code with Spotless"
	@echo "  make lint         - Check code formatting"
	@echo "  make security     - Run security scan"
	@echo ""
	@echo "Docker:"
	@echo "  make docker-up    - Start local infrastructure"
	@echo "  make docker-down  - Stop local infrastructure"
	@echo "  make docker-logs  - View infrastructure logs"
	@echo ""
	@echo "Services:"
	@echo "  make run-tenant   - Run Tenant Service"
	@echo "  make run-pipeline - Run Pipeline Service"
	@echo "  make run-all      - Run all services (requires docker-up)"

# ============================================================================
# Build & Test
# ============================================================================

build:
	./gradlew build -x test

test: test-unit

test-unit:
	./gradlew test

test-int:
	./gradlew integrationTest

clean:
	./gradlew clean

# ============================================================================
# Code Quality
# ============================================================================

format:
	./gradlew spotlessApply

lint:
	./gradlew spotlessCheck

security:
	./gradlew dependencyCheckAnalyze

# ============================================================================
# Docker Infrastructure
# ============================================================================

docker-up:
	docker compose up -d
	@echo "Waiting for services to be healthy..."
	@sleep 10
	@echo ""
	@echo "Services available at:"
	@echo "  PostgreSQL:  localhost:5432"
	@echo "  Kafka:       localhost:29092"
	@echo "  Kafka UI:    http://localhost:8090"
	@echo "  Redis:       localhost:6379"
	@echo "  Jaeger UI:   http://localhost:16686"
	@echo "  MinIO:       http://localhost:9001 (pravah/pravah123)"
	@echo "  MailHog:     http://localhost:8025"

docker-down:
	docker compose down

docker-logs:
	docker compose logs -f

docker-clean:
	docker compose down -v --remove-orphans

# ============================================================================
# Run Services
# ============================================================================

run-tenant:
	./gradlew :services:tenant-service:bootRun

run-pipeline:
	./gradlew :services:pipeline-service:bootRun

run-execution:
	./gradlew :services:execution-service:bootRun

run-gateway:
	./gradlew :services:gateway:bootRun

# ============================================================================
# Database
# ============================================================================

db-migrate:
	./gradlew flywayMigrate

db-clean:
	./gradlew flywayClean

db-info:
	./gradlew flywayInfo

# ============================================================================
# Proto
# ============================================================================

proto-gen:
	./gradlew :libs:proto:generateProto
