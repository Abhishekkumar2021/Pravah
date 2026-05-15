import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import { StatusBadge } from "@/components/ui/Badge";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { ApiError, getDevBearerToken, listExecutions, listPipelines, type ExecutionListItem } from "@/lib/api";
import { formatExecutionWallDuration, formatShortDateTime } from "@/lib/format";
import { getResolvedProjectId } from "@/lib/workspace";

export function RunListPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [rows, setRows] = useState<ExecutionListItem[]>([]);
  const [pipelineNames, setPipelineNames] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("");

  const projectId = getResolvedProjectId();
  const hasToken = Boolean(getDevBearerToken());

  const load = useCallback(async () => {
    const token = getDevBearerToken();
    if (!token) {
      setRows([]);
      setPipelineNames(new Map());
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const pid = getResolvedProjectId();
      const [execRes, pipeRes] = await Promise.all([
        listExecutions({ page: 0, size: 50, status: statusFilter || undefined }),
        pid ? listPipelines(pid, { page: 0, size: 200 }) : Promise.resolve(null),
      ]);
      setRows(execRes.content);
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
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const statusOptions = useMemo(
    () => [
      { value: "", label: "All statuses" },
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
          <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Runs</h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Live data from <span className="font-medium">GET /api/v1/executions</span> (
            <span className="font-medium">US-12.07</span>). Pipeline names resolve when a project id is set.
          </p>
        </div>
        <select
          aria-label="Filter by status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="h-10 rounded-xl border border-zinc-200 bg-white px-3 text-sm dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
        >
          {statusOptions.map((o) => (
            <option key={o.value || "all"} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      {!projectId && (
        <ProjectScopeCard onSaved={() => setProjectNonce((n) => n + 1)} />
      )}

      {!hasToken && (
        <Card className="border-teal-200/80 dark:border-teal-900/50">
          <CardTitle className="text-base">JWT required</CardTitle>
          <CardDescription>
            Open any run (or create one via API) and use the <span className="font-medium">Dev token</span> panel to
            store a gateway JWT, then reload this page.
          </CardDescription>
        </Card>
      )}

      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load runs</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {loading && <p className="text-sm text-zinc-500">Loading runs…</p>}

      <div className="surface-card overflow-hidden">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-zinc-200 bg-zinc-50/80 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/50 dark:text-zinc-400">
            <tr>
              <th className="px-6 py-3">Workflow</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Started</th>
              <th className="px-6 py-3">Duration</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {rows.map((r) => {
              const wfName = pipelineNames.get(r.pipelineId);
              return (
                <tr key={r.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/40">
                  <td className="px-6 py-4">
                    <Link
                      to={`/app/runs/${r.id}`}
                      className="font-medium text-zinc-900 hover:text-teal-600 dark:text-zinc-100 dark:hover:text-teal-400"
                    >
                      {wfName ?? "Pipeline"}
                    </Link>
                    <p className="mt-0.5 font-mono text-xs text-zinc-400">{r.id}</p>
                  </td>
                  <td className="px-6 py-4">
                    <StatusBadge status={r.status} />
                  </td>
                  <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">
                    {formatShortDateTime(r.startedAt ?? r.createdAt)}
                  </td>
                  <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">
                    {formatExecutionWallDuration(r)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {!loading && hasToken && rows.length === 0 && (
          <p className="border-t border-zinc-100 px-6 py-4 text-sm text-zinc-500 dark:border-zinc-800">
            No runs found for this tenant{statusFilter ? ` in status “${statusFilter}”.` : "."}
          </p>
        )}
      </div>
    </div>
  );
}
