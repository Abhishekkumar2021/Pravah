-- Local demo data for UI exploration (idempotent).
-- UUIDs match backend/env.local.example (PRAVAH_DEV_*).
--
-- Inserts read-model rows only (no pipeline_events / outbox). Safe for local UI demos;
-- use service APIs when testing Kafka and event sourcing.
--
-- Run: make local-seed  (requires Postgres from make local-up)

\set ON_ERROR_STOP on

-- ============================================================================
-- pravah_tenant
-- ============================================================================
\c pravah_tenant

DELETE FROM project_members WHERE project_id = '33333333-3333-4333-8333-333333333333'::uuid;
DELETE FROM projects WHERE id = '33333333-3333-4333-8333-333333333333'::uuid;
DELETE FROM team_members WHERE team_id = '44444444-4444-4444-8444-444444444444'::uuid;
DELETE FROM teams WHERE id = '44444444-4444-4444-8444-444444444444'::uuid;
DELETE FROM tenant_members WHERE tenant_id = '11111111-1111-4111-8111-111111111111'::uuid;
DELETE FROM users WHERE id = '22222222-2222-4222-8222-222222222222'::uuid;
DELETE FROM tenants WHERE id = '11111111-1111-4111-8111-111111111111'::uuid;

INSERT INTO tenants (id, name, slug, tier, settings, created_at, updated_at, version)
VALUES (
  '11111111-1111-4111-8111-111111111111',
  'Dev Local Tenant',
  'dev-local',
  'TEAM',
  '{}',
  now() - interval '30 days',
  now(),
  0
);

INSERT INTO users (id, tenant_id, email, name, password_hash, status, created_at, updated_at, version)
VALUES (
  '22222222-2222-4222-8222-222222222222',
  '11111111-1111-4111-8111-111111111111',
  'dev@localhost.pravah',
  'Dev User',
  '$2b$12$x9nfJLkH87I6f8/ylBwz8u2IVG5Th/rUkaiJRnOMvXy/pl7eVzZ52',
  'ACTIVE',
  now() - interval '30 days',
  now(),
  0
);

INSERT INTO teams (id, tenant_id, name, description, settings, created_at)
VALUES (
  '44444444-4444-4444-8444-444444444444',
  '11111111-1111-4111-8111-111111111111',
  'Platform',
  'Default team for local development',
  '{}',
  now() - interval '30 days'
);

INSERT INTO tenant_members (tenant_id, user_id, role_id, joined_at)
VALUES (
  '11111111-1111-4111-8111-111111111111',
  '22222222-2222-4222-8222-222222222222',
  '00000000-0000-0000-0000-000000000001',
  now() - interval '30 days'
);

INSERT INTO projects (id, tenant_id, team_id, name, description, settings, created_at)
VALUES (
  '33333333-3333-4333-8333-333333333333',
  '11111111-1111-4111-8111-111111111111',
  '44444444-4444-4444-8444-444444444444',
  'Demo project',
  'Seeded project for local UI (workflows, runs)',
  '{}',
  now() - interval '30 days'
);

INSERT INTO project_members (project_id, user_id, role_id)
VALUES (
  '33333333-3333-4333-8333-333333333333',
  '22222222-2222-4222-8222-222222222222',
  '00000000-0000-0000-0000-000000000001'
);

-- ============================================================================
-- pravah_pipeline
-- ============================================================================
\c pravah_pipeline

DELETE FROM pipeline_versions WHERE pipeline_id IN (
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'
);
DELETE FROM pipeline_events WHERE pipeline_id IN (
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003'
);
DELETE FROM pipelines WHERE tenant_id = '11111111-1111-4111-8111-111111111111'::uuid;

INSERT INTO pipelines (
  id, tenant_id, project_id, name, description, current_version, status, version,
  created_at, updated_at, created_by
) VALUES
  (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
    '11111111-1111-4111-8111-111111111111',
    '33333333-3333-4333-8333-333333333333',
    'Daily ETL',
    'Extract, transform, and load warehouse tables',
    1,
    'active',
    0,
    now() - interval '14 days',
    now() - interval '1 day',
    '22222222-2222-4222-8222-222222222222'
  ),
  (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
    '11111111-1111-4111-8111-111111111111',
    '33333333-3333-4333-8333-333333333333',
    'Event ingestion',
    'Stream events into the lake',
    1,
    'active',
    0,
    now() - interval '10 days',
    now() - interval '2 hours',
    '22222222-2222-4222-8222-222222222222'
  ),
  (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0003',
    '11111111-1111-4111-8111-111111111111',
    '33333333-3333-4333-8333-333333333333',
    'Weekly reports',
    'Draft pipeline — not runnable yet',
    0,
    'draft',
    0,
    now() - interval '3 days',
    now() - interval '3 days',
    '22222222-2222-4222-8222-222222222222'
  );

INSERT INTO pipeline_versions (id, pipeline_id, version, definition, published_at, published_by)
VALUES
  (
    'dddddddd-dddd-4ddd-8ddd-dddddddddd01',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
    1,
    '{"stages":[{"id":"extract","name":"Extract data","type":"sql"},{"id":"transform","name":"Transform","type":"transform"},{"id":"load","name":"Load to warehouse","type":"sql"}]}'::jsonb,
    now() - interval '7 days',
    '22222222-2222-4222-8222-222222222222'
  ),
  (
    'dddddddd-dddd-4ddd-8ddd-dddddddddd02',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
    1,
    '{"stages":[{"id":"ingest","name":"Ingest stream","type":"kafka"},{"id":"dedupe","name":"Deduplicate","type":"transform"},{"id":"publish","name":"Publish metrics","type":"sql"}]}'::jsonb,
    now() - interval '5 days',
    '22222222-2222-4222-8222-222222222222'
  );

-- ============================================================================
-- pravah_execution
-- ============================================================================
\c pravah_execution

-- RLS on job_logs requires tenant context when not connecting as superuser.
SELECT set_config('pravah.current_tenant_id', '11111111-1111-4111-8111-111111111111', true);

DELETE FROM job_logs WHERE job_id IN (
  SELECT id FROM jobs WHERE execution_id IN (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0005'
  )
);
DELETE FROM jobs WHERE execution_id IN (
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0005'
);
DELETE FROM executions WHERE tenant_id = '11111111-1111-4111-8111-111111111111'::uuid;

INSERT INTO executions (
  id, tenant_id, pipeline_id, pipeline_version, status, trigger_type, triggered_by,
  parameters, definition_snapshot, started_at, completed_at, error_message, created_at, version
) VALUES
  (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
    1,
    'FAILED',
    'manual',
    '22222222-2222-4222-8222-222222222222',
    '{}',
    '{"stages":[{"id":"extract","name":"Extract data"},{"id":"transform","name":"Transform"},{"id":"load","name":"Load to warehouse"}]}'::jsonb,
    now() - interval '2 hours',
    now() - interval '1 hour 40 minutes',
    'Stage transform failed after 2 attempts',
    now() - interval '2 hours 5 minutes',
    0
  ),
  (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
    1,
    'RUNNING',
    'manual',
    '22222222-2222-4222-8222-222222222222',
    '{}',
    '{"stages":[{"id":"extract","name":"Extract data"},{"id":"transform","name":"Transform"},{"id":"load","name":"Load to warehouse"}]}'::jsonb,
    now() - interval '12 minutes',
    NULL,
    NULL,
    now() - interval '15 minutes',
    0
  ),
  (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
    1,
    'SUCCEEDED',
    'schedule',
    NULL,
    '{}',
    '{"stages":[{"id":"ingest","name":"Ingest stream"},{"id":"dedupe","name":"Deduplicate"},{"id":"publish","name":"Publish metrics"}]}'::jsonb,
    now() - interval '1 day',
    now() - interval '23 hours',
    NULL,
    now() - interval '1 day',
    0
  ),
  (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001',
    1,
    'PENDING',
    'manual',
    '22222222-2222-4222-8222-222222222222',
    '{}',
    '{"stages":[{"id":"extract","name":"Extract data"},{"id":"transform","name":"Transform"},{"id":"load","name":"Load to warehouse"}]}'::jsonb,
    NULL,
    NULL,
    NULL,
    now() - interval '5 minutes',
    0
  ),
  (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0005',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0002',
    1,
    'CANCELLED',
    'manual',
    '22222222-2222-4222-8222-222222222222',
    '{}',
    '{"stages":[{"id":"ingest","name":"Ingest stream"},{"id":"dedupe","name":"Deduplicate"}]}'::jsonb,
    now() - interval '3 hours',
    now() - interval '2 hours 50 minutes',
    NULL,
    now() - interval '3 hours',
    0
  );

INSERT INTO jobs (
  id, execution_id, stage_id, stage_name, status, attempt,
  queued_at, started_at, completed_at, error_message, version
) VALUES
  -- Failed ETL run (good for run-detail stages tab)
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001', 'extract', 'Extract data', 'SUCCEEDED', 1,
   now() - interval '2 hours', now() - interval '115 minutes', now() - interval '100 minutes', NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0002', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001', 'transform', 'Transform', 'FAILED', 2,
   now() - interval '100 minutes', now() - interval '95 minutes', now() - interval '100 minutes', 'Null key in row 1842', 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0003', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0001', 'load', 'Load to warehouse', 'CANCELLED', 1,
   NULL, NULL, NULL, NULL, 0),
  -- Running ETL run
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0004', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002', 'extract', 'Extract data', 'SUCCEEDED', 1,
   now() - interval '14 minutes', now() - interval '13 minutes', now() - interval '10 minutes', NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0005', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002', 'transform', 'Transform', 'RUNNING', 1,
   now() - interval '10 minutes', now() - interval '8 minutes', NULL, NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0006', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0002', 'load', 'Load to warehouse', 'PENDING', 1,
   NULL, NULL, NULL, NULL, 0),
  -- Succeeded event pipeline
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0007', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003', 'ingest', 'Ingest stream', 'SUCCEEDED', 1,
   now() - interval '1 day', now() - interval '1 day', now() - interval '23 hours 30 minutes', NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0008', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003', 'dedupe', 'Deduplicate', 'SUCCEEDED', 1,
   now() - interval '23 hours 30 minutes', now() - interval '23 hours 20 minutes', now() - interval '23 hours', NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0009', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0003', 'publish', 'Publish metrics', 'SUCCEEDED', 1,
   now() - interval '23 hours', now() - interval '23 hours', now() - interval '23 hours', NULL, 0),
  -- Pending run (single stage queued)
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0010', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004', 'extract', 'Extract data', 'PENDING', 1,
   NULL, NULL, NULL, NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0011', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004', 'transform', 'Transform', 'PENDING', 1,
   NULL, NULL, NULL, NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0012', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0004', 'load', 'Load to warehouse', 'PENDING', 1,
   NULL, NULL, NULL, NULL, 0),
  -- Cancelled run
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0013', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0005', 'ingest', 'Ingest stream', 'SUCCEEDED', 1,
   now() - interval '3 hours', now() - interval '175 minutes', now() - interval '170 minutes', NULL, 0),
  ('cccccccc-cccc-4ccc-8ccc-cccccccc0014', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb0005', 'dedupe', 'Deduplicate', 'CANCELLED', 1,
   now() - interval '170 minutes', now() - interval '168 minutes', now() - interval '170 minutes', NULL, 0);

-- Per-job logs for run-detail UI (US-02.03). Mirrors embedded executor + processing messages.
INSERT INTO job_logs (id, job_id, log_time, level, message) VALUES
  -- Failed ETL: extract (succeeded)
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0001', 'cccccccc-cccc-4ccc-8ccc-cccccccc0001', now() - interval '115 minutes', 'INFO',
   'Starting stage Extract data (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0002', 'cccccccc-cccc-4ccc-8ccc-cccccccc0001', now() - interval '114 minutes', 'INFO',
   '[embedded-echo] Executing stage extract'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0003', 'cccccccc-cccc-4ccc-8ccc-cccccccc0001', now() - interval '113 minutes', 'INFO',
   'Read 128,450 rows from source table public.orders'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0004', 'cccccccc-cccc-4ccc-8ccc-cccccccc0001', now() - interval '101 minutes', 'INFO',
   'Stage Extract data completed successfully'),
  -- Failed ETL: transform (failed after retry)
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0011', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '99 minutes', 'INFO',
   'Starting stage Transform (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0012', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '98 minutes', 'INFO',
   '[embedded-echo] Executing stage transform'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0013', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '97 minutes', 'ERROR',
   'Stage Transform failed with exit code 1'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0014', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '96 minutes', 'WARN',
   'Scheduling retry (attempt 2 of 3)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0015', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '95 minutes', 'INFO',
   'Starting stage Transform (attempt 2)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0016', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '94 minutes', 'INFO',
   '[embedded-echo] Executing stage transform'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0017', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '92 minutes', 'WARN',
   'Null key in column customer_id at row 1842'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0018', 'cccccccc-cccc-4ccc-8ccc-cccccccc0002', now() - interval '91 minutes', 'ERROR',
   'Stage Transform failed with exit code 1'),
  -- Running ETL: extract (succeeded)
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0021', 'cccccccc-cccc-4ccc-8ccc-cccccccc0004', now() - interval '13 minutes', 'INFO',
   'Starting stage Extract data (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0022', 'cccccccc-cccc-4ccc-8ccc-cccccccc0004', now() - interval '12 minutes', 'INFO',
   '[embedded-echo] Executing stage extract'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0023', 'cccccccc-cccc-4ccc-8ccc-cccccccc0004', now() - interval '11 minutes', 'INFO',
   'Stage Extract data completed successfully'),
  -- Running ETL: transform (in progress — good for log polling demo)
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0031', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '8 minutes', 'INFO',
   'Starting stage Transform (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0032', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '7 minutes', 'INFO',
   '[embedded-echo] Executing stage transform'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0033', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '6 minutes', 'INFO',
   'Normalizing 128,450 rows across 4 partitions'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0034', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '4 minutes', 'INFO',
   'Partition 1/4 complete (32,112 rows)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0035', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '2 minutes', 'INFO',
   'Partition 2/4 complete (32,118 rows)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0036', 'cccccccc-cccc-4ccc-8ccc-cccccccc0005', now() - interval '45 seconds', 'INFO',
   'Partition 3/4 in progress…'),
  -- Succeeded event pipeline
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0041', 'cccccccc-cccc-4ccc-8ccc-cccccccc0007', now() - interval '1 day', 'INFO',
   'Starting stage Ingest stream (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0042', 'cccccccc-cccc-4ccc-8ccc-cccccccc0007', now() - interval '1 day' + interval '5 minutes', 'INFO',
   '[embedded-echo] Executing stage ingest'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0043', 'cccccccc-cccc-4ccc-8ccc-cccccccc0007', now() - interval '23 hours 45 minutes', 'INFO',
   'Stage Ingest stream completed successfully'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0044', 'cccccccc-cccc-4ccc-8ccc-cccccccc0008', now() - interval '23 hours 25 minutes', 'INFO',
   'Starting stage Deduplicate (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0045', 'cccccccc-cccc-4ccc-8ccc-cccccccc0008', now() - interval '23 hours 20 minutes', 'INFO',
   '[embedded-echo] Executing stage dedupe'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0046', 'cccccccc-cccc-4ccc-8ccc-cccccccc0008', now() - interval '23 hours 5 minutes', 'INFO',
   'Removed 1,204 duplicate event keys'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0047', 'cccccccc-cccc-4ccc-8ccc-cccccccc0008', now() - interval '23 hours', 'INFO',
   'Stage Deduplicate completed successfully'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0048', 'cccccccc-cccc-4ccc-8ccc-cccccccc0009', now() - interval '23 hours', 'INFO',
   'Starting stage Publish metrics (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0049', 'cccccccc-cccc-4ccc-8ccc-cccccccc0009', now() - interval '23 hours', 'INFO',
   '[embedded-echo] Executing stage publish'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0050', 'cccccccc-cccc-4ccc-8ccc-cccccccc0009', now() - interval '23 hours', 'INFO',
   'Stage Publish metrics completed successfully'),
  -- Cancelled run: ingest done, dedupe cancelled mid-flight
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0051', 'cccccccc-cccc-4ccc-8ccc-cccccccc0013', now() - interval '175 minutes', 'INFO',
   'Starting stage Ingest stream (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0052', 'cccccccc-cccc-4ccc-8ccc-cccccccc0013', now() - interval '174 minutes', 'INFO',
   '[embedded-echo] Executing stage ingest'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0053', 'cccccccc-cccc-4ccc-8ccc-cccccccc0013', now() - interval '171 minutes', 'INFO',
   'Stage Ingest stream completed successfully'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0054', 'cccccccc-cccc-4ccc-8ccc-cccccccc0014', now() - interval '168 minutes', 'INFO',
   'Starting stage Deduplicate (attempt 1)'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0055', 'cccccccc-cccc-4ccc-8ccc-cccccccc0014', now() - interval '167 minutes', 'INFO',
   '[embedded-echo] Executing stage dedupe'),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeee0056', 'cccccccc-cccc-4ccc-8ccc-cccccccc0014', now() - interval '170 minutes', 'WARN',
   'Run cancelled by user; stage Deduplicate aborted');
