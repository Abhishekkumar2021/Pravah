-- Trigger dispatch history, pending outbox/retry, and coalesce catchup policy (US-03.15)

ALTER TABLE schedules DROP CONSTRAINT IF EXISTS chk_catchup_policy;
ALTER TABLE schedules ADD CONSTRAINT chk_catchup_policy
    CHECK (catchup_policy IN ('skip', 'run_all', 'coalesce'));

CREATE TABLE trigger_dispatch_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    trigger_id      UUID NOT NULL REFERENCES pipeline_triggers(id) ON DELETE CASCADE,
    pipeline_id     UUID NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    execution_id    UUID,
    error_message   TEXT,
    payload         JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trigger_dispatch_history_trigger ON trigger_dispatch_history(trigger_id, created_at DESC);
CREATE INDEX idx_trigger_dispatch_history_tenant ON trigger_dispatch_history(tenant_id);

CREATE TABLE trigger_dispatch_pending (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    trigger_id      UUID NOT NULL REFERENCES pipeline_triggers(id) ON DELETE CASCADE,
    pipeline_id     UUID NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    idempotency_key VARCHAR(512),
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    attempts        INT NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trigger_dispatch_pending_retry ON trigger_dispatch_pending(status, next_retry_at)
    WHERE status IN ('pending', 'failed');

ALTER TABLE trigger_dispatch_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE trigger_dispatch_history FORCE ROW LEVEL SECURITY;
ALTER TABLE trigger_dispatch_pending ENABLE ROW LEVEL SECURITY;
ALTER TABLE trigger_dispatch_pending FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_trigger_dispatch_history ON trigger_dispatch_history FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_trigger_dispatch_pending ON trigger_dispatch_pending FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
