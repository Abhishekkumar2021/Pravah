# Pravah Engineering Guidelines

> This document provides guidance for AI agents working on the Pravah codebase. Human engineers should also follow these principles.

## Core Principle: Engineering Over Vibes

**Every line of code must be intentional and traceable to documentation.**

This is not a hackathon project. This is a production-grade system being built with discipline.

---

## Before Writing Any Code

### 1. Check Documentation First

| Need | Location |
|------|----------|
| **What is built in this repo** | `docs/IMPLEMENTATION_STATUS.md` |
| Architecture decisions | `docs/adr/` |
| Design patterns | `docs/lld/01-design-patterns.md` |
| Database schemas | `docs/lld/02-database-erd.md` |
| State machines | `docs/lld/03-state-machines.md` |
| Key flows | `docs/lld/04-sequence-diagrams.md` |
| Value resolution (vars, secrets, stage outputs) | `docs/lld/07-value-resolution.md` |
| Domain models | `docs/lld/05-class-diagrams.md` |
| User stories | `docs/product/epics/` |
| Theory/concepts | `docs/theory/` |
| Working examples | `playground/` |

**If documentation doesn't exist for what you're building, STOP and ask.**

When you ship or change user-facing behavior, update `docs/IMPLEMENTATION_STATUS.md` and the relevant LLD/epic in the same PR.

### 2. Understand the Context

Before implementing a feature:
1. Which service owns this functionality?
2. What events will be published?
3. What state transitions are involved?
4. How does this affect other services?

### 3. Write Tests First

If you can't write a test for the behavior, you don't understand the requirement well enough.

---

## Implementation Standards

### Java 21 + Spring Boot 3

- Use records for DTOs and Value Objects
- Use sealed interfaces for domain events
- Use pattern matching in switch expressions
- Constructor injection only (no field @Autowired)
- @Transactional at service layer

### Database

- Always use RLS for multi-tenant tables
- Set tenant context before any query
- Use Flyway for all schema changes
- Use @Version for optimistic locking
- Create indexes for foreign keys

### Kafka

- ALWAYS use the Outbox pattern (no dual writes)
- ALWAYS implement idempotent consumers
- Topic naming: `pravah.{service}.{entity}.events`
- Include eventId in all events for deduplication

### gRPC

- Proto files in `libs/proto/`
- Version in package name (`pravah.pipeline.v1`)
- UNSPECIFIED as first enum value
- Map errors to appropriate Status codes

### State Machines

- Must match diagrams in `docs/lld/03-state-machines.md`
- Enum-based with explicit transition methods
- Invalid transitions throw IllegalStateException
- 100% transition coverage in tests

---

## Testing Requirements

| Type | Naming | Tools | Coverage |
|------|--------|-------|----------|
| Unit | `*Test.java` | JUnit 5, Mockito | >80% |
| Integration | `*IT.java` | Testcontainers | All external deps |
| Kafka | `*IT.java` | @EmbeddedKafka | Consumer behavior |

**Every new code path must have test coverage.**

---

## What NOT to Do

- ❌ Add dependencies without checking ADRs
- ❌ Skip tests "to save time"
- ❌ Use empty catch blocks
- ❌ Hardcode secrets or configuration
- ❌ Trust client-provided tenant IDs
- ❌ Write code that "just works" without understanding why
- ❌ Copy-paste code without understanding it
- ❌ Create TODOs without linked issues
- ❌ Ignore compiler warnings

---

## When Stuck

1. Re-read the relevant theory chapter in `docs/theory/`
2. Check playground exercises for working examples
3. Reference the ADR for the technology decision
4. Ask clarifying questions — don't guess

---

## Pre-Commit Workflow (MANDATORY)

**CRITICAL**: Execute ALL steps in order before EVERY commit. No exceptions.

Full details: `.cursor/rules/12-pre-commit-workflow.mdc`

### Quick Commands

```bash
# Run checks for changed backend/, web/, and cli/ paths (CI parity)
./scripts/pre-commit.sh

# Or per component:
./scripts/ci-backend.sh
./scripts/ci-web.sh
./scripts/ci-cli.sh
```

Requires JDK 21, Docker (integration tests), Node.js 22, Go 1.22+. Optional: `SKIP_INTEGRATION=1`, `SKIP_E2E=1`, `SKIP_CLI=1`, or `FORCE_ALL=1`.

### Step-by-Step

| Step | Command | Verify |
|------|---------|--------|
| 1. Format | `cd backend && ./gradlew spotlessApply` | BUILD SUCCESSFUL |
| 2. Compile | `./gradlew compileJava compileTestJava` | No errors/warnings |
| 3. Backend unit tests | `./gradlew test` | All pass |
| 4. Backend integration | `./gradlew integrationTest` | All pass (Docker) |
| 5. Web | `cd web && npm ci && npm run lint && npm run test && npm run test:e2e && npm run build` | All pass |
| 5b. CLI | `./scripts/ci-cli.sh` or `cd cli && go test -race ./...` | All pass |
| 6. IDE lint | Use ReadLints tool | No new errors |
| 7. Review | `git diff` | No debug/secrets |
| 8. Self-review | Checklist below | All items checked |

### Self-Review Checklist

- [ ] Code matches design in `docs/lld/`
- [ ] Error cases handled properly
- [ ] Test coverage for new code
- [ ] No TODOs without issue links
- [ ] No hardcoded values or secrets
- [ ] Would I understand this in 6 months?

### DO NOT COMMIT IF:

- ❌ Any check above fails
- ❌ Tests are failing
- ❌ You don't understand why the code works
- ❌ There are unresolved TODOs

---

## Commit Message Format

```
type(scope): subject

- Bullet explaining the change
- Another bullet if needed

Refs: US-XX.YY or #issue
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

---

## Git workflow

Prefer **topic branches and pull requests** into `main` or `develop` instead of pushing straight to those branches. On GitHub, use **branch rulesets** (or branch protection) so default merges require PRs and checks; as a **solo maintainer** you can grant **repository admin bypass** when you need to override—see [CONTRIBUTING.md](CONTRIBUTING.md).

### PR title (semantic PR check)

PR titles are linted like conventional commits, with an extra rule: the **subject** (the part after `type(scope): `) **must start with an uppercase letter** (`subjectPattern: ^[A-Z].+$` in `pull-request.yml`). Use sentence-style wording after the colon, e.g. `feat(execution-service): Cancel execution API (US-02.04)`, not `...: cancel ...`.

---

## Quality Gates

Before any PR:

- [ ] Pre-commit workflow completed
- [ ] All tests pass locally
- [ ] No compiler warnings
- [ ] Documentation updated if design changed
- [ ] Follows patterns from LLD docs
- [ ] Security checklist completed
- [ ] Code review checklist completed

---

*Built with engineering discipline — every decision documented, every pattern intentional.*
