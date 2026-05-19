-- Runner Service Initial Schema

CREATE TABLE runners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    token_hash VARCHAR(255) NOT NULL,
    max_concurrent_jobs INT NOT NULL DEFAULT 4,
    active_jobs INT NOT NULL DEFAULT 0,
    supported_executors TEXT,
    available_memory_bytes BIGINT NOT NULL DEFAULT 0,
    available_cpus INT NOT NULL DEFAULT 0,
    heartbeat_interval_seconds INT NOT NULL DEFAULT 30,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_metrics_cpu_percent DOUBLE PRECISION DEFAULT 0,
    last_metrics_memory_used_bytes BIGINT DEFAULT 0,
    last_metrics_disk_available_bytes BIGINT DEFAULT 0,
    version_num BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT uq_runners_tenant_name UNIQUE (tenant_id, name)
);

-- Runner labels (key-value pairs for targeting)
CREATE TABLE runner_labels (
    runner_id UUID NOT NULL REFERENCES runners(id) ON DELETE CASCADE,
    label_key VARCHAR(100) NOT NULL,
    label_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (runner_id, label_key)
);

-- Indexes
CREATE INDEX idx_runners_tenant_id ON runners(tenant_id);
CREATE INDEX idx_runners_status ON runners(status);
CREATE INDEX idx_runners_tenant_status ON runners(tenant_id, status);
CREATE INDEX idx_runners_last_heartbeat ON runners(last_heartbeat_at);

-- Row Level Security
ALTER TABLE runners ENABLE ROW LEVEL SECURITY;

CREATE POLICY runners_tenant_isolation ON runners
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE runner_labels ENABLE ROW LEVEL SECURITY;

CREATE POLICY runner_labels_tenant_isolation ON runner_labels
    USING (runner_id IN (SELECT id FROM runners WHERE tenant_id = current_setting('app.tenant_id', true)::uuid));

-- Job assignments (tracking which runner has which job)
CREATE TABLE job_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    runner_id UUID NOT NULL REFERENCES runners(id),
    job_id UUID NOT NULL,
    execution_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT uq_job_assignments_job_id UNIQUE (job_id)
);

CREATE INDEX idx_job_assignments_runner_id ON job_assignments(runner_id);
CREATE INDEX idx_job_assignments_status ON job_assignments(status);
CREATE INDEX idx_job_assignments_execution_id ON job_assignments(execution_id);

-- Comments
COMMENT ON TABLE runners IS 'Registered runner instances that can execute jobs';
COMMENT ON TABLE runner_labels IS 'Labels for runner selection (e.g., env=prod, gpu=true)';
COMMENT ON TABLE job_assignments IS 'Tracks which runner is executing which job';
