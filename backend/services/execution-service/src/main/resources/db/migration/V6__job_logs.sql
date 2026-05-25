-- Job logs (docs/lld/02-database-erd.md). Alpha: non-partitioned table; monthly partitions later.

CREATE TABLE job_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    log_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    level       VARCHAR(10) NOT NULL,
    message     TEXT NOT NULL,
    attributes  JSONB
);

CREATE INDEX idx_job_logs_job_time ON job_logs(job_id, log_time);

ALTER TABLE job_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_job_logs ON job_logs
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN executions e ON e.id = j.execution_id
            WHERE j.id = job_logs.job_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID))
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM jobs j
            JOIN executions e ON e.id = j.execution_id
            WHERE j.id = job_logs.job_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID));

COMMENT ON TABLE job_logs IS 'Stdout/stderr and executor messages per job (US-02.03)';
