-- Outbox table per ADR-004
CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id  VARCHAR(255) NOT NULL,
    event_type    VARCHAR(255) NOT NULL,
    payload       JSONB        NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ,
    retry_count   INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at)
    WHERE published_at IS NULL;

-- Demo "orders" table to simulate a business entity
CREATE TABLE orders (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product    VARCHAR(255) NOT NULL,
    quantity   INT          NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
