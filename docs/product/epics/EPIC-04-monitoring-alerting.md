# EPIC-04: Monitoring & Alerting

## Overview

Provide comprehensive observability into workflow health, performance, and issues. This epic covers real-time monitoring, intelligent alerting, dashboards, and the foundation for the "always know what's happening" promise.

**Epic Owner:** Platform Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 112 story points  
**Related ADRs:** ADR-014, ADR-016

---

## Goals

1. Real-time visibility into all running workflows
2. Smart alerts that reduce noise and highlight issues
3. Customizable dashboards for different personas
4. Full audit trail of all system events
5. SLA tracking and reporting

---

## User Stories

### US-04.01: Real-Time Run Dashboard
**As a** data engineer  
**I want to** see all active runs in real-time  
**So that** I know what's happening now

**Acceptance Criteria:**
- [ ] Live updating list of active runs
- [ ] Status, duration, stage progress
- [ ] Filter by workflow, team, status
- [ ] Sort by start time, duration
- [ ] Quick access to run details

**Story Points:** 8  
**Priority:** P0

---

### US-04.02: Run Timeline View
**As a** data engineer  
**I want to** see a Gantt chart of stage execution  
**So that** I understand timing and parallelism

**Acceptance Criteria:**
- [ ] Horizontal timeline per run
- [ ] Stages as bars with duration
- [ ] Parallel stages shown in parallel
- [ ] Hover for stage details
- [ ] Click to navigate to stage

**Story Points:** 8  
**Priority:** P0

---

### US-04.03: Resource Usage View
**As a** data engineer  
**I want to** see CPU/memory usage during a run  
**So that** I can right-size resources

**Acceptance Criteria:**
- [ ] Time-series graph of CPU/memory
- [ ] Per-stage breakdown
- [ ] Peak vs average
- [ ] Overlay with stage boundaries
- [ ] Compare across runs

**Story Points:** 8  
**Priority:** P1

---

### US-04.04: Alert on Failure
**As a** data engineer  
**I want to** be alerted when my workflow fails  
**So that** I can respond quickly

**Acceptance Criteria:**
- [ ] Alert within 1 minute of failure
- [ ] Include workflow name, error summary
- [ ] Link to run detail
- [ ] Configurable per workflow
- [ ] Don't alert on test/dev environments (configurable)

**Story Points:** 5  
**Priority:** P0

---

### US-04.05: Alert on SLA Breach
**As a** data engineer  
**I want to** be alerted when my workflow exceeds its SLA  
**So that** I know about slowdowns

**Acceptance Criteria:**
- [ ] Define SLA duration per workflow
- [ ] Alert when run exceeds SLA
- [ ] Percentage thresholds (warn at 80%, alert at 100%)
- [ ] SLA calculated from historical P95

**Story Points:** 5  
**Priority:** P1

---

### US-04.06: Alert on Anomaly
**As a** data engineer  
**I want to** be alerted on unusual behavior  
**So that** I catch issues proactively

**Acceptance Criteria:**
- [ ] Detect unusual duration (much faster/slower)
- [ ] Detect unusual data volume
- [ ] Detect unusual resource usage
- [ ] Baseline from historical runs
- [ ] Configurable sensitivity

**Story Points:** 8  
**Priority:** P1

---

### US-04.07: Alert Channels - Email
**As a** data engineer  
**I want to** receive alerts via email  
**So that** I don't miss important issues

**Acceptance Criteria:**
- [ ] Configure email addresses per workflow
- [ ] Rich HTML email with details
- [ ] Direct links to UI
- [ ] Unsubscribe option
- [ ] Email templates customizable

**Story Points:** 3  
**Priority:** P0

---

### US-04.08: Alert Channels - Slack
**As a** data engineer  
**I want to** receive alerts in Slack  
**So that** my team sees them

**Acceptance Criteria:**
- [ ] Configure Slack webhook per workflow
- [ ] Rich message with buttons
- [ ] Thread updates for related alerts
- [ ] Acknowledge from Slack
- [ ] Slack app integration (optional)

**Story Points:** 5  
**Priority:** P0

---

### US-04.09: Alert Channels - PagerDuty
**As a** platform engineer  
**I want to** integrate with PagerDuty  
**So that** critical alerts follow our on-call process

**Acceptance Criteria:**
- [ ] PagerDuty integration key
- [ ] Incident creation
- [ ] Auto-resolve when issue clears
- [ ] Priority mapping
- [ ] Service mapping

**Story Points:** 5  
**Priority:** P1

---

### US-04.10: Alert Channels - Webhook
**As a** data engineer  
**I want to** send alerts to a custom webhook  
**So that** I can integrate with any system

**Acceptance Criteria:**
- [ ] HTTP POST with JSON payload
- [ ] Configurable URL and headers
- [ ] Retry on failure
- [ ] Payload template customizable

**Story Points:** 3  
**Priority:** P1

---

### US-04.11: Alert Routing Rules
**As a** platform engineer  
**I want to** route alerts by severity and time  
**So that** the right people get notified

**Acceptance Criteria:**
- [ ] Route by severity (critical → PagerDuty, warning → Slack)
- [ ] Route by time (business hours → Slack, after hours → PagerDuty)
- [ ] Route by team/owner
- [ ] Escalation policies
- [ ] Preview routing before saving

**Story Points:** 8  
**Priority:** P1

---

### US-04.12: Alert Deduplication
**As a** data engineer  
**I want to** not be spammed by duplicate alerts  
**So that** I focus on real issues

**Acceptance Criteria:**
- [ ] Group identical alerts within time window
- [ ] Count of occurrences in alert
- [ ] Smart grouping of related alerts
- [ ] "X more like this" summary

**Story Points:** 5  
**Priority:** P1

---

### US-04.13: Alert Snooze
**As a** data engineer  
**I want to** snooze an alert temporarily  
**So that** I can focus on other work

**Acceptance Criteria:**
- [ ] Snooze for 1hr, 4hr, 24hr, custom
- [ ] Auto-resume after period
- [ ] Snooze reason required
- [ ] Show snoozed alerts separately

**Story Points:** 3  
**Priority:** P1

---

### US-04.14: Maintenance Windows
**As a** platform engineer  
**I want to** define maintenance windows  
**So that** expected downtime doesn't alert

**Acceptance Criteria:**
- [ ] Schedule maintenance window
- [ ] Suppress alerts during window
- [ ] Affected workflows/systems selectable
- [ ] Auto-end after duration
- [ ] Alerts after window show what happened

**Story Points:** 5  
**Priority:** P1

---

### US-04.15: In-App Notification Center
**As a** data engineer  
**I want to** see notifications in the UI  
**So that** I don't miss alerts when working

**Acceptance Criteria:**
- [ ] Bell icon with unread count
- [ ] Dropdown list of recent notifications
- [ ] Mark as read
- [ ] Filter by type, workflow
- [ ] Link to relevant page

**Story Points:** 5  
**Priority:** P1

---

### US-04.16: Custom Dashboard - Create
**As a** data engineer  
**I want to** create custom dashboards  
**So that** I see the metrics I care about

**Acceptance Criteria:**
- [ ] Create new dashboard
- [ ] Add widgets from library
- [ ] Drag-and-drop layout
- [ ] Resize widgets
- [ ] Save and name dashboard

**Story Points:** 8  
**Priority:** P1

---

### US-04.17: Custom Dashboard - Widgets
**As a** data engineer  
**I want to** choose from various widget types  
**So that** I visualize data appropriately

**Acceptance Criteria:**
- [ ] Run status summary (pie chart)
- [ ] Run duration trend (line chart)
- [ ] Success rate (percentage)
- [ ] Recent failures (table)
- [ ] Custom metrics (configurable)

**Story Points:** 13  
**Priority:** P1

---

### US-04.18: Dashboard Sharing
**As a** data engineer  
**I want to** share dashboards with my team  
**So that** we have shared views

**Acceptance Criteria:**
- [ ] Share with specific users or teams
- [ ] Public/private toggle
- [ ] View-only or edit permissions
- [ ] Embed in external pages (iframe)
- [ ] Shareable link with optional auth

**Story Points:** 5  
**Priority:** P2

---

### US-04.19: Audit Log View
**As a** compliance officer  
**I want to** see all system events  
**So that** I can audit activity

**Acceptance Criteria:**
- [ ] Searchable audit log
- [ ] Filter by user, action, resource
- [ ] Date range selection
- [ ] Export to CSV
- [ ] Immutable records

**Story Points:** 8  
**Priority:** P0

---

### US-04.20: Error Classification
**As a** data engineer  
**I want to** see errors classified by type  
**So that** I understand failure patterns

**Acceptance Criteria:**
- [ ] Automatic classification (Infra, Code, Data, Timeout, etc.)
- [ ] Group similar errors
- [ ] Count by classification
- [ ] Top errors dashboard widget
- [ ] Click to see affected runs

**Story Points:** 8  
**Priority:** P1

---

## Technical Tasks

### T-04.01: Metrics Collection Pipeline
Design metrics collection from runners to storage.

**Related ADR:** ADR-016  
**Deliverables:**
- Metrics format specification
- Collection agent
- Time-series storage (Prometheus/VictoriaMetrics)

**Estimate:** 8 points

---

### T-04.02: OpenTelemetry Integration
Implement distributed tracing across services.

**Related ADR:** ADR-014  
**Deliverables:**
- Trace context propagation
- Span creation in services
- Jaeger integration
- Trace-to-log correlation

**Estimate:** 8 points

---

### T-04.03: Alert Engine
Build the alert evaluation and dispatch system.

**Deliverables:**
- Alert rule evaluation
- Condition matching
- Deduplication logic
- Dispatch to channels

**Estimate:** 13 points

---

### T-04.04: Alert Channel Integrations
Implement integrations for alert channels.

**Deliverables:**
- Email sender (SMTP/SES)
- Slack webhook client
- PagerDuty API client
- Generic webhook client

**Estimate:** 8 points

---

### T-04.05: Anomaly Detection Model
Build anomaly detection for metrics.

**Deliverables:**
- Baseline calculation (sliding window)
- Z-score based detection
- Configurable sensitivity
- Training on historical data

**Estimate:** 13 points

---

### T-04.06: Dashboard Backend
Implement dashboard storage and rendering API.

**Deliverables:**
- Dashboard schema
- Widget configuration
- Query execution
- Caching layer

**Estimate:** 8 points

---

### T-04.07: Real-Time Dashboard Updates
Implement WebSocket updates for dashboards.

**Deliverables:**
- WebSocket connection management
- Event streaming
- Partial updates
- Reconnection handling

**Estimate:** 8 points

---

### T-04.08: Audit Log Service
Implement audit logging infrastructure.

**Deliverables:**
- Audit event schema
- Write path (sync)
- Query API
- Retention policy

**Estimate:** 8 points

---

### T-04.09: Error Classification ML
Build error classification model.

**Deliverables:**
- Training data preparation
- Classification model
- Inference service
- Feedback loop for corrections

**Estimate:** 13 points

---

## Dependencies

| Dependency | Epic | Required For |
|------------|------|--------------|
| Run Events | EPIC-02 | Alert triggers |
| User Authentication | EPIC-10 | Audit logs |
| WebSocket Infrastructure | EPIC-09 | Real-time updates |
| UI Framework | EPIC-12 | Dashboard rendering |

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Alert fatigue | High | High | Smart dedup, routing |
| Metrics storage cost | Medium | Medium | Aggregation, retention |
| Dashboard performance | Medium | Medium | Caching, pagination |
| False positive anomalies | Medium | Medium | Tunable sensitivity |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Time to detect failure | < 1 minute | Alert timestamp vs failure |
| Alert actionability | > 80% | Survey: "Was this useful?" |
| Dashboard load time | < 2 seconds | P95 measurement |
| False positive rate | < 10% | Snoozed/dismissed alerts |

---

## Open Questions

1. Should we support Grafana dashboard embedding?
2. What's the audit log retention period?
3. How do we handle alert storms during outages?
4. Should anomaly detection be opt-in or default?

---

## Repo implementation (Sprint 1 — partial)

Tracked in `docs/IMPLEMENTATION_STATUS.md`. Shipped on branch `feat/sprint1-production-alerts`:

- **notification-service:** alert rules CRUD, Kafka `execution.failed`/`execution.completed` dispatch, email/Slack/webhook, dedup, audit log API, in-app notifications + user preferences API.
- **execution-service:** publishes terminal execution events to `pravah.execution.execution.events` when jobs fail/complete.
- **Web:** `/app/alert-rules`, workflow **Alerts** tab, audit log page, notification bell + center + preferences.

Epic acceptance checkboxes below remain the full target; unchecked items are not yet built.

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-18 | Engineering | Sprint 1 partial: notification-service + alerts UI (see repo implementation section) |
| 2026-05-13 | PM | Initial epic definition |
