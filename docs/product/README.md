# Pravah Product Documentation

This directory contains the complete product documentation for Pravah — the unified data platform.

> **Implementation tracking:** User stories describe requirements; acceptance checkboxes reflect product intent. For what is **actually shipped in this repository**, see **[Implementation Status](../IMPLEMENTATION_STATUS.md)** (updated with code changes).

---

## Document Structure

```
docs/product/
├── README.md                      # This file
├── PRODUCT-VISION.md              # Mission, value props, differentiators
├── EPICS-OVERVIEW.md              # High-level epic map and summary
├── epics/                         # Detailed epic documents
│   ├── EPIC-01-workflow-definition.md
│   ├── EPIC-02-execution-engine.md
│   ├── EPIC-03-scheduling-triggers.md
│   ├── EPIC-04-monitoring-alerting.md
│   ├── EPIC-05-data-lineage.md
│   ├── EPIC-06-data-quality.md
│   ├── EPIC-07-data-catalog.md
│   ├── EPIC-08-ai-features.md
│   ├── EPIC-09-platform-infra.md
│   ├── EPIC-10-security-access.md
│   ├── EPIC-11-developer-experience.md
│   └── EPIC-12-ui-ux.md
└── releases/
    └── RELEASE-PLAN.md            # Release milestones and timelines
```

---

## Quick Links

### Strategic Documents
- [Product Vision](PRODUCT-VISION.md) — Why Pravah exists and what it delivers
- [Release Plan](releases/RELEASE-PLAN.md) — Milestones from Alpha to v2.0

### Epic Documents
| Epic | Description | Priority | Stories |
|------|-------------|----------|---------|
| [EPIC-01](epics/EPIC-01-workflow-definition.md) | Workflow Definition | P0 | 18 |
| [EPIC-02](epics/EPIC-02-execution-engine.md) | Execution Engine | P0 | 22 |
| [EPIC-03](epics/EPIC-03-scheduling-triggers.md) | Scheduling & Triggers | P0 | 15 |
| [EPIC-04](epics/EPIC-04-monitoring-alerting.md) | Monitoring & Alerting | P0 | 20 |
| [EPIC-05](epics/EPIC-05-data-lineage.md) | Data Lineage | P1 | 16 |
| [EPIC-06](epics/EPIC-06-data-quality.md) | Data Quality | P1 | 14 |
| [EPIC-07](epics/EPIC-07-data-catalog.md) | Data Catalog | P1 | 12 |
| [EPIC-08](epics/EPIC-08-ai-features.md) | AI Features | P2 | 10 |
| [EPIC-09](epics/EPIC-09-platform-infra.md) | Platform & Infrastructure | P0 | 18 |
| [EPIC-10](epics/EPIC-10-security-access.md) | Security & Access Control | P0 | 16 |
| [EPIC-11](epics/EPIC-11-developer-experience.md) | Developer Experience | P1 | 14 |
| [EPIC-12](epics/EPIC-12-ui-ux.md) | UI & UX | P0 | 25 |

**Total: 180 User Stories across 12 Epics**

---

## Document Conventions

### User Story Format
```
### US-XX.YY: Story Title
**As a** [persona]
**I want to** [action]
**So that** [benefit]

**Acceptance Criteria:**
- [ ] Criterion 1
- [ ] Criterion 2

**Story Points:** N
**Priority:** P0/P1/P2/P3
```

### Priority Levels
| Priority | Meaning | Target Release |
|----------|---------|----------------|
| P0 | Must have for MVP | v0.1 - v0.5 |
| P1 | Important for complete product | v0.5 - v1.0 |
| P2 | Differentiator, can defer | v1.0 - v1.5 |
| P3 | Nice to have | v2.0+ |

### Story Points (Fibonacci)
| Points | Complexity | Duration |
|--------|------------|----------|
| 1 | Trivial | Hours |
| 2 | Small | 1-2 days |
| 3 | Small-Medium | 2-3 days |
| 5 | Medium | 3-5 days |
| 8 | Large | 1-2 weeks |
| 13 | Very Large | 2-3 weeks |
| 21 | Epic-level | Split required |

---

## How to Use These Documents

### For Product Managers
1. Start with [Product Vision](PRODUCT-VISION.md) for strategic context
2. Review [Release Plan](releases/RELEASE-PLAN.md) for milestones
3. Use [Epics Overview](EPICS-OVERVIEW.md) for planning

### For Developers
1. Reference epic documents for detailed requirements
2. Check acceptance criteria before implementation
3. Link PRs to user story IDs (e.g., "Implements US-02.03")

### For Designers
1. Focus on [EPIC-12: UI & UX](epics/EPIC-12-ui-ux.md)
2. Review acceptance criteria for design specs
3. Cross-reference related epics for context

---

## Updating These Documents

### Adding Features
1. Create user story in appropriate epic
2. Follow the story format above
3. Update EPICS-OVERVIEW.md totals
4. Update RELEASE-PLAN.md if needed

### Completing Stories
1. Check off acceptance criteria
2. Update story status in tracking system
3. No changes needed in these docs (source of truth is tracker)

### Changing Scope
1. Document in epic's "Open Questions" section
2. Update affected stories
3. Note in changelog at bottom of epic

---

## Related Documentation

- [Implementation Status](../IMPLEMENTATION_STATUS.md) — Built vs planned (source of truth for repo)
- [Architecture Decision Records](../adr/) — Technical decisions
- [High-Level Architecture](../architecture/) — Target system design
- [API Contracts](../design/) — Service interfaces
- [Theory Curriculum](../theory/) — Learning materials

---

## Document History

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Product Team | Initial product documentation |
