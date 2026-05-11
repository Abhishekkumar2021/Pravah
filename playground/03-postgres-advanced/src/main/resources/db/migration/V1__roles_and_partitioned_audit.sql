-- ADR-003: logical schema owned by the app role (database-per-service mindset in one DB for the playground).
-- Partitioned time-series table (Pravah audit_events pattern — prune old partitions).

CREATE ROLE app_tenant LOGIN PASSWORD 'playground' NOSUPERUSER INHERIT;

GRANT CONNECT ON DATABASE playground TO app_tenant;
GRANT USAGE, CREATE ON SCHEMA public TO app_tenant;

SET ROLE app_tenant;

CREATE TABLE audit_events (
    id          BIGSERIAL,
    tenant_id   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload     JSONB,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_events_2026_05 PARTITION OF audit_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;

RESET ROLE;
