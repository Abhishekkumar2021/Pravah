# Value Resolution System

> **Status**: Implemented  
> **Related**: US-01.05 (Variables), US-02.10 (Stage Data Passing), US-02.14 (SQL Connections), US-02.17 (Container Stages)

## Overview

Pravah uses a **unified value resolution system** to handle variables, secrets, environment configs, stage outputs, and credential references throughout pipeline definitions and connections.

## Reference Types

| Syntax | Description | Resolution Time | Example |
|--------|-------------|-----------------|---------|
| `${var.name}` | Pipeline variable | Execution start | `${var.batch_size}` |
| `${secret.name}` | Tenant secret reference | Stage execution | `${secret.api_key}` |
| `${stages.stageId.output.key}` | Upstream stage output | Stage execution | `${stages.extract.output.row_count}` |
| `${execution_date}` | Built-in variable | Execution start | `2026-05-17` |
| `env:VAR_NAME` | Environment variable | Stage execution | `env:PRAVAH_DB_PASSWORD` |
| `vault:path#key` | HashiCorp Vault (future) | Stage execution | `vault:secret/db#password` |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Pipeline Definition                           │
│  variables:                                                          │
│    batch_size: { type: number, default: 100 }                       │
│  environments:                                                       │
│    prod: { batch_size: 1000 }                                       │
│  stages:                                                             │
│    - config:                                                         │
│        query: "SELECT * FROM t LIMIT ${var.batch_size}"             │
│        api_key: "${secret.external_api}"                            │
│        connection: warehouse                                         │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Resolution Pipeline                               │
│                                                                      │
│  1. PARSE PHASE (Pipeline Publish)                                  │
│     ├── Validate variable declarations                              │
│     ├── Validate secret references exist (tenant_secrets table)     │
│     └── Validate connection references exist                        │
│                                                                      │
│  2. RESOLVE PHASE (Execution Start)                                 │
│     ├── Apply variable defaults                                     │
│     ├── Apply environment overrides                                 │
│     ├── Apply runtime parameters                                    │
│     ├── Substitute ${var.*} references                              │
│     └── Store resolved definition_snapshot                          │
│                                                                      │
│  3. EXECUTION PHASE (Stage Run)                                     │
│     ├── Resolve ${secret.*} → actual values                         │
│     ├── Resolve connection credentials (env:/vault:)                │
│     └── Execute stage with fully resolved config                    │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Design

### ValueReference (Sealed Interface)

```java
public sealed interface ValueReference
    permits VariableRef, SecretRef, BuiltinRef, EnvRef, VaultRef, StageOutputRef, LiteralValue {
  
  String raw();           // Original string representation
  boolean isDeferred();   // true if resolved at execution time vs parse time
}

public record VariableRef(String name) implements ValueReference {
  public String raw() { return "${var." + name + "}"; }
  public boolean isDeferred() { return false; } // resolved at execution start
}

public record SecretRef(String name) implements ValueReference {
  public String raw() { return "${secret." + name + "}"; }
  public boolean isDeferred() { return true; } // resolved at stage execution
}

public record StageOutputRef(String stageId, String outputPath) implements ValueReference {
  public String raw() { return "${stages." + stageId + ".output." + outputPath + "}"; }
  public boolean isDeferred() { return true; } // resolved at stage execution
}

public record EnvRef(String varName) implements ValueReference {
  public String raw() { return "env:" + varName; }
  public boolean isDeferred() { return true; }
}
```

### ValueReferenceParser

```java
public final class ValueReferenceParser {
  
  // Patterns (all linear - no backtracking for ReDoS safety)
  private static final Pattern VAR_REF = Pattern.compile("\\$\\{var\\.([a-zA-Z_][a-zA-Z0-9_]*)}");
  private static final Pattern SECRET_REF = Pattern.compile("\\$\\{secret\\.([a-zA-Z_][a-zA-Z0-9_]*)}");
  private static final Pattern BUILTIN_REF = Pattern.compile("\\$\\{(execution_date|execution_id|pipeline_id|pipeline_version)}");
  private static final Pattern ENV_REF = Pattern.compile("^env:([A-Za-z_][A-Za-z0-9_]*)$");
  private static final Pattern VAULT_REF = Pattern.compile("^vault:([^#]+)#([a-zA-Z_][a-zA-Z0-9_]*)$");
  
  public static List<ValueReference> extractAll(String value) { ... }
  public static ValueReference parse(String value) { ... }
}
```

### ValueResolver (Provider Pattern)

```java
public interface ValueResolverProvider {
  boolean supports(ValueReference ref);
  Object resolve(ValueReference ref, ResolutionContext ctx);
}

public record ResolutionContext(
    UUID tenantId,
    UUID executionId,
    Map<String, Object> variableContext,
    Map<String, Object> secretsCache  // avoid repeated lookups
) {}

// Implementations
@Component public class VariableResolverProvider implements ValueResolverProvider { ... }
@Component public class SecretResolverProvider implements ValueResolverProvider { ... }
@Component public class StageOutputResolverProvider implements ValueResolverProvider { ... }
@Component public class EnvResolverProvider implements ValueResolverProvider { ... }
@Component public class VaultResolverProvider implements ValueResolverProvider { ... } // future
```

### UnifiedValueResolver

```java
@Component
public class UnifiedValueResolver {
  
  private final List<ValueResolverProvider> providers;
  
  public Object resolve(String rawValue, ResolutionContext ctx) {
    List<ValueReference> refs = ValueReferenceParser.extractAll(rawValue);
    if (refs.isEmpty()) {
      return rawValue; // literal
    }
    // If single reference covering entire string, return typed value
    // If embedded references, substitute into string
    ...
  }
  
  public Map<String, Object> resolveConfig(Map<String, Object> config, ResolutionContext ctx) {
    // Deep recursive resolution
  }
}
```

## Database Schema

### tenant_secrets Table

```sql
CREATE TABLE tenant_secrets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,  -- reference name: ${secret.name}
    provider        VARCHAR(50) NOT NULL,   -- 'env', 'vault', 'aws_sm', etc.
    provider_path   VARCHAR(500) NOT NULL,  -- provider-specific path
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    UNIQUE(tenant_id, name)
);

-- RLS for tenant isolation
ALTER TABLE tenant_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_secrets FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_secrets ON tenant_secrets FOR ALL
    USING (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('pravah.current_tenant_id', true)::UUID);
```

### Example Secret Records

```sql
-- Environment variable (local dev)
INSERT INTO tenant_secrets (tenant_id, name, provider, provider_path, created_by)
VALUES ('...', 'db_password', 'env', 'PRAVAH_DB_PASSWORD', '...');

-- HashiCorp Vault (production)
INSERT INTO tenant_secrets (tenant_id, name, provider, provider_path, created_by)
VALUES ('...', 'api_key', 'vault', 'secret/data/myapp#api_key', '...');

-- AWS Secrets Manager (future)
INSERT INTO tenant_secrets (tenant_id, name, provider, provider_path, created_by)
VALUES ('...', 'stripe_key', 'aws_sm', 'arn:aws:secretsmanager:...:secret:stripe', '...');
```

## Resolution Flow

### Phase 1: Pipeline Publish Validation

```
1. Parse YAML definition
2. Extract all ${var.*} references → validate declared in variables block
3. Extract all ${secret.*} references → validate exist in tenant_secrets
4. Extract all connection references → validate exist in connections table
5. Store validated definition in pipeline_versions
```

### Phase 2: Execution Start Resolution

```
1. Load pipeline definition from pipeline_versions
2. Build variable context:
   a. Apply defaults from variables block
   b. Apply environment overrides (if environment parameter provided)
   c. Apply runtime parameters
3. Substitute all ${var.*} and ${builtin} references
4. Store resolved definition_snapshot in executions table
5. NOTE: ${secret.*} references remain as-is (deferred)
```

### Phase 3: Stage Execution Resolution

```
1. Load stage config from definition_snapshot
2. For each ${stages.*.output.*} reference:
   a. Query completed upstream job by stageId
   b. Extract value from job.output using path notation
   c. Substitute into config
3. For each ${secret.*} reference:
   a. Look up tenant_secrets by name
   b. Resolve via appropriate provider (env/vault/aws)
   c. Substitute into config
4. For connection credentials:
   a. Load connection config
   b. Resolve credentials.password via provider
5. Execute stage with fully resolved config
```

## Connection Credentials

Connections store credential references in `config.credentials`:

```yaml
# Connection definition (stored in connections table)
name: warehouse
type: postgres
config:
  host: localhost
  port: 5432
  database: pravah
  username: pravah
  credentials:
    password: env:PRAVAH_DB_PASSWORD  # Provider reference
```

At execution time, `credentials.password` is resolved using the same provider pattern:
- `env:VAR` → EnvResolverProvider
- `vault:path#key` → VaultResolverProvider
- `${secret.name}` → SecretResolverProvider (looks up tenant_secrets)

## Security Considerations

1. **Secrets never stored in definition_snapshot** — Only references like `${secret.api_key}` are stored; actual values resolved at execution time only

2. **Tenant isolation** — All secret lookups include tenant_id filter

3. **Audit trail** — Secret access logged with execution context

4. **No secrets in logs** — Resolved values never logged; only reference names

5. **Validation at publish** — Missing secrets caught early, not at execution time

## Internal Service-to-Service API Security

For execution-time secret resolution, the Execution Service calls the Pipeline Service's internal API. This endpoint has a different security model than public APIs:

### Internal Secret Resolution Endpoint

```
POST /internal/secrets/resolve
Headers:
  X-Pravah-Internal-Secret: <shared_secret>
  X-Tenant-ID: <tenant_uuid>
Body:
  { "secretName": "api_key" }
Response:
  { "value": "resolved_secret_value" }
```

### Security Model

| Aspect | Implementation |
|--------|----------------|
| Authentication | `X-Pravah-Internal-Secret` header (shared secret from config) |
| Authorization | Service-to-service only; tenant ID passed via header and trusted |
| Network exposure | **Never expose externally** — internal network only |
| Why not JWT | Internal services don't represent users; shared secret is simpler and sufficient |

### Configuration

```yaml
# pipeline-service application.yml
pravah:
  internal:
    secret: ${PRAVAH_INTERNAL_SECRET}

# execution-service application.yml
pravah:
  pipeline-service:
    internal-secret: ${PRAVAH_INTERNAL_SECRET}
```

### Container Stage Network Isolation

Container stages run with `--network none` by default to prevent containers from:
- Accessing the internal secret resolution API
- Querying cloud metadata endpoints (169.254.169.254)
- Scanning internal network for services

If a container stage genuinely needs network access (e.g., to fetch data from external APIs), this must be explicitly configured in a future story with allowlist-based egress rules.

## API Endpoints

### Secrets Management

```
POST   /api/v1/secrets              Create secret reference
GET    /api/v1/secrets              List secret references (names only, no values)
GET    /api/v1/secrets/{id}         Get secret reference details
PUT    /api/v1/secrets/{id}         Update secret reference
DELETE /api/v1/secrets/{id}         Delete secret reference
POST   /api/v1/secrets/{id}/test    Test secret resolution (returns success/failure, not value)
```

## Example Pipeline

```yaml
variables:
  batch_size:
    type: number
    default: 100
  target_table:
    type: string
    required: true

environments:
  prod:
    batch_size: 10000
  staging:
    batch_size: 1000

stages:
  - id: extract
    type: sql
    config:
      connection: warehouse
      query: |
        SELECT * FROM orders 
        WHERE created_at >= '${execution_date}'
        LIMIT ${var.batch_size}
  
  - id: transform
    type: sql
    dependsOn: [extract]
    config:
      connection: warehouse
      query: |
        -- Use row count from extract stage
        SELECT '${stages.extract.output.row_count}' AS extracted_rows,
               '${stages.extract.output.columns}' AS columns
  
  - id: notify
    dependsOn: [extract, transform]
    config:
      message: "Extracted ${stages.extract.output.row_count} rows from ${var.target_table}"

  - id: run-dbt
    type: container
    dependsOn: [transform]
    config:
      image: ghcr.io/org/dbt-runner:1.2.3
      command: ["dbt", "run", "--select", "${var.target_table}"]
      env:
        DBT_PROFILE: prod
        SNOWFLAKE_PASSWORD: "${secret.snowflake_password}"
        EXTRACTED_ROWS: "${stages.extract.output.row_count}"
      resources:
        memory: 2Gi
        cpus: "1.0"
```

## Stage Output Resolution (US-02.10)

Downstream stages can access outputs from completed upstream stages using the syntax:

```
${stages.<stageId>.output.<path>}
```

### Supported Output Keys

Each stage executor produces specific output keys:

| Executor | Output Keys |
|----------|-------------|
| SQL | `row_count`, `columns`, `preview`, `query_type`, `duration_ms` |
| Container | `exit_code`, `duration_ms`, `timed_out`, `image`, `container_name` |
| Echo | `executor`, `message` |

### Nested Path Access

Use dot notation for nested values:

```yaml
# Access first preview row's 'id' column
message: "First ID: ${stages.query.output.preview.0.id}"

# Access metadata
message: "Query took ${stages.extract.output.duration_ms}ms"
```

### Resolution Rules

1. **Dependency requirement**: The referenced stage must be in `dependsOn` (directly or transitively)
2. **State requirement**: The upstream stage must have `SUCCEEDED` status
3. **Caching**: Resolved outputs are cached within the same stage resolution context
4. **Error handling**: Missing keys or failed stages throw `IllegalStateException`

### Size Limits

- Maximum stage output: 1MB (configurable via `pravah.stage.max-output-bytes`)
- Preview rows: Limited to 10 rows in SQL executor
- Warning logged if output exceeds 100KB

## Migration Path

1. **Existing connections** — Already use `env:` prefix; no migration needed
2. **New secrets** — Create `tenant_secrets` table; add CRUD API
3. **Variable system** — Already implemented; extend substitutor for `${secret.*}`
4. **Documentation** — Update ERD, sequence diagrams
