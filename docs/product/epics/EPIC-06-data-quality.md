# EPIC-06: Data Quality

## Overview

Validate and trust your data with built-in quality checks, framework integrations, and quality scoring. Data quality is a first-class citizen, not an afterthought.

**Epic Owner:** Platform Team  
**Priority:** P1  
**Estimated Effort:** 84 story points  
**Related ADRs:** ADR-019

---

## Goals

1. Built-in quality checks (null, unique, range, etc.)
2. Integrate Great Expectations and dbt tests
3. PII detection and handling
4. Data contracts enforcement
5. Quality scoring and trends

---

## User Stories

### US-06.01: Define Quality Checks
**As a** data engineer  
**I want to** define quality checks on my tables  
**So that** I catch data issues early

**Acceptance Criteria:**
- [ ] Not null, unique, accepted values
- [ ] Range checks (min, max)
- [ ] Regex pattern matching
- [ ] Row count thresholds
- [ ] Custom SQL assertions

**Story Points:** 8  
**Priority:** P1

---

### US-06.02: Run Checks in Workflow
**As a** data engineer  
**I want to** run quality checks as workflow stages  
**So that** bad data doesn't propagate

**Acceptance Criteria:**
- [ ] Quality check stage type
- [ ] Fail workflow on check failure
- [ ] Warn but continue option
- [ ] Check results in run output

**Story Points:** 5  
**Priority:** P1

---

### US-06.03: Great Expectations Integration
**As a** data engineer  
**I want to** run Great Expectations suites  
**So that** I use my existing checks

**Acceptance Criteria:**
- [ ] Import expectation suites
- [ ] Run from workflow
- [ ] Results in Pravah UI
- [ ] Sync validation results

**Story Points:** 8  
**Priority:** P1

---

### US-06.04: dbt Tests Integration
**As a** data engineer  
**I want to** see dbt test results  
**So that** quality is unified

**Acceptance Criteria:**
- [ ] Parse dbt test results
- [ ] Display in quality dashboard
- [ ] Link to dbt documentation
- [ ] Alert on test failures

**Story Points:** 5  
**Priority:** P1

---

### US-06.05: Automatic PII Detection
**As a** compliance officer  
**I want to** detect PII automatically  
**So that** sensitive data is identified

**Acceptance Criteria:**
- [ ] Pattern detection (SSN, email, phone)
- [ ] ML-based classification
- [ ] Confidence scores
- [ ] Manual override/confirmation

**Story Points:** 13  
**Priority:** P1

---

### US-06.06: PII Tagging
**As a** data engineer  
**I want to** tag columns as PII  
**So that** they're handled appropriately

**Acceptance Criteria:**
- [ ] Manual PII tags
- [ ] PII categories (direct, quasi, sensitive)
- [ ] Propagate through lineage
- [ ] Filter by PII in catalog

**Story Points:** 5  
**Priority:** P1

---

### US-06.07: Data Masking
**As a** compliance officer  
**I want to** mask PII in outputs  
**So that** sensitive data is protected

**Acceptance Criteria:**
- [ ] Masking functions (hash, redact, tokenize)
- [ ] Apply per column
- [ ] Role-based unmasking
- [ ] Audit mask access

**Story Points:** 8  
**Priority:** P2

---

### US-06.08: Quality Score
**As a** data analyst  
**I want to** see a quality score for datasets  
**So that** I know how trustworthy data is

**Acceptance Criteria:**
- [ ] Composite score (0-100)
- [ ] Breakdown by dimension
- [ ] Trend over time
- [ ] Compare across datasets

**Story Points:** 8  
**Priority:** P1

---

### US-06.09: Quality Dashboard
**As a** data engineer  
**I want to** see quality across all datasets  
**So that** I find issues proactively

**Acceptance Criteria:**
- [ ] Overview of check results
- [ ] Failing checks highlighted
- [ ] Quality trends
- [ ] Filter by owner, team, severity

**Story Points:** 8  
**Priority:** P1

---

### US-06.10: Data Contracts - Define
**As a** data engineer  
**I want to** define data contracts  
**So that** expectations are explicit

**Acceptance Criteria:**
- [ ] Schema contract (column types)
- [ ] Quality contract (checks must pass)
- [ ] Freshness contract (SLA)
- [ ] YAML definition format

**Story Points:** 8  
**Priority:** P1

---

### US-06.11: Data Contracts - Enforce
**As a** data engineer  
**I want to** enforce contracts automatically  
**So that** breaking changes are caught

**Acceptance Criteria:**
- [ ] Validate on schema change
- [ ] Block breaking changes
- [ ] Notify contract consumers
- [ ] Exemption workflow

**Story Points:** 8  
**Priority:** P1

---

### US-06.12: Anomaly Detection - Statistical
**As a** data engineer  
**I want to** detect statistical anomalies  
**So that** unusual data is flagged

**Acceptance Criteria:**
- [ ] Volume anomalies (row count)
- [ ] Distribution anomalies
- [ ] Null rate changes
- [ ] Configurable sensitivity

**Story Points:** 8  
**Priority:** P1

---

### US-06.13: Freshness Tracking
**As a** data analyst  
**I want to** see data freshness  
**So that** I know if data is stale

**Acceptance Criteria:**
- [ ] Last updated timestamp
- [ ] Freshness SLA definition
- [ ] Alert on staleness
- [ ] Freshness in catalog view

**Story Points:** 5  
**Priority:** P1

---

### US-06.14: Quality Alerts
**As a** data engineer  
**I want to** be alerted on quality issues  
**So that** I respond quickly

**Acceptance Criteria:**
- [ ] Alert on check failure
- [ ] Alert on score drop
- [ ] Alert on freshness breach
- [ ] Integrate with EPIC-04 alerting

**Story Points:** 3  
**Priority:** P1

---

## Technical Tasks

### T-06.01: Quality Check Engine
Build engine to execute quality checks.

**Estimate:** 13 points

### T-06.02: PII Detection Model
Implement PII detection using ML/patterns.

**Estimate:** 13 points

### T-06.03: Great Expectations Runner
Integrate Great Expectations execution.

**Estimate:** 8 points

### T-06.04: Quality Score Calculator
Build quality scoring algorithm.

**Estimate:** 5 points

### T-06.05: Contract Validation Service
Build contract definition and enforcement.

**Estimate:** 8 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Quality issues detected pre-prod | > 80% |
| PII detection accuracy | > 90% |
| Contract adoption | > 50% of core tables |
| Mean time to quality fix | < 4 hours |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
