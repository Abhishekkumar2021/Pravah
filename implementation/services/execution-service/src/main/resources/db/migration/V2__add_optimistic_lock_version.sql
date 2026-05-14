-- Hibernate @Version optimistic locking (aligns with ExecutionEntity / JobEntity).

ALTER TABLE executions ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
