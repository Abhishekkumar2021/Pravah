# EPIC-12: UI & UX

## Overview

The web application interface — beautiful, fast, and intuitive. The UI is how most users experience Pravah, so it must be excellent.

**Epic Owner:** Product/Design Team  
**Priority:** P0 (MVP Required)  
**Estimated Effort:** 156 story points  
**Related ADRs:** ADR-033

---

## Goals

1. Modern, responsive React application
2. Real-time updates via WebSocket
3. Intuitive navigation and information hierarchy
4. Accessible (WCAG 2.1 AA)
5. Dark mode support

---

## User Stories

### US-12.01: Login Page
**As a** user  
**I want to** a clean login experience  
**So that** I can access Pravah

**Acceptance Criteria:**
- [ ] Email/password form
- [ ] OAuth buttons (Google, GitHub)
- [ ] SSO redirect option
- [ ] "Forgot password" link
- [ ] Remember me checkbox

**Story Points:** 5  
**Priority:** P0

---

### US-12.02: Global Navigation
**As a** user  
**I want to** intuitive navigation  
**So that** I find features easily

**Acceptance Criteria:**
- [ ] Sidebar with sections
- [ ] Collapsible for more space
- [ ] Active state indication
- [ ] Quick switch between projects
- [ ] Breadcrumb navigation

**Story Points:** 8  
**Priority:** P0

---

### US-12.03: Dashboard Home
**As a** user  
**I want to** a dashboard overview  
**So that** I see important info at a glance

**Acceptance Criteria:**
- [ ] Recent workflows
- [ ] Active runs status
- [ ] Recent failures
- [ ] Quick actions
- [ ] Customizable widgets (P2)

**Story Points:** 8  
**Priority:** P0

---

### US-12.04: Workflow List
**As a** user  
**I want to** browse workflows  
**So that** I find what I need

**Acceptance Criteria:**
- [ ] Table with sortable columns
- [ ] Filter by status, owner, tags
- [ ] Search by name
- [ ] Pagination
- [ ] Quick actions (run, edit)

**Story Points:** 8  
**Priority:** P0

---

### US-12.05: Workflow Detail
**As a** user  
**I want to** see workflow details  
**So that** I understand the workflow

**Acceptance Criteria:**
- [ ] Header with name, status, owner
- [ ] Visual DAG representation
- [ ] Recent runs list
- [ ] Schedule information
- [ ] Settings tab

**Story Points:** 8  
**Priority:** P0

---

### US-12.06: Visual DAG Editor
**As a** user  
**I want to** edit workflows visually  
**So that** I don't need to write YAML

**Acceptance Criteria:**
- [x] Drag-drop stage creation (alpha: add from palette; reposition nodes on canvas)
- [x] Connect stages visually (handle drag between stages)
- [x] Stage configuration panel
- [x] Undo/redo
- [x] Export to YAML
- [x] Mini-map for large DAGs

**Story Points:** 21  
**Priority:** P0

---

### US-12.07: Run List
**As a** user  
**I want to** see all runs  
**So that** I track execution

**Acceptance Criteria:**
- [ ] Filter by workflow, status, time
- [ ] Status badges with colors
- [ ] Duration display
- [ ] Click to view details

**Story Points:** 5  
**Priority:** P0

---

### US-12.08: Run Detail
**As a** user  
**I want to** see run execution details  
**So that** I understand what happened

**Acceptance Criteria:**
- [ ] Timeline/Gantt view
- [ ] Per-stage status
- [ ] Expandable logs
- [ ] Error highlighting
- [ ] Retry/cancel actions

**Story Points:** 13  
**Priority:** P0

---

### US-12.09: Log Viewer
**As a** user  
**I want to** view logs clearly  
**So that** I can debug issues

**Acceptance Criteria:**
- [ ] Syntax highlighted logs
- [ ] Level filtering
- [ ] Search within logs
- [ ] Auto-scroll for live logs
- [ ] Download option
- [ ] Jump to error

**Story Points:** 8  
**Priority:** P0

---

### US-12.10: Real-Time Updates
**As a** user  
**I want to** see updates without refresh  
**So that** I stay informed

**Acceptance Criteria:**
- [ ] WebSocket connection
- [ ] Run status updates
- [ ] Log streaming
- [ ] Notification badges
- [ ] Reconnect on disconnect

**Story Points:** 8  
**Priority:** P0

---

### US-12.11: Lineage Visualization
**As a** user  
**I want to** see lineage graphically  
**So that** I understand data flow

**Acceptance Criteria:**
- [ ] Interactive graph
- [ ] Zoom and pan
- [ ] Expand/collapse nodes
- [ ] Highlight paths
- [ ] Click for details

**Story Points:** 13  
**Priority:** P1

---

### US-12.12: Catalog Browser
**As a** user  
**I want to** browse the data catalog  
**So that** I discover datasets

**Acceptance Criteria:**
- [ ] Search and filter
- [ ] Card or table view
- [ ] Preview data
- [ ] Schema display
- [ ] Ownership info

**Story Points:** 8  
**Priority:** P1

---

### US-12.13: Quality Dashboard
**As a** user  
**I want to** see data quality status  
**So that** I trust the data

**Acceptance Criteria:**
- [ ] Quality scores
- [ ] Check results
- [ ] Trend charts
- [ ] Filter by severity

**Story Points:** 8  
**Priority:** P1

---

### US-12.14: Settings Pages
**As a** user  
**I want to** manage settings  
**So that** I configure Pravah

**Acceptance Criteria:**
- [ ] Profile settings
- [ ] Project settings
- [ ] Connections management
- [ ] API tokens

**Story Points:** 8  
**Priority:** P0

---

### US-12.15: User Management
**As a** admin  
**I want to** manage users  
**So that** I control access

**Acceptance Criteria:**
- [ ] User list
- [ ] Invite users
- [ ] Edit roles
- [ ] Deactivate users

**Story Points:** 8  
**Priority:** P0

---

### US-12.16: Connection Management
**As a** user  
**I want to** manage data connections  
**So that** I connect to sources

**Acceptance Criteria:**
- [ ] Connection list
- [ ] Create/edit forms
- [ ] Test connection
- [ ] Credential masking

**Story Points:** 8  
**Priority:** P0

---

### US-12.17: Schedule Builder UI
**As a** user  
**I want to** build schedules visually  
**So that** I don't memorize cron

**Acceptance Criteria:**
- [ ] Preset options
- [ ] Custom builder
- [ ] Preview next runs
- [ ] Timezone selector

**Story Points:** 5  
**Priority:** P0

---

### US-12.18: Alert Configuration
**As a** user  
**I want to** configure alerts in UI  
**So that** I get notified

**Acceptance Criteria:**
- [ ] Alert rule builder
- [ ] Channel selection
- [ ] Test alert
- [ ] Severity levels

**Story Points:** 8  
**Priority:** P1

---

### US-12.19: Search - Global
**As a** user  
**I want to** search across everything  
**So that** I find anything quickly

**Acceptance Criteria:**
- [ ] Cmd+K shortcut
- [ ] Search workflows, runs, datasets
- [ ] Recent searches
- [ ] Quick navigation

**Story Points:** 8  
**Priority:** P1

---

### US-12.20: Dark Mode
**As a** user  
**I want to** use dark mode  
**So that** it's comfortable at night

**Acceptance Criteria:**
- [ ] Toggle in settings
- [ ] System preference detection
- [ ] Consistent styling
- [ ] Persisted preference

**Story Points:** 5  
**Priority:** P1

---

### US-12.21: Responsive Design
**As a** user  
**I want to** use Pravah on tablet  
**So that** I'm not desktop-bound

**Acceptance Criteria:**
- [ ] Responsive layout
- [ ] Touch-friendly targets
- [ ] Collapsible navigation
- [ ] Essential features accessible

**Story Points:** 8  
**Priority:** P1

---

### US-12.22: Keyboard Navigation
**As a** user  
**I want to** use keyboard shortcuts  
**So that** I'm more productive

**Acceptance Criteria:**
- [ ] Navigation shortcuts
- [ ] Action shortcuts
- [ ] Shortcut help (?)
- [ ] Customizable (P2)

**Story Points:** 5  
**Priority:** P1

---

### US-12.23: Accessibility
**As a** user  
**I want to** accessible UI  
**So that** everyone can use Pravah

**Acceptance Criteria:**
- [ ] WCAG 2.1 AA compliance
- [ ] Screen reader support
- [ ] Keyboard navigation
- [ ] Sufficient contrast

**Story Points:** 13  
**Priority:** P1

---

### US-12.24: Error States
**As a** user  
**I want to** helpful error messages  
**So that** I recover from mistakes

**Acceptance Criteria:**
- [ ] Inline form errors
- [ ] Toast notifications
- [ ] 404/500 pages
- [ ] Retry suggestions

**Story Points:** 5  
**Priority:** P0

---

### US-12.25: Loading States
**As a** user  
**I want to** clear loading indicators  
**So that** I know data is loading

**Acceptance Criteria:**
- [ ] Skeleton screens
- [ ] Spinners for actions
- [ ] Progress bars for long ops
- [ ] Graceful timeout handling

**Story Points:** 5  
**Priority:** P0

---

## Technical Tasks

### T-12.01: React Project Setup
Set up React + TypeScript + Vite.

**Estimate:** 5 points

### T-12.02: Component Library
Build or integrate component library.

**Estimate:** 13 points

### T-12.03: Design System
Create design tokens, styles, theming.

**Estimate:** 8 points

### T-12.04: State Management
Set up state management (Zustand/Redux).

**Estimate:** 5 points

### T-12.05: API Client Layer
Build typed API client.

**Estimate:** 8 points

### T-12.06: WebSocket Integration
Implement WebSocket for real-time.

**Estimate:** 8 points

### T-12.07: Graph Visualization
Integrate react-flow for DAG/lineage.

**Estimate:** 13 points

### T-12.08: Authentication UI
Build auth flows and session management.

**Estimate:** 8 points

### T-12.09: Accessibility Audit
Conduct and fix accessibility issues.

**Estimate:** 8 points

### T-12.10: Performance Optimization
Optimize bundle size, lazy loading.

**Estimate:** 8 points

---

## Design Principles

1. **Progressive Disclosure**: Show simple first, reveal complexity on demand
2. **Immediate Feedback**: Every action has instant visual response
3. **Consistent Patterns**: Same interaction = same result
4. **Error Prevention**: Design to prevent mistakes, not just catch them
5. **Keyboard First**: All features accessible via keyboard

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Time to first meaningful paint | < 2 seconds |
| Lighthouse performance score | > 90 |
| User task completion rate | > 95% |
| Accessibility audit score | 100% (AA) |
| User satisfaction (NPS) | > 50 |

---

## Changelog

| Date | Author | Changes |
|------|--------|---------|
| 2026-05-13 | PM | Initial epic definition |
