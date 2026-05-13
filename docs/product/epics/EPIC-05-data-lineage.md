# EPIC-05: Data Lineage

## Overview

Track the complete flow of data through the platform — from source to consumption. Lineage is not bolted on but native, enabling impact analysis, debugging, and compliance.

**Epic Owner:** Platform Team  
**Priority:** P1  
**Estimated Effort:** 96 story points  
**Related ADRs:** ADR-017, ADR-018

---

## Goals

1. Automatically extract lineage from SQL, dbt, Spark
2. Support manual lineage declaration for custom code
3. Visualize lineage interactively
4. Track row-level lineage for compliance
5. Enable temporal lineage (point-in-time queries)

---

## User Stories

### US-05.01: View Dataset Lineage Graph
**As a** data analyst  
**I want to** see where my data comes from  
**So that** I understand its origins

**Acceptance Criteria:**
- [ ] Interactive graph visualization
- [ ] Upstream and downstream views
- [ ] Zoom, pan, filter controls
- [ ] Node details on click
- [ ] Highlight specific paths

**Story Points:** 13  
**Priority:** P1

---

### US-05.02: Column-Level Lineage
**As a** data engineer  
**I want to** see column-to-column mappings  
**So that** I know exactly how data transforms

**Acceptance Criteria:**
- [ ] Expand table to show columns
- [ ] Draw connections between columns
- [ ] Show transformation logic
- [ ] Filter by specific column

**Story Points:** 13  
**Priority:** P1

---

### US-05.03: Auto-Extract from SQL
**As a** data engineer  
**I want to** lineage extracted automatically from SQL  
**So that** I don't have to declare it manually

**Acceptance Criteria:**
- [ ] Parse SELECT, INSERT, CREATE AS
- [ ] Extract source tables and columns
- [ ] Handle CTEs and subqueries
- [ ] Support Snowflake, BigQuery, Postgres dialects

**Story Points:** 13  
**Priority:** P1

---

### US-05.04: Auto-Extract from dbt
**As a** data engineer  
**I want to** lineage from dbt models  
**So that** my dbt transformations are tracked

**Acceptance Criteria:**
- [ ] Parse dbt manifest.json
- [ ] Extract model dependencies
- [ ] Map to physical tables
- [ ] Include dbt test relationships

**Story Points:** 8  
**Priority:** P1

---

### US-05.05: Manual Lineage Declaration
**As a** data engineer  
**I want to** declare lineage for custom code  
**So that** Python/Spark jobs are tracked

**Acceptance Criteria:**
- [ ] YAML syntax for lineage
- [ ] API to report lineage at runtime
- [ ] Python SDK helper functions
- [ ] Validate declared vs actual

**Story Points:** 5  
**Priority:** P1

---

### US-05.06: Impact Analysis
**As a** data engineer  
**I want to** see what's affected if I change a table  
**So that** I don't break downstream

**Acceptance Criteria:**
- [ ] "What depends on this?" query
- [ ] Show all downstream tables and workflows
- [ ] Estimate blast radius
- [ ] Alert downstream owners

**Story Points:** 8  
**Priority:** P1

---

### US-05.07: Root Cause Analysis
**As a** data engineer  
**I want to** trace issues back to source  
**So that** I find the origin of bad data

**Acceptance Criteria:**
- [ ] "Where did this come from?" query
- [ ] Show full upstream path
- [ ] Highlight recent changes
- [ ] Link to workflow runs

**Story Points:** 8  
**Priority:** P1

---

### US-05.08: Row-Level Lineage
**As a** compliance officer  
**I want to** track specific record transformations  
**So that** I can audit data handling

**Acceptance Criteria:**
- [ ] Capture record identifiers
- [ ] Track through transformations
- [ ] Query: "Where did row X go?"
- [ ] Storage-efficient (sampling optional)

**Story Points:** 13  
**Priority:** P2

---

### US-05.09: Temporal Lineage
**As a** data engineer  
**I want to** see lineage as of a specific time  
**So that** I understand historical state

**Acceptance Criteria:**
- [ ] "Lineage as of date" selector
- [ ] Show historical connections
- [ ] Diff between time periods
- [ ] Animation of changes over time

**Story Points:** 8  
**Priority:** P2

---

### US-05.10: Lineage Search
**As a** data analyst  
**I want to** search for datasets in lineage  
**So that** I can find what I need

**Acceptance Criteria:**
- [ ] Search by table name
- [ ] Search by column name
- [ ] Filter by source system
- [ ] Filter by workflow

**Story Points:** 5  
**Priority:** P1

---

### US-05.11: Lineage API
**As a** developer  
**I want to** query lineage programmatically  
**So that** I can build tools on top

**Acceptance Criteria:**
- [ ] GET /lineage/upstream/{dataset}
- [ ] GET /lineage/downstream/{dataset}
- [ ] GraphQL query support
- [ ] Pagination for large graphs

**Story Points:** 5  
**Priority:** P1

---

### US-05.12: External Lineage Integration
**As a** data engineer  
**I want to** import lineage from external tools  
**So that** I have a complete picture

**Acceptance Criteria:**
- [ ] Import from DataHub
- [ ] Import from OpenLineage
- [ ] Merge with internal lineage
- [ ] Mark source of lineage

**Story Points:** 8  
**Priority:** P2

---

### US-05.13: Lineage Freshness Indicators
**As a** data analyst  
**I want to** see when data was last updated  
**So that** I know if it's fresh

**Acceptance Criteria:**
- [ ] Last update timestamp on nodes
- [ ] Freshness SLA indicators
- [ ] Color coding (green/yellow/red)
- [ ] Propagate staleness downstream

**Story Points:** 5  
**Priority:** P1

---

### US-05.14: Lineage in Run Context
**As a** data engineer  
**I want to** see lineage for a specific run  
**So that** I know what that run touched

**Acceptance Criteria:**
- [ ] Lineage view in run detail
- [ ] Show only tables from this run
- [ ] Record counts per table
- [ ] Link to full lineage graph

**Story Points:** 5  
**Priority:** P1

---

### US-05.15: Lineage Notifications
**As a** data engineer  
**I want to** be notified of lineage changes  
**So that** I know when upstream changes

**Acceptance Criteria:**
- [ ] Subscribe to dataset changes
- [ ] Alert on new upstream
- [ ] Alert on schema changes
- [ ] Weekly lineage digest

**Story Points:** 5  
**Priority:** P2

---

### US-05.16: Lineage Export
**As a** data engineer  
**I want to** export lineage data  
**So that** I can use it externally

**Acceptance Criteria:**
- [ ] Export to OpenLineage format
- [ ] Export to CSV
- [ ] Export graph as image
- [ ] Scheduled export option

**Story Points:** 3  
**Priority:** P2

---

## Technical Tasks

### T-05.01: Lineage Graph Database
Choose and set up graph storage for lineage.

**Estimate:** 8 points

### T-05.02: SQL Parser Integration
Integrate SQL parser for lineage extraction.

**Estimate:** 13 points

### T-05.03: dbt Manifest Parser
Build dbt manifest parser.

**Estimate:** 5 points

### T-05.04: Lineage API Service
Build lineage query service.

**Estimate:** 8 points

### T-05.05: Lineage Visualization Component
Build React component for lineage graph.

**Estimate:** 13 points

### T-05.06: OpenLineage Adapter
Implement OpenLineage protocol support.

**Estimate:** 8 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Lineage accuracy | > 95% correct |
| Auto-extraction coverage | > 80% of SQL |
| Impact analysis time | < 5 seconds |
| User adoption | > 50% weekly active |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
