/** Helpers for pipeline connection config (postgres) — US-12.16 */

export type PostgresConnectionForm = {
  host: string;
  port: string;
  database: string;
  username: string;
  passwordRef: string;
  jdbcUrl: string;
};

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

export function formFromPostgresConfig(config: Record<string, unknown> | undefined): PostgresConnectionForm {
  const url = config?.url != null ? String(config.url) : "";
  return {
    host: config?.host != null ? String(config.host) : "",
    port: config?.port != null ? String(config.port) : "5432",
    database: config?.database != null ? String(config.database) : "",
    username: config?.username != null ? String(config.username) : "",
    passwordRef: getPasswordRef(config),
    jdbcUrl: url,
  };
}

export function buildPostgresConfig(form: PostgresConnectionForm): Record<string, unknown> {
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

  return {
    host: form.host.trim(),
    port: Number.parseInt(form.port.trim(), 10) || 5432,
    database: form.database.trim(),
    username: form.username.trim(),
    credentials: { password: passwordRef },
  };
}

export function connectionHostSummary(config: Record<string, unknown> | undefined): string {
  if (!config) return "—";
  if (config.url) return String(config.url);
  const host = config.host != null ? String(config.host) : "?";
  const port = config.port != null ? String(config.port) : "5432";
  const database = config.database != null ? String(config.database) : "?";
  return `${host}:${port}/${database}`;
}
