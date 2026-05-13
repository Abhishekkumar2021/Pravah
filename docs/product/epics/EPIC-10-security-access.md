# EPIC-10: Security & Access Control

## Overview

Secure the platform with authentication, authorization, secrets management, and audit trails. Security is foundational, not optional.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 104 story points  
**Related ADRs:** ADR-007, ADR-008, ADR-012

---

## Goals

1. Enterprise-grade authentication (SSO, SAML)
2. Granular RBAC with custom roles
3. Secure secrets management via Vault
4. Complete audit trail
5. API security with tokens and rate limiting

---

## User Stories

### US-10.01: Local Authentication
**As a** user  
**I want to** sign in with email/password  
**So that** I access Pravah

**Acceptance Criteria:**
- [ ] Registration with email verification
- [ ] Secure password storage (bcrypt)
- [ ] Login with email/password
- [ ] Password reset flow
- [ ] Account lockout after failures

**Story Points:** 8  
**Priority:** P0

---

### US-10.02: OAuth/OIDC Login
**As a** user  
**I want to** sign in with Google/GitHub  
**So that** I don't need another password

**Acceptance Criteria:**
- [ ] Google OAuth2
- [ ] GitHub OAuth
- [ ] Link accounts
- [ ] Auto-create user on first login

**Story Points:** 8  
**Priority:** P0

---

### US-10.03: SAML SSO
**As a** enterprise admin  
**I want to** integrate with corporate SSO  
**So that** users use existing credentials

**Acceptance Criteria:**
- [ ] SAML 2.0 support
- [ ] Configure IDP metadata
- [ ] Just-in-time provisioning
- [ ] Attribute mapping

**Story Points:** 13  
**Priority:** P1

---

### US-10.04: MFA
**As a** user  
**I want to** enable two-factor authentication  
**So that** my account is more secure

**Acceptance Criteria:**
- [ ] TOTP (Google Authenticator)
- [ ] Recovery codes
- [ ] Enforce MFA per organization
- [ ] Remember device option

**Story Points:** 8  
**Priority:** P1

---

### US-10.05: Built-in Roles
**As a** admin  
**I want to** assign built-in roles  
**So that** permissions are simple

**Acceptance Criteria:**
- [ ] Viewer (read-only)
- [ ] Editor (modify workflows)
- [ ] Admin (full access)
- [ ] Owner (billing, settings)

**Story Points:** 5  
**Priority:** P0

---

### US-10.06: Custom Roles
**As a** admin  
**I want to** create custom roles  
**So that** permissions fit our needs

**Acceptance Criteria:**
- [ ] Define role with permissions
- [ ] Assign to users/groups
- [ ] Scope to project/team/org
- [ ] Role hierarchy

**Story Points:** 8  
**Priority:** P1

---

### US-10.07: Resource-Level Permissions
**As a** admin  
**I want to** control access per resource  
**So that** sensitive workflows are protected

**Acceptance Criteria:**
- [ ] Per-workflow permissions
- [ ] Per-connection permissions
- [ ] Per-secret permissions
- [ ] Inherit from parent scope

**Story Points:** 8  
**Priority:** P1

---

### US-10.08: API Tokens
**As a** developer  
**I want to** create API tokens  
**So that** I can use the API securely

**Acceptance Criteria:**
- [ ] Generate token with name
- [ ] Expiration date
- [ ] Scope to specific permissions
- [ ] Revoke token
- [ ] Token usage logging

**Story Points:** 5  
**Priority:** P0

---

### US-10.09: Service Accounts
**As a** admin  
**I want to** create service accounts  
**So that** automation has identity

**Acceptance Criteria:**
- [ ] Non-human accounts
- [ ] Token-based auth only
- [ ] Scoped permissions
- [ ] Audit as service account

**Story Points:** 5  
**Priority:** P1

---

### US-10.10: Secrets Management - Store
**As a** data engineer  
**I want to** store secrets securely  
**So that** credentials are protected

**Acceptance Criteria:**
- [ ] Store secrets in Vault
- [ ] Encryption at rest
- [ ] Access via API
- [ ] Never logged or displayed

**Story Points:** 8  
**Priority:** P0

---

### US-10.11: Secrets Management - Rotate
**As a** security admin  
**I want to** rotate secrets  
**So that** exposure is limited

**Acceptance Criteria:**
- [ ] Manual rotation
- [ ] Auto-rotation for supported types
- [ ] Zero-downtime rotation
- [ ] Rotation history

**Story Points:** 8  
**Priority:** P1

---

### US-10.12: Audit Logging
**As a** compliance officer  
**I want to** all actions logged  
**So that** I can audit activity

**Acceptance Criteria:**
- [ ] Log all write operations
- [ ] Log authentication events
- [ ] Log sensitive reads
- [ ] Immutable storage

**Story Points:** 8  
**Priority:** P0

---

### US-10.13: Audit Log Export
**As a** compliance officer  
**I want to** export audit logs  
**So that** I can use SIEM tools

**Acceptance Criteria:**
- [ ] Export to S3/GCS
- [ ] Real-time stream option
- [ ] Structured format (JSON)
- [ ] Retention policy

**Story Points:** 5  
**Priority:** P1

---

### US-10.14: API Rate Limiting
**As a** platform engineer  
**I want to** rate limit API calls  
**So that** abuse is prevented

**Acceptance Criteria:**
- [ ] Per-token rate limits
- [ ] Configurable limits
- [ ] 429 response with retry-after
- [ ] Monitoring and alerting

**Story Points:** 5  
**Priority:** P0

---

### US-10.15: Session Management
**As a** user  
**I want to** manage my sessions  
**So that** I control access

**Acceptance Criteria:**
- [ ] View active sessions
- [ ] Revoke sessions
- [ ] Session timeout
- [ ] Remember me option

**Story Points:** 5  
**Priority:** P1

---

### US-10.16: IP Allowlisting
**As a** security admin  
**I want to** restrict by IP  
**So that** access is network-limited

**Acceptance Criteria:**
- [ ] Configure allowed IPs/CIDRs
- [ ] Scope to org or API
- [ ] Block with clear error
- [ ] Bypass for admins

**Story Points:** 5  
**Priority:** P2

---

## Technical Tasks

### T-10.01: Auth Service Design
Design authentication service.

**Estimate:** 8 points

### T-10.02: JWT Implementation
Implement JWT token flow.

**Estimate:** 5 points

### T-10.03: SAML Integration
Integrate SAML library.

**Estimate:** 8 points

### T-10.04: Vault Integration
Integrate HashiCorp Vault.

**Estimate:** 8 points

### T-10.05: RBAC Engine
Build permission evaluation engine.

**Estimate:** 13 points

### T-10.06: Audit Log Service
Build audit logging infrastructure.

**Estimate:** 8 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Auth latency | < 200ms |
| MFA adoption | > 50% of users |
| Audit log completeness | 100% |
| Security incidents | 0 |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
