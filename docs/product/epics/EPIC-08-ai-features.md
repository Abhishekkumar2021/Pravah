# EPIC-08: AI Features

## Overview

Intelligent assistance that helps users diagnose issues, understand data, and automate routine tasks. AI is a differentiator but can be deferred post-MVP.

**Epic Owner:** Platform Team  
**Priority:** P2 (Post-MVP)  
**Estimated Effort:** 76 story points  
**Related ADRs:** ADR-020

---

## Goals

1. AI-powered root cause analysis for failures
2. Natural language search and queries
3. Auto-generated documentation and runbooks
4. Suggested fixes with one-click apply
5. Privacy-respecting options (self-hosted LLM)

---

## User Stories

### US-08.01: AI Diagnosis - Basic
**As a** data engineer  
**I want to** AI to explain why my workflow failed  
**So that** I understand issues faster

**Acceptance Criteria:**
- [ ] Analyze logs and error messages
- [ ] Explain in plain language
- [ ] Suggest likely causes
- [ ] Link to relevant documentation

**Story Points:** 8  
**Priority:** P2

---

### US-08.02: AI Diagnosis - Context-Aware
**As a** data engineer  
**I want to** AI to consider lineage and history  
**So that** root cause is accurate

**Acceptance Criteria:**
- [ ] Check upstream data quality
- [ ] Check recent changes
- [ ] Compare to successful runs
- [ ] Identify similar past failures

**Story Points:** 13  
**Priority:** P2

---

### US-08.03: AI Suggested Fixes
**As a** data engineer  
**I want to** AI to suggest specific fixes  
**So that** I resolve issues quickly

**Acceptance Criteria:**
- [ ] Concrete fix suggestions
- [ ] One-click apply (with approval)
- [ ] Explain what fix does
- [ ] Track fix effectiveness

**Story Points:** 13  
**Priority:** P2

---

### US-08.04: Natural Language Search
**As a** data analyst  
**I want to** search in natural language  
**So that** I don't need to know syntax

**Acceptance Criteria:**
- [ ] "Show failed pipelines yesterday"
- [ ] "Find tables with customer data"
- [ ] "What workflows use orders table"
- [ ] Interpret and translate to filters

**Story Points:** 8  
**Priority:** P2

---

### US-08.05: Natural Language to Query
**As a** data analyst  
**I want to** describe queries in English  
**So that** I explore data without SQL

**Acceptance Criteria:**
- [ ] Generate SQL from description
- [ ] Show generated query
- [ ] Allow editing
- [ ] Execute and show results

**Story Points:** 8  
**Priority:** P2

---

### US-08.06: Auto-Generated Documentation
**As a** data engineer  
**I want to** AI to generate documentation  
**So that** my datasets are documented

**Acceptance Criteria:**
- [ ] Generate table descriptions
- [ ] Generate column descriptions
- [ ] Based on names, data, usage
- [ ] Human review and edit

**Story Points:** 8  
**Priority:** P2

---

### US-08.07: Auto-Generated Runbooks
**As a** data engineer  
**I want to** AI to create runbooks from fixes  
**So that** knowledge is captured

**Acceptance Criteria:**
- [ ] Generate from successful resolutions
- [ ] Step-by-step instructions
- [ ] Link to relevant resources
- [ ] Improve over time

**Story Points:** 8  
**Priority:** P2

---

### US-08.08: AI Chat Assistant
**As a** data engineer  
**I want to** chat with an AI assistant  
**So that** I get help in context

**Acceptance Criteria:**
- [ ] Contextual help in UI
- [ ] Answer questions about Pravah
- [ ] Guide through tasks
- [ ] Maintain conversation history

**Story Points:** 8  
**Priority:** P2

---

### US-08.09: Self-Hosted LLM Option
**As a** enterprise admin  
**I want to** use a self-hosted LLM  
**So that** data doesn't leave my network

**Acceptance Criteria:**
- [ ] Configure local LLM endpoint
- [ ] Support Ollama, vLLM
- [ ] No data sent to cloud
- [ ] Feature parity (may be slower)

**Story Points:** 8  
**Priority:** P2

---

### US-08.10: AI Usage Analytics
**As a** platform admin  
**I want to** see AI feature usage  
**So that** I understand adoption and cost

**Acceptance Criteria:**
- [ ] Queries per day
- [ ] Token usage
- [ ] Success rate
- [ ] Cost tracking

**Story Points:** 3  
**Priority:** P2

---

## Technical Tasks

### T-08.01: LLM Integration Framework
Build abstraction for multiple LLM providers.

**Estimate:** 8 points

### T-08.02: Diagnosis Agent (ReAct)
Implement ReAct agent for diagnosis.

**Estimate:** 13 points

### T-08.03: Tool Functions
Build tool functions for AI to call.

**Estimate:** 8 points

### T-08.04: Natural Language Parser
Build NL to structured query parser.

**Estimate:** 8 points

### T-08.05: Chat UI Component
Build chat interface in UI.

**Estimate:** 5 points

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Diagnosis accuracy | > 70% correct root cause |
| Time saved per incident | > 30 minutes |
| NL search adoption | > 20% of searches |
| User satisfaction | > 4/5 rating |

---

## Privacy & Cost

### Privacy Options
| Mode | Data Sent | Use Case |
|------|-----------|----------|
| Cloud | Logs, schema names | Default |
| Redacted | Sanitized data | Sensitive environments |
| Self-hosted | None | Maximum privacy |

### Cost Model
- Basic AI features included
- Advanced features require BYOK (API key)
- Token usage metered for enterprise

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
