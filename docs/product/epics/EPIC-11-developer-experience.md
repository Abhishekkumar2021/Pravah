# EPIC-11: Developer Experience

## Overview

Tools and integrations for developers — CLI, SDK, IDE plugins, and CI/CD integration. Make developers productive and happy.

**Epic Owner:** Platform Team  
**Priority:** P1  
**Estimated Effort:** 88 story points  
**Related ADRs:** ADR-005, ADR-033

---

## Goals

1. Powerful CLI for all operations
2. Python SDK for programmatic access
3. IDE integration for workflow development
4. CI/CD integration for GitOps
5. Excellent documentation and examples

---

## User Stories

### US-11.01: CLI - Installation
**As a** developer  
**I want to** install the CLI easily  
**So that** I can use it quickly

**Acceptance Criteria:**
- [ ] brew install pravah
- [ ] pip install pravah-cli
- [ ] curl | bash installer
- [ ] Version check and auto-update

**Story Points:** 5  
**Priority:** P1

---

### US-11.02: CLI - Authentication
**As a** developer  
**I want to** authenticate the CLI  
**So that** I can access my account

**Acceptance Criteria:**
- [ ] pravah login (browser flow)
- [ ] API token authentication
- [ ] Profile management
- [ ] Secure credential storage

**Story Points:** 5  
**Priority:** P1

---

### US-11.03: CLI - Workflow Commands
**As a** developer  
**I want to** manage workflows via CLI  
**So that** I work from terminal

**Acceptance Criteria:**
- [ ] pravah workflow list
- [ ] pravah workflow create -f file.yaml
- [ ] pravah workflow run
- [ ] pravah workflow logs --follow

**Story Points:** 8  
**Priority:** P1

---

### US-11.04: CLI - Run Commands
**As a** developer  
**I want to** manage runs via CLI  
**So that** I monitor and control execution

**Acceptance Criteria:**
- [ ] pravah run list
- [ ] pravah run status <id>
- [ ] pravah run logs <id>
- [ ] pravah run cancel <id>

**Story Points:** 5  
**Priority:** P1

---

### US-11.05: CLI - Deploy Commands
**As a** developer  
**I want to** deploy workflows via CLI  
**So that** I can automate CI/CD

**Acceptance Criteria:**
- [ ] pravah deploy -f file.yaml
- [ ] pravah deploy --dry-run
- [ ] pravah diff (show changes)
- [ ] Exit codes for CI integration

**Story Points:** 5  
**Priority:** P1

---

### US-11.06: Python SDK - Core
**As a** developer  
**I want to** a Python SDK  
**So that** I can build integrations

**Acceptance Criteria:**
- [ ] pip install pravah
- [ ] Client with auth
- [ ] Async support
- [ ] Type hints

**Story Points:** 8  
**Priority:** P1

---

### US-11.07: Python SDK - Workflow API
**As a** developer  
**I want to** manage workflows programmatically  
**So that** I automate operations

**Acceptance Criteria:**
- [ ] client.workflows.list()
- [ ] client.workflows.create(definition)
- [ ] client.workflows.run(id, params)
- [ ] Pagination support

**Story Points:** 5  
**Priority:** P1

---

### US-11.08: Python SDK - DSL
**As a** developer  
**I want to** define workflows in Python  
**So that** I use native constructs

**Acceptance Criteria:**
- [ ] @workflow decorator
- [ ] @task decorator
- [ ] Dependencies via function calls
- [ ] Export to YAML

**Story Points:** 8  
**Priority:** P2

---

### US-11.09: VS Code Extension
**As a** developer  
**I want to** IDE support for workflows  
**So that** I develop efficiently

**Acceptance Criteria:**
- [ ] Syntax highlighting for YAML
- [ ] Schema validation inline
- [ ] Autocomplete for stage types
- [ ] Preview workflow graph

**Story Points:** 13  
**Priority:** P2

---

### US-11.10: GitHub Action
**As a** developer  
**I want to** integrate with GitHub Actions  
**So that** deployments are automated

**Acceptance Criteria:**
- [ ] pravah-deploy action
- [ ] Input: workflow files
- [ ] Output: deployment status
- [ ] Example workflow files

**Story Points:** 5  
**Priority:** P1

---

### US-11.11: GitLab CI Template
**As a** developer  
**I want to** GitLab CI integration  
**So that** I use GitLab for deployment

**Acceptance Criteria:**
- [ ] .gitlab-ci.yml template
- [ ] Docker image with CLI
- [ ] Example configuration
- [ ] Documentation

**Story Points:** 3  
**Priority:** P2

---

### US-11.12: REST API Documentation
**As a** developer  
**I want to** comprehensive API docs  
**So that** I use the API correctly

**Acceptance Criteria:**
- [ ] OpenAPI specification
- [ ] Interactive docs (Swagger UI)
- [ ] Code examples per endpoint
- [ ] Error documentation

**Story Points:** 8  
**Priority:** P1

---

### US-11.13: GraphQL API
**As a** developer  
**I want to** GraphQL API for flexibility  
**So that** I query exactly what I need

**Acceptance Criteria:**
- [ ] GraphQL schema
- [ ] Playground UI
- [ ] Authentication support
- [ ] Subscriptions for real-time

**Story Points:** 13  
**Priority:** P1

---

### US-11.14: Webhooks API
**As a** developer  
**I want to** receive webhooks  
**So that** I react to events

**Acceptance Criteria:**
- [ ] Configure webhook URLs
- [ ] Event type selection
- [ ] Signature verification
- [ ] Retry on failure
- [ ] Delivery logs

**Story Points:** 8  
**Priority:** P1

---

## Technical Tasks

### T-11.01: CLI Framework
Choose and set up CLI framework (Cobra/Click).

**Estimate:** 3 points

### T-11.02: CLI Commands
Implement all CLI commands.

**Estimate:** 13 points

### T-11.03: Python SDK
Build Python SDK package.

**Estimate:** 13 points

### T-11.04: VS Code Extension
Build VS Code extension.

**Estimate:** 13 points

### T-11.05: GitHub Action
Build and publish GitHub Action.

**Estimate:** 5 points

### T-11.06: OpenAPI Spec
Generate and maintain OpenAPI spec.

**Estimate:** 5 points

### T-11.07: GraphQL Schema
Design and implement GraphQL schema.

**Estimate:** 8 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| CLI downloads | > 1000/month |
| SDK downloads | > 500/month |
| API doc satisfaction | > 4/5 |
| Time to first API call | < 10 minutes |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
