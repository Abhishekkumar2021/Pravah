# ADR-027: Notification Service

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah executes pipelines that users depend on. When something goes wrong — or succeeds — users need to know. The notification requirement spans:

- **Pipeline failure** — alert the owner immediately with context (which step failed, error message, run link)
- **SLA breach** — a pipeline was supposed to finish by 09:00; it is 08:55 and it has not started
- **Data quality violation** — a quality rule failed with observed vs threshold values
- **Pipeline success** — for critical pipelines, a confirmation that data is loaded and ready
- **Runner disconnect** — a self-hosted runner that jobs depend on has gone offline
- **Agent auto-heal outcome** — the Agent Service attempted a fix; user needs to know if it worked

Notification delivery must be:
- **Reliable** — a critical pipeline failure that does not notify the owner is worse than useless
- **Configurable per pipeline** — each pipeline specifies its own channels and conditions
- **Multi-channel** — Slack, email, and webhook must all be supported from day one
- **Deduplication-aware** — a pipeline that fails and retries 5 times should not send 5 identical Slack messages

---

## Decision

A dedicated **Notification Service** handles all outbound notification delivery. It is an independent microservice with its own database, decoupled from all other services via Kafka.

**Event-driven architecture:**

The Notification Service subscribes to multiple Kafka topics and maps events to notifications based on per-pipeline configuration:

```
pravah.job.status.updated  ─────┐
pravah.execution.completed ─────┤──► Notification Service ──► Slack
pravah.execution.failed    ─────┤                        ──► Email
data.quality.alerts        ─────┤                        ──► Webhook
pravah.runner.disconnected ─────┘
```

No other service calls the Notification Service directly. All triggers come from Kafka events. This means the Notification Service can be taken down for maintenance without affecting pipeline execution.

**Per-pipeline notification configuration (stored in Pipeline Service):**

```yaml
notifications:
  on_failure:
    - channel: slack
      destination: "#data-alerts"
      message_template: |
        ❌ Pipeline *{{pipeline.name}}* failed
        Step: `{{step.id}}` — {{error.message}}
        Run: {{run.url}}
    - channel: email
      destination: [owner@company.com, oncall@company.com]

  on_success:
    - channel: slack
      destination: "#data-status"
      condition: "execution.duration_minutes > 60"  # Only notify if it took long

  on_sla_breach_risk:
    - channel: slack
      destination: "#data-alerts"
      trigger_minutes_before_deadline: 30

  on_quality_violation:
    - channel: webhook
      destination: https://hooks.company.com/data-quality
      headers:
        Authorization: "Bearer {{secret:notifications-webhook-token}}"
```

**Outbox Pattern for guaranteed delivery:**

The Notification Service uses the Outbox Pattern internally. When it processes a Kafka event and decides a notification should be sent:

1. Insert a `notification_outbox` row (channel, destination, rendered message, status=PENDING) — this is atomic
2. The Outbox Publisher reads PENDING rows and calls the channel adapter (Slack API, SMTP, HTTP)
3. On success: mark DELIVERED
4. On failure: increment retry_count, exponential backoff (1m, 5m, 15m, 1h, 4h)
5. After 5 failed retries: mark DEAD, emit to admin alert

This guarantees that a notification is never silently lost. If the Slack API is down, the notification stays in the outbox and is retried.

**Deduplication:**

A pipeline that fails and retries generates multiple `execution.failed` events. The Notification Service deduplicates using a key:

```
dedup_key = sha256(pipeline_id + run_id + notification_type + channel + destination)
```

The dedup key is stored in Redis with a 1-hour TTL. A notification with the same key within the TTL window is silently discarded. The user gets one Slack message, not five.

**Channel adapters:**

| Channel | Implementation | Secret Storage |
|---------|---------------|----------------|
| Slack | Slack Incoming Webhooks API | Vault KV v2, per-tenant |
| Email | SMTP via Spring Mail (SendGrid for SaaS) | Vault KV v2 |
| Webhook | HTTP POST with configurable headers | Vault KV v2, per-pipeline |
| PagerDuty | PagerDuty Events API v2 | Vault KV v2, per-tenant |
| Microsoft Teams | Teams Incoming Webhooks | Vault KV v2, per-tenant |

**Message templating:**

Messages are rendered using Mustache templates. Variables available in templates:

```
{{pipeline.name}}, {{pipeline.id}}
{{execution.id}}, {{execution.status}}, {{execution.duration_minutes}}
{{step.id}}, {{step.name}}, {{step.status}}
{{error.message}}, {{error.type}}
{{run.url}}                     — deep link to the run in the UI
{{quality.rule}}, {{quality.observed}}, {{quality.threshold}}
{{tenant.name}}
```

Secrets in webhook headers are referenced via `{{secret:vault-key-name}}` and resolved at render time from Vault, never stored in plain text in the pipeline definition.

---

## Consequences

### Positive

- Complete decoupling: the Notification Service can be deployed, scaled, and upgraded independently. A Slack API outage does not affect pipeline execution.
- Guaranteed delivery via outbox: notifications survive Notification Service restarts and channel outages.
- Deduplication prevents alert storms on retry-heavy pipelines.
- Per-pipeline configuration gives teams full control without requiring platform admin involvement.

### Negative

- Additional service to operate. Notification outages are not immediately obvious unless monitored.
- Webhook security is only as strong as the secret management — if a webhook URL or token leaks, notifications can be spoofed.
- Template rendering errors (bad variable reference) silently produce malformed notifications. Template validation at save time catches most issues.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Notification outbox grows unbounded | Delivered rows pruned after 7 days; DEAD rows retained for 30 days for audit |
| Slack rate limiting (Tier 1: 1 msg/sec per workspace) | Notification Service applies per-workspace rate limiting before calling Slack API; excess messages queued with 1s delay |
| Webhook endpoint is a customer-controlled SSRF target | Webhook URLs validated at save time: HTTPS only, private IP ranges blocked (same defense as pipeline webhook triggers, ADR from security) |
| Sensitive data in notification templates | Templates are validated at save time to not include columns tagged as PII in the Data Catalog |
