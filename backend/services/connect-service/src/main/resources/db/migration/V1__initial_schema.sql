-- Connect Service Initial Schema
-- Stores configured connections to external data sources and destinations

CREATE TABLE connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    connector_id VARCHAR(100) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    last_tested_at TIMESTAMP WITH TIME ZONE,
    last_test_success BOOLEAN,
    last_test_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_connections_tenant_name UNIQUE (tenant_id, name)
);

-- Indexes
CREATE INDEX idx_connections_tenant_id ON connections(tenant_id);
CREATE INDEX idx_connections_connector_id ON connections(connector_id);
CREATE INDEX idx_connections_status ON connections(status);
CREATE INDEX idx_connections_tenant_connector ON connections(tenant_id, connector_id);

-- Row Level Security
ALTER TABLE connections ENABLE ROW LEVEL SECURITY;

CREATE POLICY connections_tenant_isolation ON connections
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Sync history table for tracking sync jobs
CREATE TABLE sync_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    stream_name VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    records_read BIGINT DEFAULT 0,
    records_written BIGINT DEFAULT 0,
    bytes_processed BIGINT DEFAULT 0,
    error_message TEXT,
    cursor_state JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_sync_jobs_connection FOREIGN KEY (connection_id) REFERENCES connections(id)
);

CREATE INDEX idx_sync_jobs_tenant_id ON sync_jobs(tenant_id);
CREATE INDEX idx_sync_jobs_connection_id ON sync_jobs(connection_id);
CREATE INDEX idx_sync_jobs_status ON sync_jobs(status);
CREATE INDEX idx_sync_jobs_created_at ON sync_jobs(created_at DESC);

ALTER TABLE sync_jobs ENABLE ROW LEVEL SECURITY;

CREATE POLICY sync_jobs_tenant_isolation ON sync_jobs
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Comments
COMMENT ON TABLE connections IS 'Configured connections to external data sources and destinations';
COMMENT ON TABLE sync_jobs IS 'History and state of data synchronization jobs';
COMMENT ON COLUMN connections.config IS 'JSON configuration (credentials encrypted at rest)';
COMMENT ON COLUMN sync_jobs.cursor_state IS 'Incremental sync state for resumption';
