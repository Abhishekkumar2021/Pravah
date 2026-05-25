import { useEffect, useState } from "react";
import { Database, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import { Switch } from "@/components/ui/Switch";
import { useToast } from "@/components/ui/Toast";
import {
  ApiError,
  createConnection,
  type ConnectionResponse,
  updateConnection,
} from "@/lib/api";
import {
  buildConfig,
  CONNECTION_TYPES,
  formFromConfig,
  SUPPORTED_CONNECTION_TYPES,
  type ConnectionForm,
  type ConnectionType,
} from "@/lib/connectionConfig";

type ConnectionFormDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  connection?: ConnectionResponse | null;
  onSaved: () => void;
  /** Pre-select type when creating from connector catalog or deep link. */
  initialType?: ConnectionType;
  initialName?: string;
  fromConnectorId?: string;
};

const TYPE_OPTIONS = (Object.keys(CONNECTION_TYPES) as ConnectionType[]).map((type) => ({
  value: type,
  label: SUPPORTED_CONNECTION_TYPES.includes(type)
    ? CONNECTION_TYPES[type].label
    : `${CONNECTION_TYPES[type].label} (coming soon)`,
  disabled: !SUPPORTED_CONNECTION_TYPES.includes(type),
}));

function getEmptyForm(type: ConnectionType): ConnectionForm {
  const meta = CONNECTION_TYPES[type];
  return {
    host: type === "bigquery" ? "" : "localhost",
    port: meta.defaultPort,
    database: "",
    username: "",
    passwordRef: type === "bigquery" ? "" : "env:PRAVAH_DB_PASSWORD",
    jdbcUrl: "",
    account: "",
    warehouse: "",
    projectId: "",
    credentialKeyRef: type === "bigquery" ? "env:GCP_SERVICE_ACCOUNT_KEY" : "",
  };
}

export function ConnectionFormDialog({
  open,
  onOpenChange,
  connection,
  onSaved,
  initialType,
  initialName,
  fromConnectorId,
}: ConnectionFormDialogProps) {
  const { addToast } = useToast();
  const isEdit = connection != null;

  const [name, setName] = useState("");
  const [type, setType] = useState<ConnectionType>("postgres");
  const [form, setForm] = useState<ConnectionForm>(getEmptyForm("postgres"));
  const [useJdbcUrl, setUseJdbcUrl] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setError(null);
    if (connection) {
      const connType = (connection.type as ConnectionType) || "postgres";
      const parsed = formFromConfig(connection.config, connType);
      setName(connection.name);
      setType(connType);
      setForm(parsed);
      setUseJdbcUrl(Boolean(parsed.jdbcUrl));
    } else {
      const createType =
        initialType && SUPPORTED_CONNECTION_TYPES.includes(initialType) ? initialType : "postgres";
      setName(initialName ?? "");
      setType(createType);
      setForm(getEmptyForm(createType));
      setUseJdbcUrl(false);
    }
  }, [open, connection, initialType, initialName]);

  const handleTypeChange = (newType: ConnectionType) => {
    setType(newType);
    setForm(getEmptyForm(newType));
    setUseJdbcUrl(false);
  };

  const patchForm = (patch: Partial<ConnectionForm>) => {
    setForm((prev) => ({ ...prev, ...patch }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const config = buildConfig(form, type);
      if (isEdit && connection) {
        await updateConnection(connection.id, { config });
        addToast({ type: "success", title: "Connection updated" });
      } else {
        const trimmedName = name.trim().toLowerCase();
        if (!/^[a-z][a-z0-9_]*$/.test(trimmedName)) {
          throw new Error(
            "Name must start with a letter and use only lowercase letters, numbers, and underscores",
          );
        }
        await createConnection({ name: trimmedName, type, config });
        addToast({ type: "success", title: "Connection created" });
      }
      onSaved();
      onOpenChange(false);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : String(err);
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const meta = CONNECTION_TYPES[type];
  const isBigQuery = type === "bigquery";
  const isSnowflake = type === "snowflake";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <form onSubmit={(e) => void handleSubmit(e)}>
          <DialogHeader>
            <DialogTitle>{isEdit ? "Edit connection" : "New connection"}</DialogTitle>
            <DialogDescription>
              {fromConnectorId ? (
                <>
                  Creating a workflow connection from the <strong>{fromConnectorId}</strong> connector.
                  Reference it in SQL stages as{" "}
                  <code className="rounded bg-neutral-100 px-1 text-xs dark:bg-neutral-800">
                    connection: name
                  </code>
                  .
                </>
              ) : (
                <>
                  Store JDBC settings for SQL stages. Use{" "}
                  <code className="rounded bg-neutral-100 px-1 text-xs dark:bg-neutral-800">
                    env:VAR_NAME
                  </code>{" "}
                  for passwords — values are never stored in plain text.
                </>
              )}
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 space-y-4">
            {!isEdit && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="conn-name">Name</Label>
                  <Input
                    id="conn-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="warehouse"
                    autoComplete="off"
                    required
                  />
                  <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    Referenced in stages as{" "}
                    <code className="text-neutral-700 dark:text-neutral-300">
                      connection: {name.trim() || "warehouse"}
                    </code>
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="conn-type">Type</Label>
                  <Select
                    id="conn-type"
                    aria-label="Connection type"
                    value={type}
                    onValueChange={(v) => handleTypeChange(v as ConnectionType)}
                    options={TYPE_OPTIONS}
                    className="w-full"
                  />
                  <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    {meta.description}
                  </p>
                </div>
              </>
            )}

            {isEdit && connection && (
              <div className="flex items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-900">
                <Database className="h-4 w-4 text-neutral-400" />
                <span className="font-medium">{connection.name}</span>
                <span className="rounded bg-neutral-200/60 px-1.5 py-0.5 text-xs text-neutral-600 dark:bg-neutral-800 dark:text-neutral-400">
                  {CONNECTION_TYPES[(connection.type as ConnectionType) || "postgres"]?.label || connection.type}
                </span>
              </div>
            )}

            {isBigQuery ? (
              <>
                <div className="space-y-2">
                  <Label htmlFor="conn-project">Project ID</Label>
                  <Input
                    id="conn-project"
                    value={form.projectId ?? ""}
                    onChange={(e) => patchForm({ projectId: e.target.value })}
                    placeholder="my-gcp-project"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="conn-credential-key">Service account key reference</Label>
                  <Input
                    id="conn-credential-key"
                    value={form.credentialKeyRef ?? ""}
                    onChange={(e) => patchForm({ credentialKeyRef: e.target.value })}
                    placeholder="env:GCP_SERVICE_ACCOUNT_KEY"
                    autoComplete="off"
                    required
                  />
                  <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    Reference to JSON service account key — base64 encoded or file path.
                  </p>
                </div>
              </>
            ) : (
              <>
                {!isBigQuery && (
                  <div className="flex items-center justify-between gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2.5 dark:border-neutral-800 dark:bg-neutral-900/60">
                    <div className="space-y-0.5">
                      <Label htmlFor="conn-jdbc-url-mode">Use full JDBC URL</Label>
                      <p className="text-xs text-neutral-500 dark:text-neutral-400">
                        Enter a complete connection string instead of host, port, and database fields.
                      </p>
                    </div>
                    <Switch
                      id="conn-jdbc-url-mode"
                      checked={useJdbcUrl}
                      onCheckedChange={setUseJdbcUrl}
                      aria-label="Use full JDBC URL"
                    />
                  </div>
                )}

                {useJdbcUrl ? (
                  <div className="space-y-2">
                    <Label htmlFor="conn-url">JDBC URL</Label>
                    <Input
                      id="conn-url"
                      value={form.jdbcUrl}
                      onChange={(e) => patchForm({ jdbcUrl: e.target.value })}
                      placeholder={`${meta.jdbcPrefix}localhost:${meta.defaultPort}/mydb`}
                      required
                    />
                  </div>
                ) : (
                  <div className="grid gap-3 sm:grid-cols-2">
                    {isSnowflake && (
                      <>
                        <div className="space-y-2 sm:col-span-2">
                          <Label htmlFor="conn-account">Account</Label>
                          <Input
                            id="conn-account"
                            value={form.account ?? ""}
                            onChange={(e) => patchForm({ account: e.target.value })}
                            placeholder="abc12345.us-east-1"
                            required
                          />
                        </div>
                        <div className="space-y-2 sm:col-span-2">
                          <Label htmlFor="conn-warehouse">Warehouse</Label>
                          <Input
                            id="conn-warehouse"
                            value={form.warehouse ?? ""}
                            onChange={(e) => patchForm({ warehouse: e.target.value })}
                            placeholder="COMPUTE_WH"
                            required
                          />
                        </div>
                      </>
                    )}
                    {!isSnowflake && (
                      <div className="space-y-2 sm:col-span-2">
                        <Label htmlFor="conn-host">Host</Label>
                        <Input
                          id="conn-host"
                          value={form.host}
                          onChange={(e) => patchForm({ host: e.target.value })}
                          required={!useJdbcUrl}
                        />
                      </div>
                    )}
                    <div className="space-y-2">
                      <Label htmlFor="conn-port">Port</Label>
                      <Input
                        id="conn-port"
                        value={form.port}
                        onChange={(e) => patchForm({ port: e.target.value })}
                        placeholder={meta.defaultPort}
                        inputMode="numeric"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="conn-database">Database</Label>
                      <Input
                        id="conn-database"
                        value={form.database}
                        onChange={(e) => patchForm({ database: e.target.value })}
                        required={!useJdbcUrl}
                      />
                    </div>
                  </div>
                )}

                <div className="space-y-2">
                  <Label htmlFor="conn-user">Username</Label>
                  <Input
                    id="conn-user"
                    value={form.username}
                    onChange={(e) => patchForm({ username: e.target.value })}
                    autoComplete="off"
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="conn-password-ref">Password reference</Label>
                  <Input
                    id="conn-password-ref"
                    value={form.passwordRef}
                    onChange={(e) => patchForm({ passwordRef: e.target.value })}
                    placeholder="env:PRAVAH_DB_PASSWORD"
                    autoComplete="off"
                    required
                  />
                  <p className="text-xs text-neutral-500 dark:text-neutral-400">
                    Resolved at runtime from environment, Vault, or tenant secrets — never shown in
                    the UI after save.
                  </p>
                </div>
              </>
            )}

            {error && (
              <p className="text-sm text-rose-600 dark:text-rose-400" role="alert">
                {error}
              </p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <DialogClose asChild>
              <Button type="button" variant="secondary">
                Cancel
              </Button>
            </DialogClose>
            <Button type="submit" disabled={saving}>
              {saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              {isEdit ? "Save changes" : "Create connection"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
