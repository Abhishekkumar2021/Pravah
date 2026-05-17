-- US-02.12: Stage checkpoints for resumable execution (docs/lld/02-database-erd.md)

CREATE TABLE checkpoints (
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    stage_id     VARCHAR(255) NOT NULL,
    state        JSONB NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (execution_id, stage_id)
);

CREATE INDEX idx_checkpoints_execution ON checkpoints(execution_id);

ALTER TABLE checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE checkpoints FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_checkpoints ON checkpoints FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM executions e
            WHERE e.id = checkpoints.execution_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID))
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM executions e
            WHERE e.id = checkpoints.execution_id
              AND e.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID));
