import { useEffect, useState } from "react";
import { Database } from "lucide-react";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import { listConnections, type ConnectionResponse } from "@/lib/api";

type SqlConfig = {
  query: string;
  connectionId: string;
  timeout?: number;
};

type SqlConfigFormProps = {
  config: SqlConfig;
  onChange: (config: SqlConfig) => void;
  errors: { field?: string; message: string }[];
};

export function SqlConfigForm({ config, onChange, errors }: SqlConfigFormProps) {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loadingConnections, setLoadingConnections] = useState(true);

  useEffect(() => {
    void listConnections()
      .then(setConnections)
      .catch(() => setConnections([]))
      .finally(() => setLoadingConnections(false));
  }, []);

  const queryError = errors.find((e) => e.field === "config.query");
  const connectionOptions = [
    { value: "", label: loadingConnections ? "Loading..." : "Select a connection" },
    ...connections.map((c) => ({ value: c.id, label: `${c.name} (${c.type})` })),
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
        <Database className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">SQL Stage</span>
      </div>

      <div>
        <Label htmlFor="sql-connection">Connection</Label>
        <Select
          id="sql-connection"
          value={config.connectionId ?? ""}
          onValueChange={(value) => onChange({ ...config, connectionId: value })}
          options={connectionOptions}
          className="mt-1.5 w-full"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Database connection to execute the query against
        </p>
      </div>

      <div>
        <Label htmlFor="sql-query">
          Query <span className="text-rose-500">*</span>
        </Label>
        <textarea
          id="sql-query"
          value={config.query ?? ""}
          onChange={(e) => onChange({ ...config, query: e.target.value })}
          placeholder="SELECT * FROM table WHERE condition"
          rows={6}
          className={`mt-1.5 w-full rounded-lg border bg-white px-3 py-2 font-mono text-sm shadow-sm transition-colors placeholder:text-neutral-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:bg-neutral-900 dark:text-neutral-100 ${
            queryError
              ? "border-rose-500 focus:border-rose-500"
              : "border-neutral-200 hover:border-neutral-300 focus:border-blue-500 dark:border-neutral-700 dark:hover:border-neutral-600"
          }`}
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
          value={config.timeout ?? ""}
          onChange={(e) =>
            onChange({
              ...config,
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
