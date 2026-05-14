-- Transactional outbox for execution domain events (ADR-004, docs/lld/04-sequence-diagrams.md §2).

CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    partition_key   VARCHAR(255),
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- Idempotent consumer deduplication (LLD §16: Idempotent Consumer pattern).
-- Stores processed event IDs to prevent duplicate processing on redelivery.
CREATE TABLE processed_events (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partition by processed_at for efficient cleanup of old records
CREATE INDEX idx_processed_events_cleanup ON processed_events(processed_at);
