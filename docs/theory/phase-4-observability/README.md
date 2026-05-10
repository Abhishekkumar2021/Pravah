# Phase 4 — Observability & Reliability

> **Status:** ✅ Complete — 7 of 7 chapters

A system you cannot observe is a system you cannot trust. This phase covers everything needed to understand what Pravah is doing at any moment, detect problems before users do, and build confidence that the system works correctly under failure.

The three pillars of observability — metrics, traces, logs — each answer a different question. SLOs and error budgets turn "reliable" into a number. Alerting turns that number into a human action. Probes let Kubernetes self-heal. Chaos engineering ensures the self-healing actually works.

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [4.1](4.1-slos-slas-error-budgets.md) | **SLOs, SLAs & Error Budgets** | Defining reliability in numbers; error budget burn rates; the policy that turns data into decisions |
| [4.2](4.2-metrics-prometheus-grafana.md) | **Metrics with Prometheus & Grafana** | Counter, gauge, histogram; the cardinality trap; recording rules; Pravah's core dashboards |
| [4.3](4.3-distributed-tracing-opentelemetry-jaeger.md) | **Distributed Tracing with OpenTelemetry & Jaeger** | Trace context propagation through HTTP, gRPC, and Kafka; sampling strategies; correlating traces with logs and metrics |
| [4.4](4.4-structured-logging-elk.md) | **Structured Logging & the ELK Stack** | JSON log schema; MDC context propagation; Filebeat → Logstash → Elasticsearch → Kibana; incident investigation |
| [4.5](4.5-alerting-strategy.md) | **Alerting Strategy** | Three-tier model (page, ticket, dashboard); what makes a good alert; Alertmanager routing, grouping, inhibition; runbook design |
| [4.6](4.6-health-checks-readiness-liveness.md) | **Health Checks, Readiness & Liveness Probes** | Three probe types; what each checks; circuit breaker integration; graceful shutdown; probe design pitfalls |
| [4.7](4.7-chaos-engineering.md) | **Chaos Engineering** | Why deliberate failure injection makes systems more reliable; Pravah's experiment library; safety mechanisms; Game Days |

---

## The Three Pillars + Three Enablers

```
The three pillars of observability:
  Metrics    → What is the aggregate system behavior? (rate, latency, saturation)
  Traces     → What path did this specific request take? (where is the bottleneck?)
  Logs       → What exactly was the system doing at this moment? (the full story)

The three enablers of reliability:
  SLOs       → What does "reliable" mean, in numbers, for each component?
  Alerting   → Who gets notified, when, and with what information?
  Chaos      → Does the system actually behave correctly when things break?
```

---

## Signal Flow in Pravah

```
Every Pravah service pod:
  │
  ├──► /actuator/metrics  ──────────────────► Prometheus (scrape every 15s)
  │                                               │
  │                                               ├──► Recording rules (SLI computation)
  │                                               ├──► Alert rules → Alertmanager → PagerDuty/Slack
  │                                               └──► Grafana dashboards
  │
  ├──► OTEL SDK (traces, spans) ───────────► OTEL Collector ──► Jaeger
  │        │                                                       │
  │        └── trace_id injected into every log line              │
  │                                                    ◄──── exemplar links
  │
  ├──► JSON logs to stdout ────────────────► Filebeat ──► Logstash ──► Elasticsearch ──► Kibana
  │        (includes trace_id, tenant_id, job_id, ...)
  │
  └──► /actuator/health/liveness    ──► Kubernetes: restart if fails
       /actuator/health/readiness   ──► Kubernetes: remove from load balancer if fails
```

---

## Key Numbers for Pravah's Observability Stack

```
Metric                              Value          Notes
────────────────────────────────────────────────────────────────────────
Prometheus scrape interval          15 seconds     Balance between freshness and load
Recording rule evaluation           30 seconds     Pre-compute expensive SLI queries
Grafana dashboard refresh           30 seconds     Near-real-time dashboards
Alert group_wait                    30 seconds     Consolidate storm into one notification
Liveness probe period               10 seconds     Detect hung processes within 30s
Readiness probe period              5 seconds      Remove degraded pod within 15s
Log retention hot                   30 days        Full-speed Elasticsearch queries
Log retention warm                  90 days        Compressed, slower queries
Trace sampling (errors + slow)      100%           Never lose a bad trace
Trace sampling (healthy)            1%             Baseline for pattern analysis
Error budget (job success 99.5%)    50,000 / 10M   Allowed failures per 28-day period
Fast burn alert threshold           14×            Exhausts budget in 2 days
Slow burn alert threshold           3×             Exhausts budget in 9 days
```

---

## Navigation

← [Phase 3 — Database Design & Scaling](../phase-3-database-design/README.md)
→ [Phase 5 — Security, Auth & Multi-Tenancy](../phase-5-security/README.md) *(next)*
