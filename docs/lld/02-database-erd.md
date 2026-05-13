# Database Schema & ERD

Complete database schema for all Pravah services. Each service owns its database with Row-Level Security (RLS) for multi-tenancy.

---

## Database Overview

```mermaid
graph TB
    subgraph "Pravah Databases"
        subgraph "Core Services"
            PDB[(pipeline_db)]
            EDB[(execution_db)]
            TDB[(tenant_db)]
            SDB[(scheduler_db)]
        end
        subgraph "Support Services"
            RDB[(runner_db)]
            MDB[(metadata_db)]
            NDB[(notification_db)]
            ADB[(agent_db)]
        end
    end
    
    PDB --- |Event-sourced pipelines|P1[Pipeline Service]
    EDB --- |Job state tracking|E1[Execution Service]
    TDB --- |Users, roles, permissions|T1[Tenant Service]
    SDB --- |Schedules, triggers|S1[Scheduler Service]
    RDB --- |Runner fleet management|R1[Runner Service]
    MDB --- |Lineage, catalog|M1[Metadata Service]
    NDB --- |Alerts, channels|N1[Notification Service]
    ADB --- |AI diagnosis, observations|A1[Agent Service]
```

**All databases:**
- PostgreSQL 16
- RLS enabled for tenant isolation
- PgBouncer for connection pooling
- Flyway for migrations

---

## 1. Tenant Database (tenant_db)

### ERD

```mermaid
erDiagram
    organizations ||--o{ teams : has
    organizations ||--o{ users : has
    organizations ||--o{ roles : has
    organizations ||--o{ audit_logs : generates
    
    teams ||--o{ projects : contains
    teams ||--o{ team_members : has
    
    users ||--o{ team_members : belongs_to
    users ||--o{ api_tokens : owns
    users ||--o{ sessions : has
    users ||--o{ project_members : belongs_to
    
    roles ||--o{ team_members : assigned_via
    roles ||--o{ project_members : assigned_via
    
    projects ||--o{ project_members : has

    organizations {
        uuid id PK
        varchar name
        varchar slug UK
        varchar tier
        jsonb settings
        timestamptz created_at
        timestamptz updated_at
    }
    
    teams {
        uuid id PK
        uuid org_id FK
        varchar name
        jsonb settings
        timestamptz created_at
    }
    
    projects {
        uuid id PK
        uuid org_id FK
        uuid team_id FK
        varchar name
        text description
        jsonb settings
        timestamptz created_at
    }
    
    users {
        uuid id PK
        uuid org_id FK
        varchar email
        varchar name
        varchar password_hash
        varchar mfa_secret
        varchar status
        timestamptz last_login_at
        timestamptz created_at
    }
    
    roles {
        uuid id PK
        uuid org_id FK
        varchar name
        jsonb permissions
        boolean is_system
        timestamptz created_at
    }
    
    team_members {
        uuid user_id PK,FK
        uuid team_id PK,FK
        uuid role_id FK
        timestamptz joined_at
    }
    
    project_members {
        uuid user_id PK,FK
        uuid project_id PK,FK
        uuid role_id FK
    }
    
    api_tokens {
        uuid id PK
        uuid user_id FK
        uuid org_id FK
        varchar name
        varchar token_hash UK
        jsonb permissions
        timestamptz expires_at
        timestamptz last_used_at
        timestamptz created_at
    }
    
    sessions {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        inet ip_address
        text user_agent
        timestamptz created_at
        timestamptz expires_at
    }
    
    audit_logs {
        uuid id PK
        uuid org_id FK
        uuid user_id FK
        varchar action
        varchar resource_type
        uuid resource_id
        jsonb details
        inet ip_address
        timestamptz created_at
    }
```

### Table Definitions

```sql
-- Organizations (top-level tenant)
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    tier            VARCHAR(50) NOT NULL DEFAULT 'free', -- free, team, enterprise
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Teams within an organization
CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    name            VARCHAR(255) NOT NULL,
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(org_id, name)
);

-- Projects within a team
CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(team_id, name)
);

-- Users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255), -- NULL for OAuth-only users
    mfa_secret      VARCHAR(255), -- TOTP secret, encrypted
    status          VARCHAR(50) NOT NULL DEFAULT 'active', -- active, inactive, pending
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(org_id, email)
);

-- Roles (system + custom)
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID REFERENCES organizations(id), -- NULL for system roles
    name            VARCHAR(100) NOT NULL,
    permissions     JSONB NOT NULL, -- array of permission strings
    is_system       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(org_id, name)
);

-- Insert system roles
INSERT INTO roles (id, name, permissions, is_system) VALUES
    ('00000000-0000-0000-0000-000000000001', 'owner', '["*"]', true),
    ('00000000-0000-0000-0000-000000000002', 'admin', '["pipelines:*", "executions:*", "settings:read"]', true),
    ('00000000-0000-0000-0000-000000000003', 'editor', '["pipelines:read", "pipelines:write", "executions:*"]', true),
    ('00000000-0000-0000-0000-000000000004', 'viewer', '["pipelines:read", "executions:read"]', true);

-- Team membership
CREATE TABLE team_members (
    user_id         UUID NOT NULL REFERENCES users(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    role_id         UUID NOT NULL REFERENCES roles(id),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    PRIMARY KEY (user_id, team_id)
);

-- Project membership (optional, for project-level access)
CREATE TABLE project_members (
    user_id         UUID NOT NULL REFERENCES users(id),
    project_id      UUID NOT NULL REFERENCES projects(id),
    role_id         UUID NOT NULL REFERENCES roles(id),
    
    PRIMARY KEY (user_id, project_id)
);

-- API tokens
CREATE TABLE api_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    name            VARCHAR(255) NOT NULL,
    token_hash      VARCHAR(255) NOT NULL UNIQUE, -- SHA-256 of token
    permissions     JSONB NOT NULL, -- scoped permissions
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- User sessions
CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    ip_address      INET,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- Audit logs (append-only)
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    user_id         UUID REFERENCES users(id), -- NULL for system actions
    action          VARCHAR(100) NOT NULL, -- e.g., 'pipeline.created'
    resource_type   VARCHAR(100) NOT NULL, -- e.g., 'pipeline'
    resource_id     UUID,
    details         JSONB NOT NULL DEFAULT '{}',
    ip_address      INET,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_users_org_id ON users(org_id);
CREATE INDEX idx_teams_org_id ON teams(org_id);
CREATE INDEX idx_projects_team_id ON projects(team_id);
CREATE INDEX idx_audit_logs_org_created ON audit_logs(org_id, created_at DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- Row-Level Security
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_users ON users
    USING (org_id = current_setting('app.current_org_id')::UUID);
CREATE POLICY tenant_isolation_teams ON teams
    USING (org_id = current_setting('app.current_org_id')::UUID);
CREATE POLICY tenant_isolation_projects ON projects
    USING (org_id = current_setting('app.current_org_id')::UUID);
CREATE POLICY tenant_isolation_audit ON audit_logs
    USING (org_id = current_setting('app.current_org_id')::UUID);
```

---

## 2. Pipeline Database (pipeline_db)

### ERD

```mermaid
erDiagram
    pipeline_events ||--o| pipelines : "projected to"
    pipelines ||--o{ pipeline_versions : has
    pipelines ||--o{ pipeline_tags : tagged_with
    pipelines ||--o{ pipeline_secrets : uses
    tags ||--o{ pipeline_tags : applied_to
    connections ||--o{ pipelines : "used by"
    outbox ||--o| pipeline_events : "publishes"

    pipeline_events {
        uuid event_id PK
        uuid pipeline_id
        uuid tenant_id
        varchar event_type
        int event_version
        jsonb payload
        jsonb metadata
        timestamptz created_at
    }
    
    pipelines {
        uuid id PK
        uuid tenant_id
        uuid project_id
        varchar name
        text description
        int current_version
        varchar status
        timestamptz created_at
        timestamptz updated_at
        uuid created_by
    }
    
    pipeline_versions {
        uuid id PK
        uuid pipeline_id FK
        int version
        jsonb definition
        timestamptz published_at
        uuid published_by
    }
    
    tags {
        uuid id PK
        uuid tenant_id
        varchar name
        varchar color
    }
    
    pipeline_tags {
        uuid pipeline_id PK,FK
        uuid tag_id PK,FK
    }
    
    connections {
        uuid id PK
        uuid tenant_id
        varchar name
        varchar type
        jsonb config
        varchar vault_secret_path
        uuid created_by
        timestamptz created_at
    }
    
    pipeline_secrets {
        uuid id PK
        uuid pipeline_id FK
        varchar name
        varchar vault_path
    }
    
    outbox {
        uuid id PK
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        jsonb payload
        timestamptz created_at
        timestamptz published_at
    }
```

### Table Definitions

```sql
-- Event store (source of truth)
CREATE TABLE pipeline_events (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_version   INT NOT NULL, -- for optimistic locking
    payload         JSONB NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Ensure sequential versioning per pipeline
    UNIQUE(pipeline_id, event_version)
);

-- Read model (projected from events)
CREATE TABLE pipelines (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    project_id      UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    current_version INT NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL DEFAULT 'draft',
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    
    UNIQUE(project_id, name)
);

-- Immutable published versions
CREATE TABLE pipeline_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    version         INT NOT NULL,
    definition      JSONB NOT NULL, -- complete pipeline definition
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by    UUID NOT NULL,
    
    UNIQUE(pipeline_id, version)
);

-- Tags for organization
CREATE TABLE tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(100) NOT NULL,
    color           VARCHAR(7), -- hex color
    
    UNIQUE(tenant_id, name)
);

CREATE TABLE pipeline_tags (
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    tag_id          UUID NOT NULL REFERENCES tags(id),
    
    PRIMARY KEY (pipeline_id, tag_id)
);

-- Connections to external systems
CREATE TABLE connections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(100) NOT NULL, -- snowflake, bigquery, postgres, s3, etc.
    config          JSONB NOT NULL, -- non-sensitive config
    vault_secret_path VARCHAR(500), -- path in Vault for credentials
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(tenant_id, name)
);

-- Pipeline secret references
CREATE TABLE pipeline_secrets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    name            VARCHAR(255) NOT NULL, -- reference name in pipeline
    vault_path      VARCHAR(500) NOT NULL, -- actual Vault path
    
    UNIQUE(pipeline_id, name)
);

-- Transactional outbox
CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ -- NULL until published
);

-- Indexes
CREATE INDEX idx_pipeline_events_pipeline ON pipeline_events(pipeline_id, event_version);
CREATE INDEX idx_pipeline_events_tenant ON pipeline_events(tenant_id, created_at DESC);
CREATE INDEX idx_pipelines_project ON pipelines(project_id);
CREATE INDEX idx_pipelines_tenant_status ON pipelines(tenant_id, status);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- RLS
ALTER TABLE pipelines ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE connections ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_pipelines ON pipelines
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
CREATE POLICY tenant_isolation_events ON pipeline_events
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
CREATE POLICY tenant_isolation_connections ON connections
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

### Pipeline Definition JSON Schema

```json
{
  "name": "daily_sales_etl",
  "description": "Load sales data from Snowflake, transform, load to warehouse",
  "stages": [
    {
      "id": "extract",
      "name": "Extract Sales",
      "type": "sql",
      "config": {
        "connection": "snowflake_prod",
        "query": "SELECT * FROM sales WHERE date = '${date}'"
      },
      "depends_on": []
    },
    {
      "id": "transform",
      "name": "Transform Sales",
      "type": "python",
      "config": {
        "script": "transform_sales.py",
        "requirements": ["pandas==2.0.0"]
      },
      "depends_on": ["extract"]
    },
    {
      "id": "load",
      "name": "Load to Warehouse",
      "type": "sql",
      "config": {
        "connection": "bigquery_prod",
        "query": "INSERT INTO sales_mart SELECT * FROM ${transform.output}"
      },
      "depends_on": ["transform"]
    }
  ],
  "variables": {
    "date": {
      "type": "string",
      "default": "${execution_date}"
    }
  },
  "retry": {
    "max_attempts": 3,
    "backoff_multiplier": 2
  },
  "timeout_minutes": 60
}
```

---

## 3. Execution Database (execution_db)

### ERD

```mermaid
erDiagram
    executions ||--o{ jobs : contains
    executions ||--o{ checkpoints : has
    jobs ||--o{ job_logs : generates
    processed_events ||--o| executions : "tracks idempotency"

    executions {
        uuid id PK
        uuid tenant_id
        uuid pipeline_id
        int pipeline_version
        varchar status
        varchar trigger_type
        uuid triggered_by
        jsonb parameters
        timestamptz started_at
        timestamptz completed_at
        text error_message
        varchar error_category
        uuid retry_of FK
        timestamptz created_at
    }
    
    jobs {
        uuid id PK
        uuid execution_id FK
        varchar stage_id
        varchar stage_name
        varchar status
        uuid runner_id
        int attempt
        timestamptz queued_at
        timestamptz started_at
        timestamptz completed_at
        int exit_code
        text error_message
        jsonb output
        jsonb artifacts
        jsonb metrics
    }
    
    job_logs {
        uuid id PK
        uuid job_id FK
        timestamptz log_time
        varchar level
        text message
        jsonb attributes
    }
    
    checkpoints {
        uuid execution_id PK,FK
        varchar stage_id PK
        jsonb state
        timestamptz updated_at
    }
    
    processed_events {
        uuid event_id PK
        timestamptz processed_at
    }
    
    outbox {
        uuid id PK
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        jsonb payload
        timestamptz created_at
        timestamptz published_at
    }
```

### Table Definitions

```sql
-- Execution (one run of a pipeline)
CREATE TABLE executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    pipeline_version INT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    trigger_type    VARCHAR(50) NOT NULL,
    triggered_by    UUID, -- user_id, NULL for scheduled/event
    parameters      JSONB NOT NULL DEFAULT '{}',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    error_category  VARCHAR(50),
    retry_of        UUID REFERENCES executions(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Jobs (one stage execution within a run)
CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID NOT NULL REFERENCES executions(id),
    stage_id        VARCHAR(255) NOT NULL,
    stage_name      VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    runner_id       UUID,
    attempt         INT NOT NULL DEFAULT 1,
    queued_at       TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    exit_code       INT,
    error_message   TEXT,
    output          JSONB,
    artifacts       JSONB, -- {"logs": "s3://...", "result": "s3://..."}
    metrics         JSONB, -- {"cpu_seconds": 120, "memory_mb": 512}
    
    UNIQUE(execution_id, stage_id, attempt)
);

-- Job logs (partitioned for performance)
CREATE TABLE job_logs (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL,
    log_time        TIMESTAMPTZ NOT NULL,
    level           VARCHAR(10) NOT NULL,
    message         TEXT NOT NULL,
    attributes      JSONB
) PARTITION BY RANGE (log_time);

-- Create partitions for logs (example: monthly)
CREATE TABLE job_logs_2026_05 PARTITION OF job_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE job_logs_2026_06 PARTITION OF job_logs
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- Checkpoints for resumable execution
CREATE TABLE checkpoints (
    execution_id    UUID NOT NULL,
    stage_id        VARCHAR(255) NOT NULL,
    state           JSONB NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    PRIMARY KEY (execution_id, stage_id)
);

-- Idempotency tracking
CREATE TABLE processed_events (
    event_id        UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Outbox
CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_executions_tenant_status ON executions(tenant_id, status);
CREATE INDEX idx_executions_pipeline ON executions(pipeline_id, created_at DESC);
CREATE INDEX idx_executions_created ON executions(created_at DESC);
CREATE INDEX idx_jobs_execution ON jobs(execution_id);
CREATE INDEX idx_jobs_runner ON jobs(runner_id, status) WHERE status IN ('queued', 'running');
CREATE INDEX idx_job_logs_job ON job_logs(job_id, log_time);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- RLS
ALTER TABLE executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_executions ON executions
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
-- Jobs inherit tenant from execution (join required)
```

---

## 4. Scheduler Database (scheduler_db)

### ERD

```mermaid
erDiagram
    schedules ||--o{ schedule_history : logs
    
    schedules {
        uuid id PK
        uuid tenant_id
        uuid pipeline_id
        varchar name
        varchar cron_expression
        varchar timezone
        jsonb parameters
        boolean is_active
        varchar catchup_policy
        timestamptz next_run_at
        timestamptz last_run_at
        timestamptz created_at
        uuid created_by
    }
    
    event_triggers {
        uuid id PK
        uuid tenant_id
        uuid pipeline_id
        varchar trigger_type
        jsonb config
        boolean is_active
        timestamptz created_at
    }
    
    webhooks {
        uuid id PK
        uuid tenant_id
        uuid pipeline_id
        varchar token_hash UK
        boolean is_active
        timestamptz last_triggered
        timestamptz created_at
    }
    
    schedule_history {
        uuid id PK
        uuid schedule_id FK
        timestamptz scheduled_time
        uuid execution_id
        varchar status
        timestamptz created_at
    }
    
    scheduler_locks {
        varchar lock_name PK
        varchar holder_id
        timestamptz acquired_at
        timestamptz expires_at
    }
```

### Table Definitions

```sql
-- Cron schedules
CREATE TABLE schedules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    name            VARCHAR(255),
    cron_expression VARCHAR(100) NOT NULL,
    timezone        VARCHAR(100) NOT NULL DEFAULT 'UTC',
    parameters      JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT true,
    catchup_policy  VARCHAR(50) NOT NULL DEFAULT 'skip',
    next_run_at     TIMESTAMPTZ,
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID NOT NULL
);

-- Event-based triggers
CREATE TABLE event_triggers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL, -- kafka, webhook, file_sensor, partition_sensor
    config          JSONB NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Webhook endpoints
CREATE TABLE webhooks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    last_triggered  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Schedule execution history
CREATE TABLE schedule_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID NOT NULL REFERENCES schedules(id),
    scheduled_time  TIMESTAMPTZ NOT NULL,
    execution_id    UUID,
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Leader election
CREATE TABLE scheduler_locks (
    lock_name       VARCHAR(100) PRIMARY KEY,
    holder_id       VARCHAR(255) NOT NULL,
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- Indexes
CREATE INDEX idx_schedules_next_run ON schedules(next_run_at) WHERE is_active = true;
CREATE INDEX idx_schedules_tenant ON schedules(tenant_id);
CREATE INDEX idx_event_triggers_tenant ON event_triggers(tenant_id);
CREATE INDEX idx_webhooks_token ON webhooks(token_hash);

-- RLS
ALTER TABLE schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_triggers ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_schedules ON schedules
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

---

## 5. Runner Database (runner_db)

### ERD

```mermaid
erDiagram
    runners ||--o| runner_certificates : has
    runners ||--o{ runner_assignments : assigned

    runners {
        uuid id PK
        uuid tenant_id
        varchar hostname
        varchar version
        varchar status
        int capacity
        int active_jobs
        jsonb labels
        timestamptz last_heartbeat
        timestamptz registered_at
        uuid certificate_id FK
    }
    
    runner_certificates {
        uuid id PK
        uuid tenant_id
        varchar serial_number
        varchar subject_cn
        timestamptz issued_at
        timestamptz expires_at
        timestamptz revoked_at
        varchar revocation_reason
    }
    
    runner_assignments {
        uuid id PK
        uuid runner_id FK
        uuid job_id
        timestamptz assigned_at
        timestamptz acknowledged_at
    }
```

**Note:** Runner heartbeats and real-time capacity are stored in Redis (30s TTL) for performance. The database stores persistent state only.

---

## 6. Metadata Database (metadata_db)

### ERD

```mermaid
erDiagram
    datasets ||--o{ dataset_columns : has
    datasets ||--o{ dataset_schemas : "version history"
    datasets ||--o| dataset_quality : measured_by
    datasets ||--o{ lineage_edges : "source or target"
    
    glossary_terms ||--o{ column_term_mapping : applied_to
    dataset_columns ||--o{ column_term_mapping : has

    datasets {
        uuid id PK
        uuid tenant_id
        varchar name
        varchar type
        varchar source_system
        varchar location
        text description
        uuid owner_id
        varchar classification
        timestamptz last_refreshed
        bigint row_count
        bigint size_bytes
        timestamptz created_at
    }
    
    dataset_columns {
        uuid id PK
        uuid dataset_id FK
        varchar name
        varchar data_type
        text description
        boolean is_nullable
        boolean is_pii
        varchar pii_type
        jsonb statistics
    }
    
    dataset_schemas {
        uuid id PK
        uuid dataset_id FK
        int version
        jsonb schema
        timestamptz captured_at
    }
    
    lineage_edges {
        uuid id PK
        uuid source_dataset FK
        uuid target_dataset FK
        varchar source_column
        varchar target_column
        text transformation
        uuid pipeline_id
        uuid job_id
        timestamptz captured_at
    }
    
    dataset_quality {
        uuid dataset_id PK,FK
        int score
        decimal completeness
        decimal freshness
        decimal accuracy
        timestamptz calculated_at
    }
    
    glossary_terms {
        uuid id PK
        uuid tenant_id
        varchar term
        text definition
        uuid parent_id FK
    }
    
    column_term_mapping {
        uuid column_id PK,FK
        uuid term_id PK,FK
    }
```

---

## 7. Notification Database (notification_db)

### ERD

```mermaid
erDiagram
    alert_rules ||--o{ alerts : triggers
    alerts ||--o{ notification_deliveries : "delivered via"

    alert_rules {
        uuid id PK
        uuid tenant_id
        uuid pipeline_id
        varchar name
        jsonb condition
        varchar severity
        jsonb channels
        boolean is_active
        timestamptz created_at
    }
    
    alerts {
        uuid id PK
        uuid tenant_id
        uuid rule_id FK
        uuid execution_id
        varchar severity
        text title
        text message
        varchar status
        timestamptz snoozed_until
        timestamptz created_at
        timestamptz resolved_at
    }
    
    notification_deliveries {
        uuid id PK
        uuid alert_id FK
        varchar channel_type
        jsonb channel_config
        varchar status
        int attempts
        timestamptz last_attempt_at
        text error_message
        timestamptz sent_at
    }
```

### Table Definitions

```sql
-- Alert rules
CREATE TABLE alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID, -- NULL for global rules
    name            VARCHAR(255) NOT NULL,
    condition       JSONB NOT NULL, -- {"type": "failure"} or {"type": "sla", "threshold_minutes": 60}
    severity        VARCHAR(50) NOT NULL, -- info, warning, critical
    channels        JSONB NOT NULL, -- [{"type": "slack", "webhook": "..."}, {"type": "email", "to": [...]}]
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Alert history
CREATE TABLE alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    rule_id         UUID REFERENCES alert_rules(id),
    execution_id    UUID,
    severity        VARCHAR(50) NOT NULL,
    title           TEXT NOT NULL,
    message         TEXT NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'active', -- active, acknowledged, resolved, snoozed
    snoozed_until   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);

-- Notification delivery
CREATE TABLE notification_deliveries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id        UUID NOT NULL REFERENCES alerts(id),
    channel_type    VARCHAR(50) NOT NULL,
    channel_config  JSONB NOT NULL,
    status          VARCHAR(50) NOT NULL, -- pending, sent, failed
    attempts        INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    error_message   TEXT,
    sent_at         TIMESTAMPTZ
);
```

---

## 8. Agent Database (agent_db)

### ERD

```mermaid
erDiagram
    agent_observations ||--o{ healing_actions : proposes
    
    agent_observations {
        uuid id PK
        uuid tenant_id
        uuid execution_id
        jsonb observation
        text root_cause
        decimal confidence
        timestamptz created_at
    }
    
    healing_actions {
        uuid id PK
        uuid observation_id FK
        varchar action_type
        text description
        jsonb proposed_change
        varchar status
        uuid applied_by
        timestamptz applied_at
        jsonb result
        timestamptz created_at
    }
    
    schema_drift_events {
        uuid id PK
        uuid tenant_id
        uuid dataset_id
        jsonb previous_schema
        jsonb current_schema
        varchar drift_type
        timestamptz detected_at
        jsonb affected_pipelines
    }
```

### Table Definitions

```sql
-- Agent observations (AI diagnosis attempts)
CREATE TABLE agent_observations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    execution_id    UUID NOT NULL,
    observation     JSONB NOT NULL, -- full agent reasoning chain
    root_cause      TEXT,
    confidence      DECIMAL(3,2), -- 0.00 to 1.00
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Healing actions (suggested and applied fixes)
CREATE TABLE healing_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    observation_id  UUID REFERENCES agent_observations(id),
    action_type     VARCHAR(100) NOT NULL, -- schema_fix, retry, config_update
    description     TEXT NOT NULL,
    proposed_change JSONB, -- the actual change
    status          VARCHAR(50) NOT NULL DEFAULT 'proposed', -- proposed, approved, applied, rejected
    applied_by      UUID,
    applied_at      TIMESTAMPTZ,
    result          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Schema drift events
CREATE TABLE schema_drift_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    dataset_id      UUID NOT NULL,
    previous_schema JSONB NOT NULL,
    current_schema  JSONB NOT NULL,
    drift_type      VARCHAR(50) NOT NULL, -- column_added, column_removed, type_changed
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    affected_pipelines JSONB -- list of pipeline IDs
);
```

---

## Multi-Tenancy Pattern

### Row-Level Security (RLS) Implementation

All tables with tenant data implement RLS:

```sql
-- 1. Enable RLS on table
ALTER TABLE pipelines ENABLE ROW LEVEL SECURITY;

-- 2. Create policy
CREATE POLICY tenant_isolation ON pipelines
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- 3. Application sets tenant context at connection start
SET LOCAL app.current_tenant_id = 'tenant-uuid-here';

-- 4. All subsequent queries automatically filtered
SELECT * FROM pipelines; -- Only returns current tenant's pipelines
```

### Connection Setup (Java/Spring)

```java
@Component
public class TenantConnectionInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String tenantId = extractTenantId(request);
        
        // Set tenant context for this request
        TenantContext.setCurrentTenant(tenantId);
        
        return true;
    }
}

@Aspect
@Component
public class RlsAspect {
    
    @Around("execution(* *Repository.*(..))")
    public Object setTenantContext(ProceedingJoinPoint pjp) {
        String tenantId = TenantContext.getCurrentTenant();
        
        // Execute: SET LOCAL app.current_tenant_id = ?
        jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");
        
        return pjp.proceed();
    }
}
```

---

## Indexing Strategy

### Primary Access Patterns

| Query Pattern | Table | Index |
|---------------|-------|-------|
| Pipelines by project | pipelines | `(project_id)` |
| Executions by pipeline | executions | `(pipeline_id, created_at DESC)` |
| Active executions | executions | `(status) WHERE status IN ('pending', 'running')` |
| Jobs by runner | jobs | `(runner_id, status)` |
| Due schedules | schedules | `(next_run_at) WHERE is_active = true` |
| Lineage lookup | lineage_edges | `(source_dataset)`, `(target_dataset)` |
| Audit by resource | audit_logs | `(resource_type, resource_id)` |

### Partial Indexes

```sql
-- Only index active executions (most queries filter by status)
CREATE INDEX idx_executions_active ON executions(tenant_id, created_at)
    WHERE status IN ('pending', 'running');

-- Only index unpublished outbox entries
CREATE INDEX idx_outbox_pending ON outbox(created_at)
    WHERE published_at IS NULL;
```

---

## Interview Questions

**Q: "How do you handle multi-tenancy?"**
> PostgreSQL Row-Level Security. Each table has a `tenant_id` column. RLS policies filter automatically based on `current_setting('app.current_tenant_id')`. Application sets this at connection start.

**Q: "Why event sourcing for pipelines?"**
> Three reasons: (1) Complete audit trail for compliance, (2) Time-travel debugging — "what was this pipeline last week?", (3) Agent Service needs full history to reason about failure patterns.

**Q: "How do you handle schema migrations in production?"**
> Flyway with backward-compatible migrations. Add columns as nullable, populate in batches, then add NOT NULL constraint. Never drop columns in production — mark deprecated, remove in next major version.

**Q: "How do you scale the database?"**
> Read replicas for read-heavy workloads. Table partitioning for large tables (job_logs by date). Connection pooling via PgBouncer. Each service has its own database — no cross-database queries.

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Engineering | Initial schema |
| 1.1 | 2026-05-13 | Engineering | Updated to Mermaid diagrams |
