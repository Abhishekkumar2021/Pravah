-- Add definition_snapshot column to store pipeline definition at execution time.
-- This is used for DAG scheduling after job completion (determining which downstream
-- stages are ready to queue). Stored separately from parameters to keep user-provided
-- runtime parameters clean.

ALTER TABLE executions ADD COLUMN definition_snapshot JSONB;

COMMENT ON COLUMN executions.definition_snapshot IS
    'Pipeline definition JSON at execution creation time; used for DAG scheduling';
