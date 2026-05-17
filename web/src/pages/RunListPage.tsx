import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { PlayCircle, RefreshCw } from "lucide-react";
import { AlphaSetupBanner } from "@/components/workspace/AlphaSetupBanner";
import { IndicatorBadge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { DataTable } from "@/components/ui/DataTable";
import { EmptyState } from "@/components/ui/EmptyState";
import { Pagination } from "@/components/ui/Pagination";
import { Select } from "@/components/ui/Select";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { ApiError, getDevBearerToken, listExecutions, listPipelines, type ExecutionListItem } from "@/lib/api";
import { formatExecutionWallDuration, formatShortDateTime } from "@/lib/format";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";
import { cn } from "@/lib/cn";
import { getResolvedProjectId } from "@/lib/workspace";

export function RunListPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [rows, setRows] = useState<ExecutionListItem[]>([]);
  const [pipelineNames, setPipelineNames] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 20;

  const hasToken = Boolean(getDevBearerToken());

  const load = useCallback(async () => {
    const token = getDevBearerToken();
    if (!token) {
      setRows([]);
      setPipelineNames(new Map());
      setTotalPages(0);
      setTotalElements(0);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const pid = getResolvedProjectId();
      const [execRes, pipeRes] = await Promise.all([
        listExecutions({
          page,
          size: pageSize,
          status: statusFilter === "all" ? undefined : statusFilter,
        }),
        pid ? listPipelines(pid, { page: 0, size: 200 }) : Promise.resolve(null),
      ]);
      setRows(execRes.content);
      setTotalPages(execRes.totalPages);
      setTotalElements(execRes.totalElements);
      if (pipeRes) {
        const m = new Map<string, string>();
        for (const p of pipeRes.content) {
          m.set(p.id, p.name);
        }
        setPipelineNames(m);
      } else {
        setPipelineNames(new Map());
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setRows([]);
      setPipelineNames(new Map());
      setTotalPages(0);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => {
    setPage(0);
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const hasActiveRuns = rows.some((r) => {
    const s = r.status.toLowerCase();
    return s === "pending" || s === "running";
  });

  const { liveConnected } = useExecutionRealtime({
    enabled: hasToken && hasActiveRuns,
    onExecutionUpdated: () => {
      void load();
    },
  });

  const statusOptions = useMemo(
    () => [
      { value: "all", label: "All statuses" },
      { value: "pending", label: "Pending" },
      { value: "running", label: "Running" },
      { value: "succeeded", label: "Succeeded" },
      { value: "failed", label: "Failed" },
      { value: "cancelled", label: "Cancelled" },
    ],
    [],
  );

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="page-title flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-600 text-white shadow-lg shadow-emerald-500/25">
              <PlayCircle className="h-5 w-5" />
            </span>
            Runs
          </h1>
          <p className="page-desc mt-2">Track and monitor your workflow executions</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {hasToken && hasActiveRuns && (
            <IndicatorBadge
              label={liveConnected ? "Live" : "Connecting…"}
              active={liveConnected}
              pulse
            />
          )}
          <Select
            aria-label="Filter by status"
            value={statusFilter}
            onValueChange={setStatusFilter}
            options={statusOptions}
            className="w-full sm:w-40"
          />
          <Button
            type="button"
            variant="secondary"
            disabled={!hasToken || loading}
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
            Could not load runs
          </CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">
            {error}
          </CardDescription>
        </Card>
      )}

      {loading && <span className="sr-only">Loading runs…</span>}

      <DataTable
        aria-label="Runs"
        footer={
          <>
            {hasToken && !loading && rows.length === 0 && (
              <EmptyState
                icon={<PlayCircle className="h-7 w-7" />}
                title="No runs found"
                description={
                  statusFilter !== "all"
                    ? `No executions with status "${statusFilter}"`
                    : "Trigger a workflow to see your first run"
                }
                action={
                  <Button variant="primary" asChild>
                    <Link to="/app/workflows">Browse workflows</Link>
                  </Button>
                }
                className="border-0 bg-transparent"
              />
            )}
            {hasToken && !loading && rows.length > 0 && (
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
        {loading && hasToken ? (
          <TableSkeleton headers={["Workflow", "Status", "Started", "Duration"]} rows={8} />
        ) : rows.length > 0 ? (
          <table className="table-data">
            <thead>
              <tr>
                <th>Workflow</th>
                <th>Status</th>
                <th className="hidden sm:table-cell">Started</th>
                <th className="hidden md:table-cell">Duration</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const wfName = pipelineNames.get(r.pipelineId);
                return (
                  <tr key={r.id} className="group">
                    <td>
                      <Link
                        to={`/app/runs/${r.id}`}
                        className="font-medium text-neutral-900 transition-colors hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                      >
                        {wfName ?? "Pipeline"}
                      </Link>
                      <p className="mt-0.5 font-mono text-[10px] text-neutral-400 transition-opacity group-hover:opacity-100 md:opacity-60">
                        {r.id}
                      </p>
                    </td>
                    <td>
                      <StatusBadge status={r.status} />
                    </td>
                    <td className="hidden text-neutral-500 dark:text-neutral-400 sm:table-cell">
                      {formatShortDateTime(r.startedAt ?? r.createdAt)}
                    </td>
                    <td className="hidden text-neutral-500 dark:text-neutral-400 md:table-cell">
                      {formatExecutionWallDuration(r)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : null}
      </DataTable>
    </div>
  );
}
