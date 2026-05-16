-- V1: Scheduler service schema (US-03.01)
-- @see docs/lld/02-database-erd.md - Scheduler Database

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE schedules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    timezone        VARCHAR(100) NOT NULL DEFAULT 'UTC',
    parameters      JSONB NOT NULL DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT true,
    catchup_policy  VARCHAR(50) NOT NULL DEFAULT 'skip',
    next_run_at     TIMESTAMPTZ,
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_catchup_policy CHECK (catchup_policy IN ('skip', 'run_all'))
);

CREATE TABLE schedule_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    schedule_id     UUID NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    scheduled_time  TIMESTAMPTZ NOT NULL,
    execution_id    UUID,
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scheduler_locks (
    lock_name       VARCHAR(100) PRIMARY KEY,
    holder_id       VARCHAR(255) NOT NULL,
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_schedules_next_run ON schedules(next_run_at) WHERE is_active = true;
CREATE INDEX idx_schedules_tenant ON schedules(tenant_id);
CREATE INDEX idx_schedules_pipeline ON schedules(tenant_id, pipeline_id);
CREATE INDEX idx_schedule_history_schedule ON schedule_history(schedule_id, created_at DESC);
CREATE INDEX idx_schedule_history_tenant ON schedule_history(tenant_id);

ALTER TABLE schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE schedule_history FORCE ROW LEVEL SECURITY;
ALTER TABLE schedules FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_schedules ON schedules FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_schedule_history ON schedule_history FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
