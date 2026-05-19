-- V4: Kafka trigger deduplication table for idempotent processing
-- Prevents duplicate pipeline runs when Kafka consumer reprocesses the same message

CREATE TABLE kafka_trigger_processed (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trigger_id      UUID NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    partition_num   INTEGER NOT NULL,
    offset_num      BIGINT NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_kafka_trigger_offset UNIQUE (trigger_id, topic, partition_num, offset_num)
);

CREATE INDEX idx_kafka_trigger_processed_cleanup ON kafka_trigger_processed(processed_at);

COMMENT ON TABLE kafka_trigger_processed IS 'Idempotent consumer deduplication for Kafka triggers (US-03.06)';
