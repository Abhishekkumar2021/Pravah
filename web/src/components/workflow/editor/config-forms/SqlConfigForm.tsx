import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Database, ExternalLink } from "lucide-react";
import { ExpandableTextarea } from "@/components/ui/ExpandableTextarea";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import { Button } from "@/components/ui/Button";
import { listConnections, type ConnectionResponse } from "@/lib/api";

type SqlConfig = {
  query: string;
  connection: string;
  timeout?: number;
};

type SqlConfigFormProps = {
  config?: Record<string, unknown> | null;
  onChange: (config: Record<string, unknown>) => void;
  errors: { field?: string; message: string }[];
};

const EMPTY_SQL_CONFIG: SqlConfig = { query: "", connection: "" };

function readSqlConfig(config?: Record<string, unknown> | null): SqlConfig {
  const merged = { ...EMPTY_SQL_CONFIG, ...(config ?? {}) };
  return {
    query: typeof merged.query === "string" ? merged.query : "",
    connection: typeof merged.connection === "string" ? merged.connection : "",
    timeout: typeof merged.timeout === "number" ? merged.timeout : undefined,
  };
}

export function SqlConfigForm({ config, onChange, errors }: SqlConfigFormProps) {
  const sqlConfig = readSqlConfig(config);
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loadingConnections, setLoadingConnections] = useState(true);

  useEffect(() => {
    void listConnections()
      .then((rows) => {
        setConnections(rows);
        const legacyId = config?.connectionId;
        if (typeof legacyId === "string" && legacyId && !readSqlConfig(config).connection) {
          const match = rows.find((c) => c.id === legacyId);
          if (match) {
            onChange({
              ...readSqlConfig(config),
              connection: match.name,
            });
          }
        }
      })
      .catch(() => setConnections([]))
      .finally(() => setLoadingConnections(false));
    // Migrate legacy connectionId once when connections load.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional one-shot migration
  }, []);

  const queryError = errors.find((e) => e.field === "config.query");
  const connectionError = errors.find((e) => e.field === "config.connection");
  const connectionOptions = connections.map((c) => ({
    value: c.name,
    label: `${c.name} (${c.type})`,
  }));

  function patchSqlConfig(patch: Partial<SqlConfig>) {
    const next = { ...sqlConfig, ...patch };
    onChange({
      query: next.query,
      connection: next.connection,
      ...(next.timeout != null ? { timeout: next.timeout } : {}),
    });
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
        <Database className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">SQL Stage</span>
      </div>

      <p className="text-xs leading-relaxed text-neutral-600 dark:text-neutral-400">
        Runs a JDBC query against a{" "}
        <Link to="/app/connections" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
          saved connection
        </Link>
        . For APIs, files, or SaaS sources, use a <span className="font-medium">Python</span> or{" "}
        <span className="font-medium">Container</span> stage — see the{" "}
        <Link to="/app/connectors" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
          connector catalog
        </Link>{" "}
        for options.
      </p>

      <div>
        <Label htmlFor="sql-connection">Connection</Label>
        <Select
          id="sql-connection"
          aria-label="Database connection"
          value={sqlConfig.connection ?? ""}
          onValueChange={(value) => patchSqlConfig({ connection: value })}
          options={connectionOptions}
          placeholder={loadingConnections ? "Loading…" : "Select a connection"}
          disabled={loadingConnections}
          invalid={!!connectionError}
          className="mt-1.5 w-full"
        />
        {connectionError ? (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{connectionError.message}</p>
        ) : (
          <p className="mt-1 text-[10px] text-neutral-500">
            Saved as <code className="font-mono">connection: {sqlConfig.connection || "name"}</code> in pipeline YAML.
          </p>
        )}
        {!loadingConnections && connections.length === 0 ? (
          <Button variant="secondary" size="sm" className="mt-2" asChild>
            <Link to="/app/connections?create=postgres">
              <ExternalLink className="h-3.5 w-3.5" aria-hidden />
              Create a connection
            </Link>
          </Button>
        ) : null}
      </div>

      <div>
        <Label htmlFor="sql-query">
          Query <span className="text-rose-500">*</span>
        </Label>
        <ExpandableTextarea
          id="sql-query"
          value={sqlConfig.query ?? ""}
          onChange={(e) => patchSqlConfig({ query: e.target.value })}
          placeholder="SELECT * FROM table WHERE condition"
          rows={8}
          mono
          invalid={!!queryError}
          expandTitle="SQL query"
          expandDescription="Use ${var.name} for variables and ${secret.name} for secrets."
          minHeightClass="min-h-[10rem]"
        />
        {queryError && (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{queryError.message}</p>
        )}
        <p className="mt-1 text-[10px] text-neutral-500">
          SQL query to execute. Use {"${var.name}"} for variables, {"${secret.name}"} for secrets.
        </p>
      </div>

      <div>
        <Label htmlFor="sql-timeout">Timeout (seconds)</Label>
        <Input
          id="sql-timeout"
          type="number"
          min={0}
          value={sqlConfig.timeout ?? ""}
          onChange={(e) =>
            patchSqlConfig({
              timeout: e.target.value ? parseInt(e.target.value, 10) : undefined,
            })
          }
          placeholder="300"
          className="mt-1.5"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Maximum execution time. Leave empty for default (300s).
        </p>
      </div>
    </div>
  );
}
