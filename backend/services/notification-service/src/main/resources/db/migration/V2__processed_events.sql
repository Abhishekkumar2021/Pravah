-- Idempotent Kafka consumer deduplication (LLD §16, ADR-004)
CREATE TABLE processed_events (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_cleanup ON processed_events(processed_at);
