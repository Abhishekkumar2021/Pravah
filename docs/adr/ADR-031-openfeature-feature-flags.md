# ADR-031: OpenFeature for Feature Flags

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah needs to control feature availability dynamically — without redeployment. The use cases are:

- **Gradual rollout**: release the Agent Service auto-heal feature to 5% of tenants, monitor error rates, expand to 25%, then 100%. Roll back instantly if problems appear.
- **Kill switches**: a new DAG engine feature causes unexpected CPU spikes. Toggle it off for all tenants immediately, without a deployment.
- **A/B testing**: the new pipeline UI vs the old one — measure completion rates across the two groups.
- **Tenant-specific enablement**: enable the Billing Service compute metering feature only for Enterprise-tier tenants; hide it from Free-tier tenants.
- **Early access programs**: give specific tenants access to beta connectors before general availability.

The naive approach — `if (config.getValue("new_feature_enabled"))` reading from a property file — has critical limitations:
- Changing a property file requires a redeployment. A kill switch that requires 10 minutes to deploy is not a kill switch.
- Property files have no concept of targeting rules (% of users, specific tenants, tenant tier).
- There is no central dashboard to see which flags are active and for whom.
- There is no audit trail for flag changes — a critical compliance requirement.

---

## Decision

**OpenFeature SDK** as the standard interface for all feature flag evaluation in Pravah services, with a pluggable provider backend.

**Why OpenFeature over a proprietary SDK:**

OpenFeature is a CNCF standard that defines a vendor-neutral SDK interface for feature flags. Writing to the OpenFeature interface means:
- Switching the flag management backend (e.g., from a simple Redis implementation to LaunchDarkly or Flagsmith) requires only changing the provider configuration — not changing any feature flag evaluation code across all services.
- The SDK interface is consistent across languages (Java, Go, Python) — the runner binary uses the same pattern as the Spring Boot services.

**Provider choice:**

Pravah uses a **self-hosted OpenFeature provider backed by Redis** for the initial implementation. Flag definitions are stored in Redis as JSON. The OpenFeature evaluation context carries `tenant_id`, `tenant_tier`, and `user_id`.

For enterprise deployments that require a commercial flag management UI with audit trails, the provider can be swapped to **Flagsmith** (self-hosted, open-source) or **LaunchDarkly** without changing service code.

**Flag evaluation in a Spring Boot service:**

```java
@Service
public class AgentService {
    private final Client featureFlags;

    public AgentService(OpenFeatureAPI openFeature) {
        this.featureFlags = openFeature.getClient("agent-service");
    }

    public void maybeAutoHeal(PipelineFailureEvent event) {
        EvaluationContext ctx = new ImmutableContext(Map.of(
            "tenant_id",   event.getTenantId(),
            "tenant_tier", getTenantTier(event.getTenantId()),  // FREE, PRO, ENTERPRISE
            "feature",     "auto-heal"
        ));

        boolean autoHealEnabled = featureFlags.getBooleanValue(
            "agent.auto_heal.enabled",
            false,   // default: off
            ctx
        );

        if (autoHealEnabled) {
            agentOrchestrator.triggerAutoHeal(event);
        }
    }
}
```

**Flag definition structure (stored in Redis KV):**

```json
{
  "key": "agent.auto_heal.enabled",
  "description": "Enable Agent Service auto-heal on pipeline failure",
  "defaultValue": false,
  "rules": [
    {
      "condition": { "tenant_tier": "ENTERPRISE" },
      "value": true
    },
    {
      "condition": { "tenant_id": ["acme-corp", "beta-partner-1"] },
      "value": true
    },
    {
      "condition": { "rollout_percentage": 10 },
      "value": true,
      "comment": "10% gradual rollout for PRO tier"
    }
  ]
}
```

Rules are evaluated in order. First matching rule wins. If no rule matches, `defaultValue` is returned.

**Flag caching to avoid Redis on every evaluation:**

Flags are cached in-process (Caffeine cache) with a 30-second TTL. Flag changes propagate within 30 seconds — acceptable for gradual rollouts. For immediate kill switches, a Redis Pub/Sub channel (`pravah.flags.invalidate`) pushes invalidation events to all service instances, forcing an immediate cache clear.

```
Flag updated in Redis
    │
    ├── Redis Pub/Sub publishes to: pravah.flags.invalidate
    │
    └── All service instances subscribed → clear local Caffeine cache
        → next evaluation fetches fresh value from Redis
```

**Flag taxonomy for Pravah:**

| Prefix | Purpose | Example |
|--------|---------|---------|
| `feature.*` | New product features | `feature.dag_dynamic_tasks.enabled` |
| `agent.*` | Agent Service capabilities | `agent.auto_heal.enabled` |
| `connector.*` | Connector availability | `connector.snowflake.enabled` |
| `infra.*` | Infrastructure behavior | `infra.new_scheduler.enabled` |
| `billing.*` | Billing & quota enforcement | `billing.compute_metering.enabled` |
| `experiment.*` | A/B tests | `experiment.new_pipeline_ui.variant` |

---

## Consequences

### Positive

- **Instant rollback without redeployment**: toggling a flag in Redis takes effect within 30 seconds (or immediately with Pub/Sub invalidation). A bad feature can be disabled at 2am without waking the on-call engineer for a deployment.
- **Granular targeting**: roll out to 5% of tenants, then specific beta partners, then all Enterprise-tier, then GA — all without code changes.
- **Vendor-neutral interface**: OpenFeature's SDK decouples the flag evaluation logic from the backend. Migrating from Redis to Flagsmith or LaunchDarkly is a provider swap.
- **Audit trail**: every flag change in Redis is logged (Vault audit log pattern can be applied; alternatively, a Redis Stream captures all flag writes with timestamp and actor).
- **Dark launches**: new code paths can be deployed but kept dark (flag = false for all) until ready to enable. Reduces deployment risk significantly.

### Negative

- **Redis dependency on the evaluation path**: every feature flag evaluation hits Redis (or the local cache). If Redis is unavailable and the local cache has expired, `getBooleanValue()` returns the `defaultValue`. Services must be designed to operate correctly when a flag defaults to `false` — i.e., new features should default to disabled, not enabled.
- **Flag debt accumulates**: flags that are never cleaned up become permanent conditionals in the code. Requires a hygiene process: flags older than 90 days that are 100% rolled out get removed in the next sprint.
- **Targeting rules add complexity**: complex targeting rules (multi-condition AND/OR) in JSON are hard to debug. The provider must log which rule matched for each evaluation to enable debugging.
- **Cache staleness window**: a 30-second TTL means a kill switch takes up to 30 seconds to propagate without Pub/Sub. This is acceptable but must be documented — "kill switches take up to 30 seconds" in the runbook.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Flag incorrectly enables a feature for all tenants | Flags default to `false`; enabling for all requires explicit `defaultValue: true` — a deliberate config change |
| Redis unavailable during flag evaluation | `defaultValue` is always `false` for new features; safe degradation — new features are off, not on |
| Flag naming collision between services | Prefix convention enforced in code review; flag keys are globally unique by prefix |
| Flag targeting rule logic error | Dry-run mode: evaluate flag for a specific tenant without enabling it; UI shows effective value per tenant |

---

## Alternatives Considered

### Hardcoded Configuration Properties (application.yaml)

`feature.auto_heal.enabled: true` in `application.yaml`, updated via ConfigMap in Kubernetes.

Rejected because:
- Changing a ConfigMap requires a pod restart (or `@RefreshScope` polling) — takes 30–60 seconds minimum, not instant.
- No per-tenant targeting — the same value applies to all tenants.
- No rollout percentage — cannot do gradual rollout without custom code.

### LaunchDarkly (SaaS feature flag platform)

A commercial SaaS platform with a rich UI, audit trail, A/B testing, and targeting rules.

Not chosen as the initial implementation because:
- Introduces a hard dependency on an external SaaS for a critical path (every feature flag evaluation). If LaunchDarkly is unavailable, Pravah's behavior becomes unpredictable.
- Per-seat or per-evaluation pricing at scale is significant cost.
- The OpenFeature provider architecture makes it straightforward to add LaunchDarkly as an alternative provider for enterprise customers who already use it — without changing any service code.

### Environment Variables per Service

Each service reads its feature state from environment variables. Kubernetes ConfigMaps updated for changes.

Rejected for the same reasons as hardcoded config — no dynamic updates without restart, no per-tenant targeting, no rollout percentages.
