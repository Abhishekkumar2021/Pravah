# Pravah Epics Overview

This document provides a high-level view of all epics (major feature areas) in Pravah. Each epic has its own detailed document with user stories and tasks.

---

## Epic Map

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRAVAH PLATFORM                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   EPIC-01    │  │   EPIC-02    │  │   EPIC-03    │  │   EPIC-04    │    │
│  │   Workflow   │  │  Execution   │  │  Scheduling  │  │  Monitoring  │    │
│  │  Definition  │  │   Engine     │  │  & Triggers  │  │  & Alerting  │    │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   EPIC-05    │  │   EPIC-06    │  │   EPIC-07    │  │   EPIC-08    │    │
│  │    Data      │  │    Data      │  │    Data      │  │     AI       │    │
│  │   Lineage    │  │   Quality    │  │   Catalog    │  │  Features    │    │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   EPIC-09    │  │   EPIC-10    │  │   EPIC-11    │  │   EPIC-12    │    │
│  │   Platform   │  │   Security   │  │  Developer   │  │     UI       │    │
│  │    & Infra   │  │  & Access    │  │ Experience   │  │   & UX       │    │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Epic Summary Table

| Epic | Name | Description | Priority | Stories | ADRs |
|------|------|-------------|----------|---------|------|
| [EPIC-01](epics/EPIC-01-workflow-definition.md) | Workflow Definition | Create, edit, version workflows | P0 | 18 | ADR-001, ADR-002 |
| [EPIC-02](epics/EPIC-02-execution-engine.md) | Execution Engine | Run workflows reliably at scale | P0 | 22 | ADR-004, ADR-010, ADR-011, ADR-015 |
| [EPIC-03](epics/EPIC-03-scheduling-triggers.md) | Scheduling & Triggers | When and how workflows run | P0 | 15 | ADR-002, ADR-006 |
| [EPIC-04](epics/EPIC-04-monitoring-alerting.md) | Monitoring & Alerting | Observe and respond to issues | P0 | 20 | ADR-014, ADR-016 |
| [EPIC-05](epics/EPIC-05-data-lineage.md) | Data Lineage | Track data flow end-to-end | P1 | 16 | ADR-017, ADR-018 |
| [EPIC-06](epics/EPIC-06-data-quality.md) | Data Quality | Validate and trust data | P1 | 14 | ADR-019 |
| [EPIC-07](epics/EPIC-07-data-catalog.md) | Data Catalog | Discover and understand data | P1 | 12 | ADR-017 |
| [EPIC-08](epics/EPIC-08-ai-features.md) | AI Features | Intelligent assistance | P2 | 10 | ADR-020 |
| [EPIC-09](epics/EPIC-09-platform-infra.md) | Platform & Infrastructure | Multi-tenancy, scaling, ops | P0 | 18 | ADR-003, ADR-013, ADR-022 |
| [EPIC-10](epics/EPIC-10-security-access.md) | Security & Access Control | Auth, RBAC, compliance | P0 | 16 | ADR-007, ADR-008, ADR-012 |
| [EPIC-11](epics/EPIC-11-developer-experience.md) | Developer Experience | CLI, SDK, IDE, CI/CD | P1 | 14 | ADR-005, ADR-033 |
| [EPIC-12](epics/EPIC-12-ui-ux.md) | UI & UX | Web application interface | P0 | 25 | ADR-033 |

**Total User Stories: 180**

---

## Priority Definitions

| Priority | Meaning | Timeline |
|----------|---------|----------|
| **P0** | Must have for MVP | Phase 1-2 |
| **P1** | Important for complete product | Phase 2-3 |
| **P2** | Differentiator, can defer | Phase 3-4 |
| **P3** | Nice to have | Future |

---

## Epic Dependencies

```
                    ┌─────────────────┐
                    │   EPIC-09       │
                    │ Platform/Infra  │
                    └────────┬────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
    ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
    │  EPIC-10    │   │  EPIC-01    │   │  EPIC-12    │
    │  Security   │   │  Workflow   │   │   UI/UX     │
    └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
           │                 │                 │
           │                 ▼                 │
           │          ┌─────────────┐          │
           └─────────▶│  EPIC-02    │◀─────────┘
                      │  Execution  │
                      └──────┬──────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
    ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
    │  EPIC-03    │   │  EPIC-04    │   │  EPIC-05    │
    │ Scheduling  │   │ Monitoring  │   │  Lineage    │
    └─────────────┘   └──────┬──────┘   └──────┬──────┘
                             │                 │
                             ▼                 ▼
                      ┌─────────────┐   ┌─────────────┐
                      │  EPIC-08    │   │  EPIC-06    │
                      │     AI      │   │  Quality    │
                      └─────────────┘   └──────┬──────┘
                                               │
                                               ▼
                                        ┌─────────────┐
                                        │  EPIC-07    │
                                        │  Catalog    │
                                        └─────────────┘
```

---

## Cross-Epic Features

Some features span multiple epics:

| Feature | Epics Involved |
|---------|----------------|
| Git Sync | EPIC-01, EPIC-11, EPIC-10 |
| Notifications | EPIC-04, EPIC-12, EPIC-09 |
| Search | EPIC-07, EPIC-12, EPIC-08 |
| API | EPIC-11, all others |
| RBAC | EPIC-10, all others |
| Audit Logs | EPIC-10, EPIC-04, EPIC-09 |

---

## Release Mapping

| Release | Epics | Focus |
|---------|-------|-------|
| **v0.1 (Alpha)** | EPIC-01 (partial), EPIC-02 (core), EPIC-09 (basic), EPIC-12 (basic) | Core execution works |
| **v0.5 (Beta)** | Above + EPIC-03, EPIC-04, EPIC-10, EPIC-11 (CLI) | Production-ready for early adopters |
| **v1.0 (GA)** | Above + EPIC-05, EPIC-06, EPIC-12 (complete) | Full platform launch |
| **v1.5** | Above + EPIC-07, EPIC-08 (basic) | Data catalog + basic AI |
| **v2.0** | All epics complete | Enterprise-ready |

---

## How to Read Epic Documents

Each epic document (`epics/EPIC-XX-name.md`) contains:

1. **Overview** — What the epic delivers
2. **User Stories** — Detailed requirements with acceptance criteria
3. **Technical Tasks** — Implementation work linked to ADRs
4. **Dependencies** — What must be built first
5. **Risks** — What could go wrong
6. **Success Metrics** — How we know it's working

---

## Story Point Estimates

| Size | Points | Typical Duration | Example |
|------|--------|------------------|---------|
| XS | 1 | < 1 day | Add a config field |
| S | 2 | 1-2 days | New API endpoint |
| M | 5 | 3-5 days | New UI screen |
| L | 8 | 1-2 weeks | New service component |
| XL | 13 | 2-3 weeks | Major feature |
| XXL | 21+ | Split required | Too large |

---

## Document Navigation

- [Product Vision](PRODUCT-VISION.md)
- **Epics Overview** (this document)
- [Release Plan](releases/RELEASE-PLAN.md)
- Individual Epics:
  - [EPIC-01: Workflow Definition](epics/EPIC-01-workflow-definition.md)
  - [EPIC-02: Execution Engine](epics/EPIC-02-execution-engine.md)
  - [EPIC-03: Scheduling & Triggers](epics/EPIC-03-scheduling-triggers.md)
  - [EPIC-04: Monitoring & Alerting](epics/EPIC-04-monitoring-alerting.md)
  - [EPIC-05: Data Lineage](epics/EPIC-05-data-lineage.md)
  - [EPIC-06: Data Quality](epics/EPIC-06-data-quality.md)
  - [EPIC-07: Data Catalog](epics/EPIC-07-data-catalog.md)
  - [EPIC-08: AI Features](epics/EPIC-08-ai-features.md)
  - [EPIC-09: Platform & Infrastructure](epics/EPIC-09-platform-infra.md)
  - [EPIC-10: Security & Access Control](epics/EPIC-10-security-access.md)
  - [EPIC-11: Developer Experience](epics/EPIC-11-developer-experience.md)
  - [EPIC-12: UI & UX](epics/EPIC-12-ui-ux.md)
