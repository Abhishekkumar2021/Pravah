import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshCw, Search, Workflow } from "lucide-react";
import { Link } from "react-router-dom";
import { AlphaSetupBanner } from "@/components/workspace/AlphaSetupBanner";
import { TriggerRunButton } from "@/components/workspace/TriggerRunButton";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { ApiError, getDevBearerToken, listPipelines, type PipelineResponse } from "@/lib/api";
import { formatShortDateTime } from "@/lib/format";
import { getResolvedProjectId } from "@/lib/workspace";
import { cn } from "@/lib/cn";

export function WorkflowListPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | "active" | "draft">("all");
  const [rows, setRows] = useState<PipelineResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 20;

  const projectId = getResolvedProjectId();
  const hasToken = Boolean(getDevBearerToken());

  const load = useCallback(async () => {
    const pid = getResolvedProjectId();
    const token = getDevBearerToken();
    if (!pid || !token) {
      setRows([]);
      setTotalPages(0);
      setTotalElements(0);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await listPipelines(pid, {
        page,
        size: pageSize,
        status: statusFilter === "all" ? undefined : statusFilter,
      });
      setRows(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setRows([]);
      setTotalPages(0);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    setPage(0);
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((p) => {
      const desc = p.description?.toLowerCase() ?? "";
      return p.name.toLowerCase().includes(q) || desc.includes(q);
    });
  }, [rows, query]);

  const showPagination = !loading && rows.length > 0 && totalPages > 1;

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="page-title flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-violet-600 text-white shadow-lg shadow-violet-500/25">
              <Workflow className="h-5 w-5" />
            </span>
            Workflows
          </h1>
          <p className="page-desc mt-2">Manage and run your automation pipelines</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1 lg:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
            <Input
              type="search"
              placeholder="Search workflows…"
              aria-label="Search workflows"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="pl-9"
            />
          </div>
          <Select
            aria-label="Filter by status"
            value={statusFilter}
            onValueChange={(v) => setStatusFilter(v as typeof statusFilter)}
            options={[
              { value: "all", label: "All statuses" },
              { value: "active", label: "Active" },
              { value: "draft", label: "Draft" },
            ]}
            className="w-full sm:w-36"
          />
          <Button
            type="button"
            variant="secondary"
            disabled={!projectId || !hasToken || loading}
            onClick={() => void load()}
            className="gap-2"
          >
            <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden />
            Refresh
          </Button>
        </div>
      </div>

      <AlphaSetupBanner
        onProjectSaved={() => setProjectNonce((n) => n + 1)}
        onTokenSaved={() => setProjectNonce((n) => n + 1)}
      />

      {error && (
        <Card className="border-rose-200 bg-rose-50/50 dark:border-rose-900/50 dark:bg-rose-950/30">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">
            Could not load workflows
          </CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">
            {error}
          </CardDescription>
        </Card>
      )}

      {projectId && hasToken && (
        <DataTable
          aria-label="Workflows"
          footer={
            <>
              {!loading && rows.length === 0 && (
                <EmptyState
                  icon={<Workflow className="h-7 w-7" />}
                  title="No workflows found"
                  description={
                    statusFilter !== "all"
                      ? `No workflows with status "${statusFilter}" on this page`
                      : "Create your first workflow to get started"
                  }
                  className="border-0 bg-transparent"
                />
              )}
              {!loading && rows.length > 0 && filtered.length === 0 && query.trim() && (
                <div className="px-4 py-6 text-center">
                  <p className="text-[13px] text-neutral-500">
                    No workflows match "<span className="font-medium">{query}</span>" on this page
                  </p>
                  <Button
                    variant="ghost"
                    className="mt-2 text-[12px]"
                    onClick={() => setQuery("")}
                  >
                    Clear search
                  </Button>
                </div>
              )}
              {showPagination && (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  onPageChange={setPage}
                />
              )}
            </>
          }
        >
          {loading ? (
            <TableSkeleton headers={["Name", "Status", "Version", "Updated", "Actions"]} rows={8} />
          ) : filtered.length > 0 ? (
            <table className="table-data">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Status</th>
                  <th className="hidden sm:table-cell">Version</th>
                  <th className="hidden md:table-cell">Updated</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((p) => (
                  <tr key={p.id} className="group">
                    <td>
                      <Link
                        to={`/app/workflows/${p.id}`}
                        className="font-medium text-neutral-900 transition-colors hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                      >
                        {p.name}
                      </Link>
                      <p className="mt-0.5 font-mono text-[10px] text-neutral-400 transition-opacity group-hover:opacity-100 md:opacity-60">
                        {p.id}
                      </p>
                    </td>
                    <td>
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="hidden text-neutral-500 dark:text-neutral-400 sm:table-cell">
                      v{p.currentVersion}
                    </td>
                    <td className="hidden text-neutral-500 dark:text-neutral-400 md:table-cell">
                      {formatShortDateTime(p.updatedAt)}
                    </td>
                    <td className="text-right">
                      <TriggerRunButton
                        pipelineId={p.id}
                        pipelineVersion={p.currentVersion}
                        disabled={p.status.toLowerCase() !== "active"}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </DataTable>
      )}

      {totalPages > 1 && !loading && rows.length > 0 && (
        <p className="text-center text-[11px] text-neutral-500 dark:text-neutral-400">
          Search applies to current page only
        </p>
      )}
    </div>
  );
}
