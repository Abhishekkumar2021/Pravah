# Pravah Documentation

Navigation hub for all project documentation. **Keep [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) updated** whenever code ships or scope changes.

---

## Start here

| Document | Use when |
|----------|----------|
| [Implementation Status](IMPLEMENTATION_STATUS.md) | **What exists in the repo today** (services, UI, gaps) |
| [High-Level Architecture](architecture/high-level-architecture.md) | Target platform design (not all built yet) |
| [Product Vision](product/PRODUCT-VISION.md) | Why Pravah exists |
| [Release Plan](product/releases/RELEASE-PLAN.md) | Alpha → GA milestones |

---

## By area

| Directory | Contents |
|-----------|----------|
| [adr/](adr/README.md) | 33 Architecture Decision Records |
| [architecture/](architecture/high-level-architecture.md) | HLA, API contracts |
| [lld/](lld/README.md) | Low-level design (patterns, ERD, state machines, value resolution, WebSocket) |
| [product/](product/README.md) | Epics and 180 user stories |
| [theory/](theory/README.md) | 75-chapter engineering curriculum |
| [design/](design/) | System architecture notes, contracts |

---

## For contributors

1. Read [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) before assuming a feature exists.
2. Implement against [LLD](lld/) and [ADRs](adr/); update docs in the same PR.
3. Run `./scripts/pre-commit.sh` — see [CONTRIBUTING.md](../CONTRIBUTING.md).
