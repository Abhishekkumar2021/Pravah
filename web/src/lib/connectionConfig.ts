/** Helpers for pipeline connection config — US-12.16 */

export type ConnectionType =
  | "postgres"
  | "mysql"
  | "mssql"
  | "snowflake"
  | "redshift"
  | "bigquery";

/** Pipeline connection types that can be created today (matches pipeline-service ConnectionType). */
export const SUPPORTED_CONNECTION_TYPES: ConnectionType[] = ["postgres"];

export type ConnectionTypeMeta = {
  label: string;
  description: string;
  defaultPort: string;
  jdbcPrefix: string;
};

export const CONNECTION_TYPES: Record<ConnectionType, ConnectionTypeMeta> = {
  postgres: {
    label: "PostgreSQL",
    description: "Open-source relational database",
    defaultPort: "5432",
    jdbcPrefix: "jdbc:postgresql://",
  },
  mysql: {
    label: "MySQL",
    description: "Popular open-source relational database",
    defaultPort: "3306",
    jdbcPrefix: "jdbc:mysql://",
  },
  mssql: {
    label: "SQL Server",
    description: "Microsoft SQL Server",
    defaultPort: "1433",
    jdbcPrefix: "jdbc:sqlserver://",
  },
  snowflake: {
    label: "Snowflake",
    description: "Cloud data warehouse",
    defaultPort: "443",
    jdbcPrefix: "jdbc:snowflake://",
  },
  redshift: {
    label: "Redshift",
    description: "Amazon Redshift data warehouse",
    defaultPort: "5439",
    jdbcPrefix: "jdbc:redshift://",
  },
  bigquery: {
    label: "BigQuery",
    description: "Google BigQuery (uses service account key)",
    defaultPort: "",
    jdbcPrefix: "",
  },
};

export type ConnectionForm = {
  host: string;
  port: string;
  database: string;
  username: string;
  passwordRef: string;
  jdbcUrl: string;
  account?: string;
  warehouse?: string;
  projectId?: string;
  credentialKeyRef?: string;
};

export type PostgresConnectionForm = ConnectionForm;

export function maskCredentialRef(value: string | undefined): string {
  if (!value) return "—";
  if (value.startsWith("env:")) {
    const name = value.slice(4);
    if (name.length <= 4) return "env:****";
    return `env:${name.slice(0, 2)}****${name.slice(-2)}`;
  }
  if (value.startsWith("vault:")) return "vault:****";
  if (value.startsWith("${secret.")) return "${secret.****}";
  return "****";
}

export function getPasswordRef(config: Record<string, unknown> | undefined): string {
  const credentials = config?.credentials;
  if (credentials && typeof credentials === "object" && !Array.isArray(credentials)) {
    const password = (credentials as Record<string, unknown>).password;
    return password != null ? String(password) : "";
  }
  return "";
}

export function formFromPostgresConfig(config: Record<string, unknown> | undefined): ConnectionForm {
  const url = config?.url != null ? String(config.url) : "";
  return {
    host: config?.host != null ? String(config.host) : "",
    port: config?.port != null ? String(config.port) : "5432",
    database: config?.database != null ? String(config.database) : "",
    username: config?.username != null ? String(config.username) : "",
    passwordRef: getPasswordRef(config),
    jdbcUrl: url,
    account: config?.account != null ? String(config.account) : "",
    warehouse: config?.warehouse != null ? String(config.warehouse) : "",
    projectId: config?.projectId != null ? String(config.projectId) : "",
    credentialKeyRef: config?.credentialKeyRef != null ? String(config.credentialKeyRef) : "",
  };
}

export function formFromConfig(config: Record<string, unknown> | undefined, type: ConnectionType): ConnectionForm {
  const url = config?.url != null ? String(config.url) : "";
  const meta = CONNECTION_TYPES[type];
  return {
    host: config?.host != null ? String(config.host) : "",
    port: config?.port != null ? String(config.port) : meta.defaultPort,
    database: config?.database != null ? String(config.database) : "",
    username: config?.username != null ? String(config.username) : "",
    passwordRef: getPasswordRef(config),
    jdbcUrl: url,
    account: config?.account != null ? String(config.account) : "",
    warehouse: config?.warehouse != null ? String(config.warehouse) : "",
    projectId: config?.projectId != null ? String(config.projectId) : "",
    credentialKeyRef: config?.credentialKeyRef != null ? String(config.credentialKeyRef) : "",
  };
}

export function buildPostgresConfig(form: ConnectionForm): Record<string, unknown> {
  return buildConfig(form, "postgres");
}

export function buildConfig(form: ConnectionForm, type: ConnectionType): Record<string, unknown> {
  const meta = CONNECTION_TYPES[type];
  
  if (type === "bigquery") {
    const credentialKeyRef = form.credentialKeyRef?.trim();
    if (!credentialKeyRef) {
      throw new Error("Service account key reference is required for BigQuery (e.g. env:GCP_SERVICE_ACCOUNT_KEY)");
    }
    return {
      projectId: form.projectId?.trim() || "",
      credentials: { serviceAccountKey: credentialKeyRef },
    };
  }
  
  const passwordRef = form.passwordRef.trim();
  if (!passwordRef) {
    throw new Error("Password reference is required (e.g. env:PRAVAH_DB_PASSWORD)");
  }

  if (form.jdbcUrl.trim()) {
    return {
      url: form.jdbcUrl.trim(),
      username: form.username.trim(),
      credentials: { password: passwordRef },
    };
  }

  const baseConfig: Record<string, unknown> = {
    host: form.host.trim(),
    port: Number.parseInt(form.port.trim(), 10) || Number.parseInt(meta.defaultPort, 10) || 5432,
    database: form.database.trim(),
    username: form.username.trim(),
    credentials: { password: passwordRef },
  };
  
  if (type === "snowflake") {
    baseConfig.account = form.account?.trim() || "";
    baseConfig.warehouse = form.warehouse?.trim() || "";
  }
  
  return baseConfig;
}

export function connectionHostSummary(config: Record<string, unknown> | undefined): string {
  if (!config) return "—";
  if (config.url) return String(config.url);
  const host = config.host != null ? String(config.host) : "?";
  const port = config.port != null ? String(config.port) : "5432";
  const database = config.database != null ? String(config.database) : "?";
  return `${host}:${port}/${database}`;
}
