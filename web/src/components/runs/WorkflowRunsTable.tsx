import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { PlayCircle, RefreshCw } from "lucide-react";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { ApiError, listExecutions, type ExecutionListItem } from "@/lib/api";
import { formatExecutionWallDuration, formatShortDateTime } from "@/lib/format";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";
import { cn } from "@/lib/cn";

type WorkflowRunsTableProps = {
  pipelineId: string;
  pipelineName?: string;
};

export function WorkflowRunsTable({ pipelineId, pipelineName }: WorkflowRunsTableProps) {
  const [rows, setRows] = useState<ExecutionListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 10;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listExecutions({
        pipelineId,
        page,
        size: pageSize,
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
  }, [pipelineId, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const hasActiveRuns = rows.some((r) => {
    const s = r.status.toLowerCase();
    return s === "pending" || s === "running";
  });

  useExecutionRealtime({
    enabled: hasActiveRuns,
    onExecutionUpdated: () => {
      void load();
    },
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-neutral-600 dark:text-neutral-400">
          {totalElements > 0 ? `${totalElements} run${totalElements !== 1 ? "s" : ""}` : "No runs yet"}
        </p>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={loading}
          onClick={() => void load()}
          className="gap-1.5"
        >
          <RefreshCw className={cn("h-3.5 w-3.5", loading && "animate-spin")} aria-hidden />
          Refresh
        </Button>
      </div>

      {error && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300">
          {error}
        </div>
      )}

      <DataTable
        aria-label={`Runs for ${pipelineName ?? "workflow"}`}
        footer={
          <>
            {!loading && rows.length === 0 && !error && (
              <EmptyState
                icon={<PlayCircle className="h-7 w-7" />}
                title="No runs yet"
                description="Trigger this workflow to see your first run"
                className="border-0 bg-transparent py-8"
              />
            )}
            {!loading && rows.length > 0 && (
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
          <TableSkeleton headers={["Run ID", "Status", "Started", "Duration"]} rows={5} />
        ) : rows.length > 0 ? (
          <table className="table-data">
            <thead>
              <tr>
                <th>Run ID</th>
                <th>Status</th>
                <th className="hidden sm:table-cell">Started</th>
                <th className="hidden md:table-cell">Duration</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="group transition-colors">
                  <td>
                    <Link
                      to={`/app/runs/${r.id}`}
                      className="group/link inline-flex items-center gap-1.5 font-mono text-sm font-medium text-neutral-900 transition-colors hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                    >
                      <span>{r.id.slice(0, 8)}…</span>
                      <span className="opacity-0 transition-opacity group-hover/link:opacity-100">
                        <svg className="h-3.5 w-3.5" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2">
                          <path d="M6 4l4 4-4 4" />
                        </svg>
                      </span>
                    </Link>
                  </td>
                  <td>
                    <StatusBadge status={r.status} />
                  </td>
                  <td className="hidden text-neutral-500 dark:text-neutral-400 sm:table-cell">
                    {formatShortDateTime(r.startedAt ?? r.createdAt)}
                  </td>
                  <td className="hidden md:table-cell">
                    <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-1.5 py-0.5 font-mono text-[11px] text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
                      {formatExecutionWallDuration(r)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </DataTable>
    </div>
  );
}
