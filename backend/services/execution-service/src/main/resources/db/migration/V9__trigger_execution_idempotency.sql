-- V9: Idempotent event trigger deduplication (scheduler Kafka / webhook retries)

CREATE TABLE IF NOT EXISTS trigger_execution_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL,
    execution_id UUID NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_trigger_idempotency_execution
    ON trigger_execution_idempotency(execution_id);

COMMENT ON TABLE trigger_execution_idempotency IS
    'Maps trigger idempotency keys to executions for at-least-once Kafka delivery';
