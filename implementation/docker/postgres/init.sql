-- ============================================================================
-- Pravah PostgreSQL Initialization Script
-- Creates databases for each service (database-per-service pattern)
-- ============================================================================

-- Create databases for each service
CREATE DATABASE pravah_tenant;
CREATE DATABASE pravah_pipeline;
CREATE DATABASE pravah_execution;
CREATE DATABASE pravah_scheduler;
CREATE DATABASE pravah_runner;
CREATE DATABASE pravah_notification;
CREATE DATABASE pravah_connect;
CREATE DATABASE pravah_agent;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE pravah_tenant TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_pipeline TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_execution TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_scheduler TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_runner TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_notification TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_connect TO pravah;
GRANT ALL PRIVILEGES ON DATABASE pravah_agent TO pravah;

-- ============================================================================
-- Enable required extensions on all databases
-- ============================================================================

\c pravah_tenant
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_pipeline
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_execution
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_scheduler
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_runner
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_notification
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_connect
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\c pravah_agent
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Return to default database
\c pravah
