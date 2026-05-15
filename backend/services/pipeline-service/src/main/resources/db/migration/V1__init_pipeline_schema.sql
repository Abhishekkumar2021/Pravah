-- Pipeline DB core tables (docs/lld/02-database-erd.md) — vertical slice: events, read model, versions, outbox.

CREATE TABLE pipeline_events (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    event_version   INT NOT NULL,
    payload         JSONB NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(pipeline_id, event_version)
);

CREATE TABLE pipelines (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    project_id      UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    current_version INT NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL DEFAULT 'draft',
    version         INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE pipeline_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    version         INT NOT NULL,
    definition      JSONB NOT NULL,
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by    UUID NOT NULL,
    UNIQUE(pipeline_id, version)
);

CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_pipeline_events_pipeline ON pipeline_events(pipeline_id, event_version);
CREATE INDEX idx_pipeline_events_tenant ON pipeline_events(tenant_id, created_at DESC);
CREATE INDEX idx_pipelines_project ON pipelines(project_id);
CREATE INDEX idx_pipelines_tenant_status ON pipelines(tenant_id, status);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

ALTER TABLE pipelines ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipelines FORCE ROW LEVEL SECURITY;
ALTER TABLE pipeline_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_events FORCE ROW LEVEL SECURITY;
ALTER TABLE pipeline_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_versions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_pipelines ON pipelines FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_events ON pipeline_events FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);

CREATE POLICY tenant_isolation_versions ON pipeline_versions FOR ALL
    USING (EXISTS (
        SELECT 1 FROM pipelines p
        WHERE p.id = pipeline_versions.pipeline_id
          AND p.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID))
    WITH CHECK (EXISTS (
        SELECT 1 FROM pipelines p
        WHERE p.id = pipeline_versions.pipeline_id
          AND p.tenant_id = current_setting('pravah.current_tenant_id', true)::UUID));
