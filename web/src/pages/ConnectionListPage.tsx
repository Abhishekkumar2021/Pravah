import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Cable, Loader2, Pencil, PlugZap, Plus, RefreshCw, Trash2 } from "lucide-react";
import { ConnectionFormDialog } from "@/components/connections/ConnectionFormDialog";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { IconButton } from "@/components/ui/IconButton";
import { Input } from "@/components/ui/Input";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { PageError } from "@/components/ui/PageError";
import { useToast } from "@/components/ui/Toast";
import {
  ApiError,
  deleteConnection,
  listConnections,
  testConnection,
  type ConnectionResponse,
} from "@/lib/api";
import {
  connectionHostSummary,
  getPasswordRef,
  maskCredentialRef,
  type ConnectionType,
} from "@/lib/connectionConfig";
import { pipelineConnectionTypeForConnector } from "@/lib/connectorCatalog";
import { formatShortDateTime } from "@/lib/format";

export function ConnectionListPage() {
  const { addToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [rows, setRows] = useState<ConnectionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");

  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<ConnectionResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<ConnectionResponse | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [createInitialType, setCreateInitialType] = useState<ConnectionType | undefined>();
  const [createFromConnector, setCreateFromConnector] = useState<string | undefined>();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listConnections();
      setRows(list);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const create = searchParams.get("create");
    const from = searchParams.get("from");
    if (!create) return;

    const mapped = from ? pipelineConnectionTypeForConnector(from) : null;
    const type = (mapped ?? create) as ConnectionType;
    setEditTarget(null);
    setCreateInitialType(type);
    setCreateFromConnector(from ?? undefined);
    setFormOpen(true);

    const next = new URLSearchParams(searchParams);
    next.delete("create");
    next.delete("from");
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.type.toLowerCase().includes(q) ||
        connectionHostSummary(c.config).toLowerCase().includes(q),
    );
  }, [rows, query]);

  const handleTest = async (connection: ConnectionResponse) => {
    setTestingId(connection.id);
    try {
      const result = await testConnection(connection.id);
      if (result.success) {
        addToast({ type: "success", title: "Connection OK", description: result.message });
      } else {
        addToast({ type: "error", title: "Connection failed", description: result.message });
      }
    } catch (e) {
      addToast({
        type: "error",
        title: "Test failed",
        description: e instanceof ApiError ? e.message : String(e),
      });
    } finally {
      setTestingId(null);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteConnection(deleteTarget.id);
      addToast({ type: "success", title: "Connection deleted" });
      setDeleteTarget(null);
      await load();
    } catch (e) {
      addToast({
        type: "error",
        title: "Delete failed",
        description: e instanceof ApiError ? e.message : String(e),
      });
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="page-title flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-lg shadow-emerald-500/20 ring-1 ring-emerald-400/20">
              <Cable className="h-5 w-5" />
            </span>
            <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
              Connections
            </span>
          </h1>
          <p className="page-desc mt-2">
            Named JDBC credentials for <strong>SQL stages</strong> in workflows. Browse the{" "}
            <Link to="/app/connectors" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
              connector catalog
            </Link>{" "}
            to see all supported data sources — only PostgreSQL connections can be saved here today.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}>
            <RefreshCw className="mr-2 h-4 w-4" aria-hidden />
            Refresh
          </Button>
          <Button
            type="button"
            onClick={() => {
              setEditTarget(null);
              setFormOpen(true);
            }}
          >
            <Plus className="mr-2 h-4 w-4" aria-hidden />
            New connection
          </Button>
        </div>
      </div>

      <Card className="p-4">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, type, or host…"
          aria-label="Search connections"
        />
      </Card>

      {error ? (
        <PageError title="Could not load connections" message={error} onRetry={() => void load()} />
      ) : null}

      {!error && loading ? (
        <TableSkeleton
          headers={["Name", "Type", "Target", "Password", "Created", "Actions"]}
          rows={4}
        />
      ) : !error && filtered.length === 0 ? (
        <EmptyState
          icon={<Cable className="h-7 w-7" />}
          title={rows.length === 0 ? "No connections yet" : "No matches"}
          description={
            rows.length === 0
              ? "Create a connection to use in SQL stage configs as connection: name."
              : "Try a different search term."
          }
          action={
            rows.length === 0 ? (
              <Button
                type="button"
                onClick={() => {
                  setEditTarget(null);
                  setFormOpen(true);
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                New connection
              </Button>
            ) : undefined
          }
        />
      ) : !error ? (
        <DataTable aria-label="Connections">
          <table className="table-data">
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th className="hidden md:table-cell">Target</th>
                <th className="hidden lg:table-cell">Password</th>
                <th className="hidden sm:table-cell">Created</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.id}>
                  <td className="font-medium">{c.name}</td>
                  <td>{c.type}</td>
                  <td className="hidden font-mono text-xs text-neutral-600 dark:text-neutral-400 md:table-cell">
                    {connectionHostSummary(c.config)}
                  </td>
                  <td className="hidden font-mono text-xs lg:table-cell">
                    {maskCredentialRef(getPasswordRef(c.config))}
                  </td>
                  <td className="hidden text-neutral-500 sm:table-cell">
                    {formatShortDateTime(c.createdAt)}
                  </td>
                  <td>
                    <div className="flex justify-end gap-1">
                      <IconButton
                        type="button"
                        aria-label={`Test connection ${c.name}`}
                        disabled={testingId === c.id}
                        onClick={() => void handleTest(c)}
                      >
                        {testingId === c.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <PlugZap className="h-4 w-4" />
                        )}
                      </IconButton>
                      <IconButton
                        type="button"
                        aria-label={`Edit ${c.name}`}
                        onClick={() => {
                          setEditTarget(c);
                          setFormOpen(true);
                        }}
                      >
                        <Pencil className="h-4 w-4" />
                      </IconButton>
                      <IconButton
                        type="button"
                        aria-label={`Delete ${c.name}`}
                        onClick={() => setDeleteTarget(c)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </IconButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataTable>
      ) : null}

      <ConnectionFormDialog
        open={formOpen}
        onOpenChange={(open) => {
          setFormOpen(open);
          if (!open) {
            setCreateInitialType(undefined);
            setCreateFromConnector(undefined);
          }
        }}
        connection={editTarget}
        initialType={createInitialType}
        fromConnectorId={createFromConnector}
        onSaved={() => void load()}
      />

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete connection?</DialogTitle>
            <DialogDescription>
              Stages referencing <strong>{deleteTarget?.name}</strong> will fail validation until
              updated.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="secondary">
                Cancel
              </Button>
            </DialogClose>
            <Button type="button" variant="danger" onClick={() => void handleDelete()}>
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
