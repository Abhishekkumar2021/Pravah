# Pravah Product Vision

## Mission Statement

**Pravah is the unified data platform that makes data teams powerful** — bringing orchestration, lineage, and quality into one place where you always know what's happening and why.

---

## The Problem

Modern data teams are drowning in tool fragmentation:

| Pain Point | Current Reality |
|------------|-----------------|
| **Orchestration** | Airflow/Dagster for scheduling, separate monitoring |
| **Lineage** | Bolted-on solutions, often stale or incomplete |
| **Quality** | Yet another tool (Great Expectations, dbt tests) |
| **Debugging** | Grep through logs, guess at root causes |
| **Multi-tenancy** | One deployment per team, ops nightmare |

The result: Data engineers spend more time operating tools than building value.

---

## The Solution

Pravah is a **single platform** where:

1. **You define workflows** in code or UI — your choice
2. **You see everything** — runs, lineage, quality, in one view
3. **You always know why** — observability-first design
4. **Teams share infrastructure** — true multi-tenancy
5. **The platform helps you** — AI diagnosis, not just dashboards

---

## Core Value Propositions

### 1. Unified Platform
No more stitching together 5 tools. Orchestration, lineage, quality, catalog — one login, one mental model, one API.

### 2. Observability-First
Every feature is designed around the question: "What happened and why?"
- Real-time run status
- Automatic error classification
- Full audit trail
- Time-travel debugging

### 3. Native Lineage
Lineage isn't an afterthought — it's extracted automatically, enriched manually, and visualized beautifully. Row-level. Temporal. Always current.

### 4. True Multi-Tenancy
One Pravah deployment serves your entire organization:
- Org → Teams → Projects → Environments → Workflows
- Isolated resources, shared infrastructure
- Granular RBAC with custom roles

### 5. AI-Powered Operations (Future)
The platform doesn't just show you errors — it diagnoses them:
- Automatic root cause analysis
- Suggested fixes
- Natural language queries
- AI-generated runbooks

---

## Target Users

### Primary: Data Engineer
The person who builds and maintains data pipelines daily.
- Needs: Fast workflow creation, clear debugging, reliable scheduling
- Success metric: Time from code to production

### Secondary Users

| Persona | Needs |
|---------|-------|
| Analytics Engineer | dbt integration, transformation focus |
| Data Analyst | Lineage exploration, data freshness |
| Engineering Manager | Team performance, cost visibility |
| Platform Engineer | Deployment, scaling, security |
| Compliance Officer | Audit logs, access control, PII tracking |

---

## Key Differentiators

### vs. Apache Airflow
- **Native lineage** (not bolted on)
- **True multi-tenancy** (not one deployment per team)
- **Modern UI** (not a 2015 interface)
- **First-class quality** (not just task orchestration)

### vs. Dagster
- **Unified platform** (not just orchestration)
- **Full data catalog** (not asset-centric only)
- **Enterprise features** (SSO, audit, compliance)
- **AI diagnosis** (not just observability)

### vs. Prefect
- **On-prem option** (not cloud-only)
- **Native lineage** (not external integration)
- **Row-level tracking** (not pipeline-level only)
- **Full quality framework** (not basic assertions)

---

## Product Principles

### 1. Observability Over Features
We'd rather have 10 features with excellent observability than 50 features where you can't tell what's happening.

### 2. Code-First, UI-Complete
Power users define everything in code. Everyone else has a beautiful UI. Both are first-class citizens.

### 3. Gradual Complexity
New users see simplicity. Power users unlock depth. Never overwhelm, never limit.

### 4. Open Core
The core platform is open source (Apache 2.0). Enterprise features (SSO, advanced audit, HA) are commercial.

### 5. Convention Over Configuration
Sensible defaults everywhere. Configuration for those who need it.

---

## Success Metrics

### North Star Metric
**Workflows running successfully per month** — measures adoption and reliability

### Supporting Metrics

| Metric | Target |
|--------|--------|
| Time to first workflow | < 15 minutes |
| Mean time to detect failure | < 1 minute |
| Mean time to root cause | < 10 minutes |
| Platform uptime | 99.9% |
| User NPS | > 50 |

---

## Pricing Model

### Open Source (Free)
- Unlimited workflows
- Core orchestration
- Basic lineage
- Community support

### Team ($X/user/month)
- Everything in Free
- Advanced lineage (column-level)
- Data quality framework
- Email support

### Enterprise (Custom)
- Everything in Team
- SSO/SAML
- Audit logs
- Custom roles
- HA deployment
- Dedicated support
- SLA guarantees

---

## Competitive Landscape

```
                    Simple ─────────────────────── Complex
                        │                              │
              ┌─────────┴──────────────────────────────┴─────────┐
              │                                                   │
    Prefect   │     ┌─────────┐                                  │
              │     │ Pravah  │ ← Unified + Powerful             │
              │     └─────────┘                                  │
              │                          Dagster                 │
              │                                                   │
              │                                    Airflow        │
              │                                                   │
              └───────────────────────────────────────────────────┘
                        │                              │
                    Lightweight ────────────────── Enterprise
```

Pravah occupies the "unified + powerful" quadrant — more integrated than Dagster, more modern than Airflow, more flexible than Prefect.

---

## Long-Term Vision (3-5 Years)

1. **The default choice** for data orchestration in enterprises
2. **AI-native operations** where the platform suggests and applies fixes
3. **Full data operations platform** — orchestration, quality, catalog, governance
4. **Ecosystem hub** — marketplace of integrations, templates, and extensions
5. **Community-driven** — open source contributors, public roadmap, user-built connectors

---

## What Pravah is NOT

- ❌ A data warehouse (use Snowflake, BigQuery, etc.)
- ❌ A BI tool (use Looker, Metabase, etc.)
- ❌ A data transformation tool (we integrate with dbt, Spark)
- ❌ A simple task scheduler (we're a data platform)
- ❌ Cloud-only (self-hosted is first-class)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Product Team | Initial vision document |
