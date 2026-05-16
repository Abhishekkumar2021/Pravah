import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { AlphaSetupBanner } from "@/components/workspace/AlphaSetupBanner";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { DataTable } from "@/components/ui/DataTable";
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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="page-title">Runs</h2>
          <p className="page-desc">
            Live data from <span className="font-medium">POST/GET /api/v1/executions</span> (
            <span className="font-medium">US-12.07</span>). While runs are pending or running, status
            updates stream over WebSocket (<span className="font-medium">US-12.10</span>) when a dev
            bearer token is set.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {hasToken && hasActiveRuns && (
            <span
              className={cn(
                "rounded-full border px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide",
                liveConnected
                  ? "border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200"
                  : "border-neutral-200 bg-neutral-50 text-neutral-600 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400",
              )}
              title={
                liveConnected
                  ? "Connected to execution updates (WebSocket)"
                  : "Connecting to execution updates…"
              }
            >
              {liveConnected ? "Live" : "Live…"}
            </span>
          )}
          <Button
            type="button"
            variant="secondary"
            className="h-9"
            disabled={!hasToken || loading}
            onClick={() => void load()}
          >
            Refresh
          </Button>
          <Select
            aria-label="Filter by status"
            value={statusFilter}
            onValueChange={setStatusFilter}
            options={statusOptions}
            className="w-full sm:w-44"
          />
        </div>
      </div>

      <AlphaSetupBanner
        onProjectSaved={() => setProjectNonce((n) => n + 1)}
        onTokenSaved={() => setProjectNonce((n) => n + 1)}
      />

      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load runs</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {loading && <span className="sr-only">Loading runs…</span>}

      <DataTable
        aria-label="Runs"
        footer={
          <>
            {hasToken && !loading && rows.length === 0 ? (
              <p className="px-4 py-3 text-[13px] text-neutral-500">
                No runs found for this tenant
                {statusFilter !== "all" ? ` in status "${statusFilter}".` : "."}
              </p>
            ) : null}
            {hasToken && !loading && rows.length > 0 ? (
              <Pagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                onPageChange={setPage}
              />
            ) : null}
          </>
        }
      >
        {loading && hasToken ? (
          <TableSkeleton headers={["Workflow", "Status", "Started", "Duration"]} rows={8} />
        ) : (
          <table className="table-data">
            <thead>
              <tr>
                <th>Workflow</th>
                <th>Status</th>
                <th>Started</th>
                <th>Duration</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const wfName = pipelineNames.get(r.pipelineId);
                return (
                  <tr key={r.id}>
                    <td>
                      <Link
                        to={`/app/runs/${r.id}`}
                        className="font-medium text-neutral-900 hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                      >
                        {wfName ?? "Pipeline"}
                      </Link>
                      <p className="mt-0.5 font-mono text-[11px] text-neutral-400">{r.id}</p>
                    </td>
                    <td>
                      <StatusBadge status={r.status} />
                    </td>
                    <td className="text-neutral-600 dark:text-neutral-400">
                      {formatShortDateTime(r.startedAt ?? r.createdAt)}
                    </td>
                    <td className="text-neutral-600 dark:text-neutral-400">
                      {formatExecutionWallDuration(r)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </DataTable>
    </div>
  );
}
