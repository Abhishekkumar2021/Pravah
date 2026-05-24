# EPIC-09: Platform & Infrastructure

## Overview

The foundational infrastructure that powers Pravah — multi-tenancy, database, Kafka, Kubernetes, observability, and deployment. This is the skeleton on which everything runs.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 128 story points  
**Related ADRs:** ADR-003, ADR-013, ADR-022

---

## Goals

1. True multi-tenancy with strong isolation
2. Horizontal scalability to 1000+ concurrent workflows
3. High availability (99.9% uptime)
4. Self-hosted and cloud deployment options
5. Infrastructure as Code for all components

---

## User Stories

### US-09.01: Tenant Hierarchy
**As a** platform admin  
**I want to** organize resources hierarchically  
**So that** access and billing are clear

**Acceptance Criteria:**
- [ ] Organization → Teams → Projects → Environments
- [ ] Resources scoped to appropriate level
- [ ] Cross-project visibility for org admins
- [ ] Self-service team creation

**Story Points:** 13  
**Priority:** P0

---

### US-09.02: Resource Isolation
**As a** platform admin  
**I want to** tenants isolated from each other  
**So that** there's no cross-tenant access

**Acceptance Criteria:**
- [ ] Database RLS for all tables
- [ ] Kafka topic prefixing
- [ ] Runner namespace isolation
- [ ] Secret isolation

**Story Points:** 13  
**Priority:** P0

---

### US-09.03: Resource Quotas
**As a** platform admin  
**I want to** set resource quotas  
**So that** teams don't overconsume

**Acceptance Criteria:**
- [ ] Concurrent runs limit
- [ ] Storage limit
- [ ] API rate limits
- [ ] Enforcement with clear errors

**Story Points:** 8  
**Priority:** P1

---

### US-09.04: Kubernetes Deployment
**As a** platform engineer  
**I want to** deploy Pravah on Kubernetes  
**So that** we run in our infrastructure

**Acceptance Criteria:**
- [x] Helm chart provided (`deploy/helm/pravah-platform`; 8 platform services + prod validation gates)
- [x] Configurable for scale (`replicaCount`, `values-prod.yaml`)
- [x] HA configuration (PDB/HPA/replicas in `values-prod.yaml`; external managed deps)
- [x] Clear installation docs (`deploy/README.md`)
- [x] GitOps Application manifests (`deploy/argocd/`; local auto-sync + production manual sync; CI smoke)

**Story Points:** 13  
**Priority:** P0

---

### US-09.05: Docker Compose Local
**As a** developer  
**I want to** run Pravah locally  
**So that** I can test and develop

**Acceptance Criteria:**
- [ ] Single docker-compose up
- [ ] All dependencies included
- [ ] Seed data for demo
- [ ] Documented resource requirements

**Story Points:** 5  
**Priority:** P0

---

### US-09.06: Database Setup
**As a** platform engineer  
**I want to** PostgreSQL configured properly  
**So that** data is safe and performant

**Acceptance Criteria:**
- [ ] Schema migrations (Flyway)
- [ ] Connection pooling (PgBouncer)
- [ ] Backup configuration
- [ ] Monitoring queries

**Story Points:** 8  
**Priority:** P0

---

### US-09.07: Kafka Setup
**As a** platform engineer  
**I want to** Kafka configured for reliability  
**So that** events are not lost

**Acceptance Criteria:**
- [ ] Topic configuration
- [ ] Replication factor
- [ ] Schema Registry
- [ ] Consumer lag monitoring

**Story Points:** 8  
**Priority:** P0

---

### US-09.08: Service Discovery
**As a** platform engineer  
**I want to** services to discover each other  
**So that** scaling is automatic

**Acceptance Criteria:**
- [x] Kubernetes Service DNS (Helm Services; gateway routes via cluster DNS)
- [x] Health checks (`/actuator/health` probes; `k8s-local-smoke.sh` post-install validation)
- [x] Automated kind smoke in CI (nightly + PR; `k8s-smoke-nightly.yml`)
- [ ] Graceful degradation

**Story Points:** 5  
**Priority:** P0

---

### US-09.09: Horizontal Scaling
**As a** platform engineer  
**I want to** scale services horizontally  
**So that** we handle increased load

**Acceptance Criteria:**
- [ ] Stateless services
- [ ] Auto-scaling based on metrics
- [ ] KEDA for event-driven scaling
- [ ] Zero-downtime scaling

**Story Points:** 8  
**Priority:** P0

---

### US-09.10: High Availability
**As a** platform engineer  
**I want to** no single point of failure  
**So that** Pravah stays up

**Acceptance Criteria:**
- [ ] Multiple replicas per service
- [ ] Database failover
- [ ] Kafka replication
- [ ] Leader election for schedulers

**Story Points:** 13  
**Priority:** P1

---

### US-09.11: Disaster Recovery
**As a** platform engineer  
**I want to** DR procedures documented  
**So that** we recover from failures

**Acceptance Criteria:**
- [ ] Backup procedures
- [ ] Restore procedures
- [ ] RPO and RTO defined
- [ ] Regular DR testing

**Story Points:** 8  
**Priority:** P1

---

### US-09.12: Upgrade Process
**As a** platform engineer  
**I want to** upgrade without downtime  
**So that** users aren't impacted

**Acceptance Criteria:**
- [ ] Rolling deployment
- [ ] Database migration strategy
- [ ] Rollback procedure
- [ ] Upgrade testing

**Story Points:** 8  
**Priority:** P1

---

### US-09.13: Configuration Management
**As a** platform engineer  
**I want to** configure via environment  
**So that** deployment is flexible

**Acceptance Criteria:**
- [ ] Environment variables
- [ ] ConfigMaps/Secrets
- [ ] Defaults with overrides
- [ ] Validation on startup

**Story Points:** 5  
**Priority:** P0

---

### US-09.14: Observability Stack
**As a** platform engineer  
**I want to** built-in observability  
**So that** I diagnose issues

**Acceptance Criteria:**
- [ ] Metrics (Prometheus)
- [ ] Logs (structured, aggregated)
- [ ] Traces (OpenTelemetry)
- [ ] Pre-built dashboards

**Story Points:** 13  
**Priority:** P0

---

### US-09.15: Infrastructure as Code
**As a** platform engineer  
**I want to** all infrastructure in code  
**So that** deployments are repeatable

**Acceptance Criteria:**
- [ ] Terraform modules (AWS, GCP, Azure)
- [ ] Helm chart
- [ ] Example configurations
- [ ] Cost estimates

**Story Points:** 13  
**Priority:** P1

---

### US-09.16: Connection Pooling
**As a** platform engineer  
**I want to** database connections pooled  
**So that** we don't exhaust connections

**Acceptance Criteria:**
- [ ] PgBouncer configuration
- [ ] Transaction pooling
- [ ] Monitoring pool usage
- [ ] Alert on exhaustion

**Story Points:** 5  
**Priority:** P0

---

### US-09.17: Cache Layer
**As a** platform engineer  
**I want to** caching for performance  
**So that** repeated queries are fast

**Acceptance Criteria:**
- [ ] Redis for distributed cache
- [ ] Cache invalidation strategy
- [ ] TTL configuration
- [ ] Cache hit ratio monitoring

**Story Points:** 5  
**Priority:** P1

---

### US-09.18: Storage Backend
**As a** platform engineer  
**I want to** object storage configured  
**So that** artifacts are persisted

**Acceptance Criteria:**
- [ ] S3/GCS/MinIO support
- [ ] Encryption at rest
- [ ] Lifecycle policies
- [ ] Access via signed URLs

**Story Points:** 8  
**Priority:** P0

---

## Technical Tasks

### T-09.01: Database Schema Design
Design core PostgreSQL schema with RLS.

**Estimate:** 13 points

### T-09.02: Flyway Migrations Setup
Configure Flyway for migrations.

**Estimate:** 3 points

### T-09.03: Kafka Topics & Schema
Define Kafka topics and Avro schemas.

**Estimate:** 8 points

### T-09.04: Helm Chart
Build production Helm chart.

**Estimate:** 13 points

### T-09.05: Docker Compose Dev
Build docker-compose for local dev.

**Estimate:** 5 points

### T-09.06: Terraform Modules
Build Terraform for cloud deployment.

**Estimate:** 21 points

### T-09.07: Observability Integration
Integrate Prometheus, Jaeger, Loki.

**Estimate:** 13 points

### T-09.09: Argo CD GitOps
Argo CD Application manifests, local/CI install scripts, production Application, GitOps image tag automation, and nightly Argo CD kind smoke (ADR-010, pathway #9). **Partial (2026-05):** `deploy/argocd/`, `k8s-local-argocd.sh`, `k8s-ci-smoke-argocd.sh`, `deploy.yml` GitOps tag bump, gateway secrets route, external Vault policy example.

**Estimate:** 8 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Platform uptime | 99.9% |
| P99 API latency | < 500ms |
| Horizontal scale time | < 5 minutes |
| Deployment time | < 30 minutes |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
