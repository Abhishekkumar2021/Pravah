import { useState, useEffect, useCallback } from "react";
import { FileText, ChevronLeft, ChevronRight, Download, Filter, X, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Pill } from "@/components/ui/Badge";
import { DataTable } from "@/components/ui/DataTable";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageError } from "@/components/ui/PageError";
import { useToast } from "@/components/ui/Toast";
import { PageHeader } from "@/components/layout/PageHeader";
import { ApiError, type AuditLogEntry, type AuditLogPage, listAuditLogs } from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatShortDateTime } from "@/lib/format";

type PillVariant = "default" | "blue" | "green" | "amber" | "rose";

function actionPillVariant(action: string): PillVariant {
  if (action.includes("created")) return "green";
  if (action.includes("deleted") || action.includes("failed")) return "rose";
  if (action.includes("updated") || action.includes("modified")) return "blue";
  return "default";
}

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const { addToast } = useToast();

  const [actionFilter, setActionFilter] = useState("");
  const [resourceTypeFilter, setResourceTypeFilter] = useState("");

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data: AuditLogPage = await listAuditLogs({
        action: actionFilter || undefined,
        resourceType: resourceTypeFilter || undefined,
        page,
        size: 50,
      });
      setLogs(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Unknown error";
      setError(message);
      setLogs([]);
    } finally {
      setLoading(false);
    }
  }, [actionFilter, resourceTypeFilter, page]);

  useEffect(() => {
    void fetchLogs();
  }, [fetchLogs]);

  function clearFilters() {
    setActionFilter("");
    setResourceTypeFilter("");
    setPage(0);
  }

  const exportToCsv = useCallback(() => {
    if (logs.length === 0) return;

    const headers = [
      "Timestamp",
      "Actor Type",
      "Actor Name",
      "Action",
      "Resource Type",
      "Resource ID",
      "Resource Name",
      "IP Address",
    ];
    const rows = logs.map((entry) => [
      entry.createdAt,
      entry.actorType,
      entry.actorName ?? "",
      entry.action,
      entry.resourceType,
      entry.resourceId,
      entry.resourceName ?? "",
      entry.ipAddress ?? "",
    ]);

    const csvContent = [
      headers.join(","),
      ...rows.map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(",")),
    ].join("\n");

    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    addToast({
      type: "success",
      title: "Export complete",
      description: `Exported ${logs.length} entries to CSV`,
    });
  }, [logs, addToast]);

  const hasActiveFilters = Boolean(actionFilter || resourceTypeFilter);

  const headerActions = (
    <>
      <Button variant="secondary" size="sm" className="gap-2" onClick={() => void fetchLogs()} disabled={loading}>
        <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden />
        Refresh
      </Button>
      <Button variant="secondary" size="sm" className="gap-2" onClick={exportToCsv} disabled={logs.length === 0}>
        <Download className="h-4 w-4" aria-hidden />
        Export CSV
      </Button>
      <Button
        variant="secondary"
        size="sm"
        className={cn("gap-2", showFilters && "ring-2 ring-blue-500/30")}
        aria-pressed={showFilters}
        onClick={() => setShowFilters((v) => !v)}
      >
        <Filter className="h-4 w-4" aria-hidden />
        Filters
        {hasActiveFilters ? (
          <span className="rounded-full bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700 dark:bg-blue-950 dark:text-blue-300">
            on
          </span>
        ) : null}
      </Button>
    </>
  );

  if (loading && page === 0 && logs.length === 0 && !error) {
    return (
      <div className="space-y-6">
        <PageHeader
          icon={FileText}
          iconAccent="from-slate-500 to-slate-600 shadow-slate-500/20 ring-slate-400/20"
          title="Audit Log"
          description="Track system activity — pipeline changes, executions, and account events."
          actions={headerActions}
        />
        <TableSkeleton headers={["Timestamp", "Actor", "Action", "Resource", "IP"]} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon={FileText}
        iconAccent="from-slate-500 to-slate-600 shadow-slate-500/20 ring-slate-400/20"
        title="Audit Log"
        description="Track system activity — pipeline changes, executions, and account events."
        actions={headerActions}
      />

      {showFilters ? (
        <Card className="p-4 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-neutral-900 dark:text-neutral-100">Filters</span>
            {hasActiveFilters ? (
              <Button variant="ghost" size="sm" onClick={clearFilters}>
                <X className="h-4 w-4" aria-hidden />
                Clear
              </Button>
            ) : null}
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="audit-action-filter">Action</Label>
              <Input
                id="audit-action-filter"
                value={actionFilter}
                onChange={(e) => {
                  setActionFilter(e.target.value);
                  setPage(0);
                }}
                placeholder="e.g. execution.failed"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="audit-resource-type-filter">Resource type</Label>
              <Input
                id="audit-resource-type-filter"
                value={resourceTypeFilter}
                onChange={(e) => {
                  setResourceTypeFilter(e.target.value);
                  setPage(0);
                }}
                placeholder="e.g. pipeline, execution"
              />
            </div>
          </div>
        </Card>
      ) : null}

      {error ? (
        <PageError title="Could not load audit log" message={error} onRetry={() => void fetchLogs()} />
      ) : null}

      {!error && logs.length === 0 && !loading ? (
        <EmptyState
          icon={<FileText className="h-12 w-12 text-neutral-300 dark:text-neutral-600" />}
          title="No audit entries"
          description={
            hasActiveFilters
              ? "No entries match your filters. Try adjusting or clearing them."
              : "Activity will appear here as you use pipelines, runs, and settings."
          }
          action={
            hasActiveFilters ? (
              <Button variant="secondary" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : undefined
          }
        />
      ) : null}

      {!error && (logs.length > 0 || (loading && page > 0)) ? (
        <>
          <DataTable>
            <table className="table-data">
              <thead>
                <tr>
                  <th className="w-44">Timestamp</th>
                  <th className="w-40">Actor</th>
                  <th className="w-44">Action</th>
                  <th>Resource</th>
                  <th className="w-32">IP</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((entry) => (
                  <tr key={entry.id}>
                    <td className="tabular-nums text-neutral-600 dark:text-neutral-400">
                      {formatShortDateTime(entry.createdAt)}
                    </td>
                    <td>
                      <p className="font-medium text-neutral-900 dark:text-neutral-100">
                        {entry.actorName || entry.actorType}
                      </p>
                      <p className="text-xs capitalize text-neutral-500">{entry.actorType}</p>
                    </td>
                    <td>
                      <Pill variant={actionPillVariant(entry.action)}>{entry.action}</Pill>
                    </td>
                    <td>
                      <p className="text-neutral-900 dark:text-neutral-100">
                        {entry.resourceName || entry.resourceId || "—"}
                      </p>
                      <p className="text-xs text-neutral-500">{entry.resourceType}</p>
                    </td>
                    <td className="font-mono text-xs text-neutral-500">{entry.ipAddress || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataTable>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-neutral-500">
              Showing {logs.length} of {totalElements} {totalElements === 1 ? "entry" : "entries"}
            </p>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0 || loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                aria-label="Previous page"
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="min-w-[7rem] text-center text-sm text-neutral-600 dark:text-neutral-400">
                Page {page + 1} of {Math.max(totalPages, 1)}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages - 1 || loading}
                onClick={() => setPage((p) => p + 1)}
                aria-label="Next page"
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
