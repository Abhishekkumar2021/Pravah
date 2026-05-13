# Pravah Release Plan

## Release Philosophy

Pravah follows a milestone-based release approach:
- **Alpha** releases are for internal testing and early feedback
- **Beta** releases are for early adopters who accept instability
- **GA (General Availability)** releases are production-ready
- **Minor** releases add features without breaking changes
- **Patch** releases fix bugs and security issues

---

## Release Timeline Overview

```
        Alpha    Beta     GA        v1.5      v2.0
         │        │        │          │         │
─────────┼────────┼────────┼──────────┼─────────┼─────────▶
         │        │        │          │         │
     Core MVP   Prod-   Complete   Catalog   Enterprise
     Works     Ready    Platform    + AI     Complete
```

---

## v0.1.0-alpha — "First Light"

### Goal
Core execution works end-to-end. A workflow can be created, scheduled, and executed.

### Included Features

| Epic | Stories | Notes |
|------|---------|-------|
| EPIC-01 | US-01.01, US-01.03, US-01.04, US-01.05, US-01.12 | YAML workflow, basic stages, validation |
| EPIC-02 | US-02.01, US-02.02, US-02.03, US-02.04, US-02.06, US-02.07, US-02.14, US-02.17 | Manual run, logs, cancel, retry |
| EPIC-03 | US-03.01, US-03.05, US-03.08 | Cron schedule, pause, API trigger |
| EPIC-09 | US-09.04, US-09.05, US-09.06, US-09.07 | K8s deploy, docker-compose, Postgres, Kafka |
| EPIC-10 | US-10.01, US-10.05, US-10.08 | Local auth, basic roles, API tokens |
| EPIC-12 | US-12.01, US-12.02, US-12.03, US-12.04, US-12.05, US-12.07, US-12.08, US-12.09 | Basic UI shell |

### Exit Criteria
- [ ] Create workflow via YAML
- [ ] Run workflow manually
- [ ] View logs in UI
- [ ] Schedule on cron
- [ ] Login and basic RBAC working
- [ ] Deploy to local K8s

### Known Limitations
- Single tenant only
- No lineage
- No data quality
- Basic UI (functional, not polished)
- No SSO

---

## v0.5.0-beta — "Production Ready"

### Goal
Ready for production use by early adopters. Full scheduling, monitoring, alerts, and CLI.

### Included Features (cumulative)

| Epic | Stories | Notes |
|------|---------|-------|
| EPIC-01 | US-01.02, US-01.06, US-01.07, US-01.08, US-01.09, US-01.14, US-01.15, US-01.16 | Visual editor, Git sync, versioning |
| EPIC-02 | US-02.05, US-02.08, US-02.09, US-02.10, US-02.12, US-02.15, US-02.20, US-02.21 | Retry from stage, resources, checkpointing |
| EPIC-03 | US-03.02, US-03.04, US-03.06, US-03.07, US-03.13, US-03.14 | Schedule builder, Kafka/webhook triggers |
| EPIC-04 | US-04.01, US-04.02, US-04.04, US-04.05, US-04.07, US-04.08, US-04.12, US-04.19 | Monitoring, alerts, audit log |
| EPIC-09 | US-09.01, US-09.02, US-09.08, US-09.09, US-09.13, US-09.14, US-09.16 | Multi-tenancy, scaling, observability |
| EPIC-10 | US-10.02, US-10.06, US-10.10, US-10.12, US-10.14 | OAuth, custom roles, secrets, rate limiting |
| EPIC-11 | US-11.01, US-11.02, US-11.03, US-11.04, US-11.05, US-11.10, US-11.12 | CLI, GitHub Action, API docs |
| EPIC-12 | US-12.06, US-12.10, US-12.14, US-12.15, US-12.16, US-12.17, US-12.24, US-12.25 | Visual editor, real-time, settings |

### Exit Criteria
- [ ] Multi-tenant isolation working
- [ ] Git sync with GitHub
- [ ] Alerts via Slack/email
- [ ] CLI for all operations
- [ ] Horizontal scaling tested
- [ ] Visual workflow editor complete
- [ ] Documentation complete

### Known Limitations
- No lineage (coming in GA)
- No data quality (coming in GA)
- Basic catalog only
- No AI features

---

## v1.0.0 — "General Availability"

### Goal
Complete data platform. Lineage, quality, and full polish.

### Included Features (cumulative)

| Epic | Stories | Notes |
|------|---------|-------|
| EPIC-01 | All remaining | Templates, Airflow import |
| EPIC-02 | All remaining | Spark, dbt, artifacts |
| EPIC-03 | All remaining | Data sensors, cross-workflow deps |
| EPIC-04 | All remaining | Custom dashboards, anomaly detection |
| EPIC-05 | US-05.01–US-05.11, US-05.13, US-05.14 | Core lineage features |
| EPIC-06 | US-06.01–US-06.06, US-06.08–US-06.14 | Core quality features |
| EPIC-09 | All remaining | HA, DR, IaC |
| EPIC-10 | All remaining | SAML, MFA, IP allowlist |
| EPIC-11 | US-11.06, US-11.07, US-11.13, US-11.14 | Python SDK, GraphQL, webhooks |
| EPIC-12 | All remaining | Dark mode, accessibility, polish |

### Exit Criteria
- [ ] Lineage auto-extraction from SQL
- [ ] Quality checks in workflows
- [ ] Full UI polish complete
- [ ] Accessibility audit passed
- [ ] Performance benchmarks met
- [ ] Security audit passed
- [ ] Public documentation site
- [ ] Example projects published

### SLA Commitments (Enterprise)
- 99.9% uptime
- < 1 minute failure detection
- < 4 hour critical bug response

---

## v1.5.0 — "Intelligence"

### Goal
Data catalog and basic AI features.

### Included Features

| Epic | Stories | Notes |
|------|---------|-------|
| EPIC-05 | All remaining | Row-level, temporal lineage |
| EPIC-06 | All remaining | PII masking, contracts |
| EPIC-07 | All stories | Full data catalog |
| EPIC-08 | US-08.01, US-08.02, US-08.04, US-08.06, US-08.10 | Basic AI diagnosis, NL search |

### Exit Criteria
- [ ] Catalog auto-discovery working
- [ ] AI diagnosis reduces MTTR by 30%
- [ ] Natural language search > 70% accuracy

---

## v2.0.0 — "Enterprise"

### Goal
Enterprise-complete with full AI.

### Included Features

| Epic | Stories | Notes |
|------|---------|-------|
| EPIC-08 | All remaining | AI chat, self-hosted LLM |
| EPIC-11 | All remaining | VS Code extension, Python DSL |

### Exit Criteria
- [ ] Enterprise features complete
- [ ] Self-hosted LLM option
- [ ] IDE integration published
- [ ] Partner integrations (Snowflake, Databricks)

---

## Feature Flags

Features are gated by flags during development:

| Flag | Release | Description |
|------|---------|-------------|
| `lineage_enabled` | v1.0-beta | Enable lineage features |
| `quality_enabled` | v1.0-beta | Enable quality features |
| `ai_features` | v1.5 | Enable AI features |
| `graphql_api` | v0.5 | Enable GraphQL endpoint |
| `visual_editor_v2` | v1.0 | New visual editor |

---

## Versioning Policy

### Semantic Versioning
- **MAJOR**: Breaking API changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, security patches

### Database Migrations
- All migrations are forward-only
- Rollback scripts provided but tested
- Major version may require manual migration

### API Deprecation
- Deprecated APIs work for 2 minor versions
- Deprecation warnings in responses
- Migration guide provided

---

## Release Checklist

### Pre-Release
- [ ] All tests passing
- [ ] Security scan completed
- [ ] Performance benchmarks run
- [ ] Documentation updated
- [ ] Changelog prepared
- [ ] Migration guide ready

### Release
- [ ] Docker images published
- [ ] Helm chart updated
- [ ] CLI released
- [ ] Release notes published
- [ ] Documentation site updated

### Post-Release
- [ ] Monitoring dashboards reviewed
- [ ] Support team briefed
- [ ] Social media announcement
- [ ] Community post

---

## Support Matrix

| Version | Status | Support Until |
|---------|--------|---------------|
| v2.x | Current | Active |
| v1.x | Maintenance | 12 months after v2 GA |
| v0.x | EOL | No support |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial release plan |
