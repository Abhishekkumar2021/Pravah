-- V3: Event triggers — webhook (US-03.07) and Kafka (US-03.06)
-- @see docs/lld/02-database-erd.md - event_triggers, webhooks

CREATE TABLE pipeline_triggers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    pipeline_id     UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    trigger_type    VARCHAR(50) NOT NULL,
  -- webhook: {"rateLimitPerMinute": 60}
  -- kafka: {"topic": "orders", "filter": {"eventType": "created"}}
    config          JSONB NOT NULL DEFAULT '{}',
    secret_hash     VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT true,
    last_triggered_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_pipeline_trigger_type CHECK (trigger_type IN ('webhook', 'kafka')),
    CONSTRAINT uq_pipeline_trigger_name UNIQUE (tenant_id, pipeline_id, name)
);

CREATE INDEX idx_pipeline_triggers_tenant ON pipeline_triggers(tenant_id);
CREATE INDEX idx_pipeline_triggers_pipeline ON pipeline_triggers(tenant_id, pipeline_id);
CREATE INDEX idx_pipeline_triggers_kafka ON pipeline_triggers(trigger_type, enabled)
    WHERE trigger_type = 'kafka' AND enabled = true;

ALTER TABLE pipeline_triggers ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_triggers FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_pipeline_triggers ON pipeline_triggers FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY pipeline_trigger_webhook_lookup ON pipeline_triggers FOR SELECT
    USING (current_setting('pravah.webhook_lookup', true) = 'true');

CREATE POLICY pipeline_trigger_kafka_consumer ON pipeline_triggers FOR SELECT
    USING (current_setting('pravah.kafka_trigger_consumer', true) = 'true');
