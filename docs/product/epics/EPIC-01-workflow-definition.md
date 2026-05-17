# EPIC-01: Workflow Definition

## Overview

Enable users to create, edit, version, and manage data workflows through both code and UI interfaces. Workflows are the core unit of work in Pravah — a directed acyclic graph (DAG) of stages that transform and move data.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 89 story points  
**Related ADRs:** ADR-001, ADR-002, ADR-006

---

## Goals

1. Users can define workflows in YAML, JSON, or Python DSL
2. Users can create/edit workflows visually in the UI
3. Workflows are versioned with full history
4. Git sync keeps workflows in sync with source control
5. Variables and secrets integrate seamlessly

---

## User Stories

### US-01.01: Create Workflow via YAML
**As a** data engineer  
**I want to** define a workflow in a YAML file  
**So that** I can version control it with my code

**Acceptance Criteria:**
- [ ] YAML schema is documented and validated
- [ ] Validation errors show line numbers and suggestions
- [ ] Workflow can be uploaded via CLI or API
- [ ] UI shows rendered workflow from YAML

**Story Points:** 5  
**Priority:** P0

---

### US-01.02: Create Workflow via UI
**As a** data engineer  
**I want to** create a workflow using a visual editor  
**So that** I can quickly prototype without writing code

**Acceptance Criteria:**
- [ ] Drag-and-drop stage creation
- [ ] Visual connection of dependencies
- [ ] Form-based stage configuration
- [ ] Generated YAML can be exported
- [ ] Undo/redo support

**Story Points:** 13  
**Priority:** P0

---

### US-01.03: Define Stage Types
**As a** data engineer  
**I want to** choose from different stage types  
**So that** I can use the right tool for each task

**Acceptance Criteria:**
- [x] SQL Transform stage (query against warehouse) — `SqlEmbeddedStageExecutor`
- [ ] Python Script stage (alpha: YAML validation only; execution returns planned-stage message)
- [ ] dbt Model stage (alpha: validation only)
- [ ] Spark Job stage (alpha: validation only)
- [x] Custom Container stage (any Docker image) — `ContainerEmbeddedStageExecutor` (local Docker)

**Story Points:** 8  
**Priority:** P0

---

### US-01.04: Configure Stage Dependencies
**As a** data engineer  
**I want to** define which stages depend on others  
**So that** they execute in the correct order

**Acceptance Criteria:**
- [ ] Explicit `depends_on` in YAML/JSON
- [ ] Visual connection in UI editor
- [ ] Cycle detection with clear error message
- [ ] Parallel execution of independent stages

**Story Points:** 5  
**Priority:** P0

---

### US-01.05: Use Variables in Workflow
**As a** data engineer  
**I want to** use variables in my workflow definition  
**So that** I can parameterize without hardcoding

**Acceptance Criteria:**
- [ ] Define variables at workflow level
- [ ] Reference variables with `${var.name}` syntax
- [ ] Environment-specific variable overrides
- [ ] Variable validation (required, type, default)

**Story Points:** 5  
**Priority:** P0

---

### US-01.06: Use Secrets in Workflow
**As a** data engineer  
**I want to** reference secrets securely  
**So that** credentials aren't in my workflow code

**Acceptance Criteria:**
- [ ] Reference secrets with `${secret.name}` syntax
- [ ] Secrets fetched at runtime from Vault
- [ ] Secrets never logged or displayed
- [ ] Audit log when secrets are accessed

**Story Points:** 5  
**Priority:** P0

---

### US-01.07: Version Workflows
**As a** data engineer  
**I want to** see version history of my workflow  
**So that** I can track changes and rollback if needed

**Acceptance Criteria:**
- [ ] Every save creates a new version
- [ ] Version list with timestamps and authors
- [ ] Diff view between versions
- [ ] One-click rollback to previous version
- [ ] Version numbers are immutable

**Story Points:** 8  
**Priority:** P0

---

### US-01.08: Git Sync - Connect Repository
**As a** data engineer  
**I want to** connect my Git repository  
**So that** workflows sync automatically

**Acceptance Criteria:**
- [ ] Connect GitHub, GitLab, Bitbucket
- [ ] OAuth flow for authentication
- [ ] Select repository and branch
- [ ] Configure sync directory

**Story Points:** 8  
**Priority:** P1

---

### US-01.09: Git Sync - Auto Pull
**As a** data engineer  
**I want to** have workflows update when I push to Git  
**So that** my Git repo is the source of truth

**Acceptance Criteria:**
- [ ] Webhook triggers sync on push
- [ ] Only syncs configured branch (e.g., main)
- [ ] Validation before applying changes
- [ ] Sync status visible in UI
- [ ] Conflict handling for concurrent edits

**Story Points:** 8  
**Priority:** P1

---

### US-01.10: Git Sync - Push Changes
**As a** data engineer  
**I want to** push UI changes back to Git  
**So that** my repo stays up-to-date

**Acceptance Criteria:**
- [ ] "Commit" button in UI editor
- [ ] Commit message prompt
- [ ] Creates branch or commits to main
- [ ] Pull request creation option

**Story Points:** 5  
**Priority:** P1

---

### US-01.11: Workflow Templates
**As a** data engineer  
**I want to** start from a template  
**So that** I don't have to build common patterns from scratch

**Acceptance Criteria:**
- [ ] Built-in templates (ETL, ML, dbt)
- [ ] Community template marketplace
- [ ] Preview template before using
- [ ] Customize after applying template

**Story Points:** 5  
**Priority:** P2

---

### US-01.12: Validate Workflow
**As a** data engineer  
**I want to** validate my workflow before running  
**So that** I catch errors early

**Acceptance Criteria:**
- [ ] Schema validation (structure, types)
- [ ] Dependency validation (no cycles, missing refs)
- [ ] Connection validation (test credentials)
- [ ] Resource validation (images exist, permissions)
- [ ] Validation runs on save and before deploy

**Story Points:** 8  
**Priority:** P0

---

### US-01.13: Dry Run Workflow
**As a** data engineer  
**I want to** dry-run my workflow  
**So that** I can see what would happen without executing

**Acceptance Criteria:**
- [ ] Shows execution plan (stage order, parallelism)
- [ ] Resolves all variables
- [ ] Validates connections without running queries
- [ ] Estimates resource usage
- [ ] Warnings for potential issues

**Story Points:** 5  
**Priority:** P1

---

### US-01.14: Clone Workflow
**As a** data engineer  
**I want to** clone an existing workflow  
**So that** I can create variations without starting over

**Acceptance Criteria:**
- [ ] Clone with new name
- [ ] Clone to different project
- [ ] Option to include or exclude schedules
- [ ] History shows cloned-from relationship

**Story Points:** 2  
**Priority:** P1

---

### US-01.15: Archive/Delete Workflow
**As a** data engineer  
**I want to** archive or delete workflows I no longer need  
**So that** my workspace stays clean

**Acceptance Criteria:**
- [ ] Archive stops scheduling but preserves history
- [ ] Delete requires confirmation
- [ ] Delete checks for dependencies
- [ ] Archived workflows can be restored
- [ ] Audit log for both actions

**Story Points:** 3  
**Priority:** P1

---

### US-01.16: Workflow Documentation
**As a** data engineer  
**I want to** document my workflow  
**So that** others understand what it does

**Acceptance Criteria:**
- [ ] Description field (markdown supported)
- [ ] Per-stage descriptions
- [ ] Auto-generated documentation from schema
- [ ] README file from Git repo displayed

**Story Points:** 3  
**Priority:** P1

---

### US-01.17: Workflow Tags
**As a** data engineer  
**I want to** tag workflows  
**So that** I can organize and filter them

**Acceptance Criteria:**
- [ ] Add multiple tags to workflow
- [ ] Create new tags inline
- [ ] Filter workflow list by tags
- [ ] Tag management (rename, merge, delete)

**Story Points:** 2  
**Priority:** P1

---

### US-01.18: Import from Airflow
**As a** data engineer  
**I want to** import my Airflow DAGs  
**So that** I can migrate to Pravah

**Acceptance Criteria:**
- [ ] Parse Airflow DAG Python files
- [ ] Map operators to Pravah stages
- [ ] Import schedules and dependencies
- [ ] Generate migration report (what needs manual work)
- [ ] Preview before applying

**Story Points:** 13  
**Priority:** P2

---

## Technical Tasks

### T-01.01: Workflow Schema Definition
Define the YAML/JSON schema for workflows.

**Related ADR:** ADR-001  
**Acceptance Criteria:**
- JSON Schema with validation rules
- Version field for schema evolution
- Documentation generated from schema

**Estimate:** 3 points

---

### T-01.02: Workflow Parser Service
Build service to parse and validate workflow definitions.

**Acceptance Criteria:**
- Parse YAML, JSON, Python DSL
- Validate against schema
- Return structured errors with locations
- Cache parsed workflows

**Estimate:** 5 points

---

### T-01.03: Workflow Storage Model
Design database schema for workflow storage.

**Related ADR:** ADR-003  
**Acceptance Criteria:**
- Versioned storage (every change creates version)
- Efficient retrieval of current and historical
- Tenant isolation via RLS

**Estimate:** 5 points

---

### T-01.04: Workflow API Endpoints
Implement REST API for workflow CRUD.

**Acceptance Criteria:**
- POST /workflows - create
- GET /workflows - list with pagination
- GET /workflows/{id} - get with version history
- PUT /workflows/{id} - update (creates version)
- DELETE /workflows/{id} - soft delete

**Estimate:** 5 points

---

### T-01.05: GraphQL Workflow Queries
Implement GraphQL schema and resolvers for workflows.

**Related ADR:** ADR-033  
**Acceptance Criteria:**
- Query: workflows, workflow(id)
- Nested: workflow.stages, workflow.versions
- DataLoader for N+1 prevention

**Estimate:** 5 points

---

### T-01.06: Git Integration Service
Build service to sync with Git providers.

**Acceptance Criteria:**
- OAuth flows for GitHub, GitLab, Bitbucket
- Webhook handling for push events
- Pull and push operations
- Conflict detection

**Estimate:** 13 points

---

### T-01.07: Variable Resolution Engine
Build engine to resolve variables at parse time.

**Acceptance Criteria:**
- Variable syntax parsing
- Environment-specific resolution
- Circular reference detection
- Type coercion

**Estimate:** 5 points

---

### T-01.08: Workflow Visual Editor Component
Build React component for visual workflow editing.

**Acceptance Criteria:**
- DAG rendering with react-flow
- Drag-and-drop stage creation
- Connection drawing
- Form panel for stage config
- Undo/redo state management

**Estimate:** 21 points (split into sub-tasks)

---

## Dependencies

| Dependency | Epic | Required For |
|------------|------|--------------|
| Database Schema | EPIC-09 | T-01.03 |
| Authentication | EPIC-10 | All APIs |
| Secret Management | EPIC-10 | US-01.06 |
| Basic UI Shell | EPIC-12 | US-01.02 |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| YAML schema too complex | Medium | High | User testing, start simple |
| Git sync conflicts | Medium | Medium | Clear conflict resolution UI |
| Visual editor performance | Low | High | Virtualization, lazy rendering |
| Migration tool accuracy | High | Medium | Manual review step required |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Workflow creation time | < 10 min for simple | Track in analytics |
| Validation error clarity | > 80% resolved without docs | User survey |
| Git sync reliability | 99.9% successful syncs | Monitor sync failures |
| UI editor adoption | > 30% of workflows | Track creation method |

---

## Open Questions

1. Should Python DSL support decorators like Dagster?
2. How do we handle workflow schema migrations?
3. Should templates be versioned independently?

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
