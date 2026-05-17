# ============================================================================
# Pravah Makefile
# Common development commands
# ============================================================================

.PHONY: help build test clean format lint docker-up docker-down \
	local-setup local-up local-down local-services local-services-stop \
	local-status local-dev-token local-web local-print-token local-seed

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
	@echo ""
	@echo "Local full stack (from backend/):"
	@echo "  make local-setup         - Copy env.local.example → .env and web/.env.local"
	@echo "  make local-up            - Docker infra + wait for health"
	@echo "  make local-services      - Start tenant, pipeline, execution, scheduler, gateway (background)"
	@echo "  make local-services-stop - Stop background Java services"
	@echo "  make local-dev-token     - Mint JWT via POST /api/v1/auth/dev-token"
	@echo "  make local-print-token   - Mint JWT and show DevTokenCard / project id hints"
	@echo "  make local-seed          - Load demo tenant, workflows, and runs into Postgres"
	@echo "  make local-status        - Ports + pid status"
	@echo "  make local-web           - npm run dev in ../web"
	@echo "  make local-down          - Stop Java services + docker compose down"

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
# Local stack (env files + scripts/local/*.sh)
# ============================================================================

local-setup:
	@chmod +x scripts/local/*.sh
	@./scripts/local/setup.sh

local-up: local-setup docker-up
	@./scripts/local/wait-infra.sh

local-down: local-services-stop docker-down

local-services:
	@chmod +x scripts/local/*.sh
	@./scripts/local/start-services.sh

local-services-stop:
	@chmod +x scripts/local/*.sh
	@./scripts/local/stop-services.sh

local-status:
	@chmod +x scripts/local/*.sh
	@./scripts/local/status.sh

local-dev-token:
	@chmod +x scripts/local/*.sh
	@./scripts/local/mint-dev-token.sh

local-seed:
	@chmod +x scripts/local/seed-demo.sh
	@./scripts/local/seed-demo.sh

local-print-token: local-dev-token
	@echo ""
	@echo "Paste the token above in the UI: Login → Local development, or Dashboard → Alpha setup."
	@echo "Project id (if needed): $${PRAVAH_DEV_PROJECT_ID:-33333333-3333-4333-8333-333333333333}"

local-web:
	@cd ../web && npm run dev

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
