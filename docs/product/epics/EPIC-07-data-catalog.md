# EPIC-07: Data Catalog

## Overview

Discover, understand, and trust your data assets. The catalog provides a searchable inventory of all datasets with metadata, ownership, and usage information.

**Epic Owner:** Platform Team  
**Priority:** P1  
**Estimated Effort:** 68 story points  
**Related ADRs:** ADR-017

---

## Goals

1. Centralized inventory of all data assets
2. Rich metadata (schema, stats, descriptions)
3. Search and discovery
4. Ownership and governance
5. Business glossary integration

---

## User Stories

### US-07.01: Browse Data Assets
**As a** data analyst  
**I want to** browse available datasets  
**So that** I can find data I need

**Acceptance Criteria:**
- [ ] List view with filtering
- [ ] Filter by source, type, owner
- [ ] Sort by name, popularity, freshness
- [ ] Pagination

**Story Points:** 5  
**Priority:** P1

---

### US-07.02: Search Data Assets
**As a** data analyst  
**I want to** search for datasets  
**So that** I find specific tables quickly

**Acceptance Criteria:**
- [ ] Full-text search
- [ ] Search in names, descriptions, columns
- [ ] Relevance ranking
- [ ] Semantic search (natural language)

**Story Points:** 8  
**Priority:** P1

---

### US-07.03: View Dataset Details
**As a** data analyst  
**I want to** see detailed dataset information  
**So that** I understand the data

**Acceptance Criteria:**
- [ ] Schema with column types
- [ ] Statistics (row count, null %, distinct)
- [ ] Sample data preview
- [ ] Lineage summary
- [ ] Quality score

**Story Points:** 8  
**Priority:** P1

---

### US-07.04: Edit Dataset Metadata
**As a** data engineer  
**I want to** add descriptions and tags  
**So that** others understand my datasets

**Acceptance Criteria:**
- [ ] Edit table description (markdown)
- [ ] Edit column descriptions
- [ ] Add tags
- [ ] Version history of edits

**Story Points:** 5  
**Priority:** P1

---

### US-07.05: Dataset Ownership
**As a** data engineer  
**I want to** assign owners to datasets  
**So that** people know who to ask

**Acceptance Criteria:**
- [ ] Assign owner (user or team)
- [ ] Show owner in catalog
- [ ] Filter by owner
- [ ] Notify owner on issues

**Story Points:** 3  
**Priority:** P1

---

### US-07.06: Schema History
**As a** data engineer  
**I want to** see schema change history  
**So that** I track evolution

**Acceptance Criteria:**
- [ ] List of schema versions
- [ ] Diff between versions
- [ ] When changes occurred
- [ ] Who made changes

**Story Points:** 5  
**Priority:** P1

---

### US-07.07: Usage Statistics
**As a** data engineer  
**I want to** see how datasets are used  
**So that** I know what's important

**Acceptance Criteria:**
- [ ] Query count over time
- [ ] Unique users
- [ ] Popular columns
- [ ] Workflows using this dataset

**Story Points:** 8  
**Priority:** P2

---

### US-07.08: Business Glossary
**As a** data analyst  
**I want to** define business terms  
**So that** we have common language

**Acceptance Criteria:**
- [ ] Create/edit terms
- [ ] Link terms to columns
- [ ] Search by term
- [ ] Term hierarchy

**Story Points:** 8  
**Priority:** P2

---

### US-07.09: Data Classification
**As a** compliance officer  
**I want to** classify data sensitivity  
**So that** we handle it appropriately

**Acceptance Criteria:**
- [ ] Classification levels (public, internal, confidential)
- [ ] Assign to datasets/columns
- [ ] Access based on classification
- [ ] Report by classification

**Story Points:** 5  
**Priority:** P2

---

### US-07.10: Auto-Discovery
**As a** data engineer  
**I want to** auto-discover datasets  
**So that** the catalog stays current

**Acceptance Criteria:**
- [ ] Crawl connected warehouses
- [ ] Detect new tables
- [ ] Update schema changes
- [ ] Scheduled discovery

**Story Points:** 13  
**Priority:** P1

---

### US-07.11: Data Preview
**As a** data analyst  
**I want to** preview sample data  
**So that** I understand content

**Acceptance Criteria:**
- [ ] Show first N rows
- [ ] Respect access controls
- [ ] Mask PII
- [ ] Column profiling stats

**Story Points:** 8  
**Priority:** P1

---

### US-07.12: Catalog API
**As a** developer  
**I want to** query catalog programmatically  
**So that** I build tools

**Acceptance Criteria:**
- [ ] REST API for assets
- [ ] GraphQL support
- [ ] Search API
- [ ] Metadata update API

**Story Points:** 5  
**Priority:** P1

---

## Technical Tasks

### T-07.01: Catalog Storage Schema
Design catalog database schema.

**Estimate:** 5 points

### T-07.02: Metadata Crawler
Build warehouse metadata crawler.

**Estimate:** 13 points

### T-07.03: Search Index
Implement search using Elasticsearch.

**Estimate:** 8 points

### T-07.04: Catalog UI
Build catalog browsing interface.

**Estimate:** 13 points

### T-07.05: Glossary Service
Build business glossary backend.

**Estimate:** 5 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Catalog coverage | 100% of tables |
| Documented datasets | > 60% |
| Search success rate | > 80% find what they need |
| Time to find dataset | < 30 seconds |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
