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
import { ApiError, listPipelines, type PipelineResponse } from "@/lib/api";
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

  const load = useCallback(async () => {
    const pid = getResolvedProjectId();
    if (!pid) {
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
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-violet-600 text-white shadow-lg shadow-violet-500/20 ring-1 ring-violet-400/20">
              <Workflow className="h-5 w-5" />
            </span>
            <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
              Workflows
            </span>
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
            disabled={!projectId || loading}
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
      />

      {error && (
        <Card className="border-rose-200/80 bg-gradient-to-br from-rose-50 to-white dark:border-rose-900/50 dark:from-rose-950/40 dark:to-neutral-950">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600 ring-1 ring-rose-200/50 dark:bg-rose-900/50 dark:text-rose-400 dark:ring-rose-800/50">
              <Workflow className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-[15px] text-rose-800 dark:text-rose-200">
                Could not load workflows
              </CardTitle>
              <CardDescription className="mt-1 text-rose-700/90 dark:text-rose-300/90">
                {error}
              </CardDescription>
            </div>
          </div>
        </Card>
      )}

      {projectId && (
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
                  <tr key={p.id} className="group transition-colors">
                    <td>
                      <Link
                        to={`/app/workflows/${p.id}`}
                        className="group/link inline-flex items-center gap-1.5 font-medium text-neutral-900 transition-colors hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                      >
                        <span>{p.name}</span>
                        <span className="opacity-0 transition-opacity group-hover/link:opacity-100">
                          <svg className="h-3.5 w-3.5" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M6 4l4 4-4 4" />
                          </svg>
                        </span>
                      </Link>
                      <p className="mt-0.5 font-mono text-[10px] text-neutral-400 transition-opacity group-hover:opacity-100 md:opacity-50">
                        {p.id}
                      </p>
                    </td>
                    <td>
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="hidden sm:table-cell">
                      <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-1.5 py-0.5 font-mono text-[11px] text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
                        v{p.currentVersion}
                      </span>
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
