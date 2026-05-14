-- Execution DB core tables (docs/lld/02-database-erd.md) — vertical slice: executions, jobs.

CREATE TABLE executions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    pipeline_id      UUID NOT NULL,
    pipeline_version INT NOT NULL,
    status           VARCHAR(50) NOT NULL DEFAULT 'pending',
    trigger_type     VARCHAR(50) NOT NULL,
    triggered_by     UUID,
    parameters       JSONB NOT NULL DEFAULT '{}',
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    error_message    TEXT,
    error_category   VARCHAR(50),
    retry_of         UUID REFERENCES executions(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
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
    artifacts       JSONB,
    metrics         JSONB,

    UNIQUE(execution_id, stage_id, attempt)
);

CREATE INDEX idx_executions_tenant_status ON executions(tenant_id, status);
CREATE INDEX idx_executions_pipeline ON executions(pipeline_id, created_at DESC);
CREATE INDEX idx_executions_created ON executions(created_at DESC);
CREATE INDEX idx_jobs_execution ON jobs(execution_id);

ALTER TABLE executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE executions FORCE ROW LEVEL SECURITY;
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_executions ON executions FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_jobs ON jobs FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM executions e
            WHERE e.id = jobs.execution_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID))
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM executions e
            WHERE e.id = jobs.execution_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID));
