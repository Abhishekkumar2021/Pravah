import { useCallback, useEffect, useState } from "react";
import { ArrowUpRight, PlayCircle, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Pill } from "@/components/ui/Badge";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import {
  ApiError,
  getDevBearerToken,
  listExecutions,
  listPipelines,
  type ExecutionListItem,
  type PipelineResponse,
} from "@/lib/api";
import { formatExecutionWallDuration, formatShortDateTime } from "@/lib/format";
import { getResolvedProjectId } from "@/lib/workspace";

export function DashboardPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [workflows, setWorkflows] = useState<PipelineResponse[]>([]);
  const [executions, setExecutions] = useState<ExecutionListItem[]>([]);
  const [pipelineNames, setPipelineNames] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const projectId = getResolvedProjectId();
  const hasToken = Boolean(getDevBearerToken());

  const load = useCallback(async () => {
    const pid = getResolvedProjectId();
    const token = getDevBearerToken();
    if (!pid || !token) {
      setWorkflows([]);
      setExecutions([]);
      setPipelineNames(new Map());
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [pipes, runs] = await Promise.all([
        listPipelines(pid, { page: 0, size: 12 }),
        listExecutions({ page: 0, size: 25 }),
      ]);
      setWorkflows(pipes.content);
      setExecutions(runs.content);
      const m = new Map<string, string>();
      for (const p of pipes.content) {
        m.set(p.id, p.name);
      }
      setPipelineNames(m);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setWorkflows([]);
      setExecutions([]);
      setPipelineNames(new Map());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const activeRuns = executions.filter((e) => {
    const s = e.status.toLowerCase();
    return s === "pending" || s === "running";
  });
  const failedRuns = executions.filter((e) => e.status.toLowerCase() === "failed").slice(0, 4);

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Dashboard
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Overview wired to REST for <span className="font-medium text-zinc-700 dark:text-zinc-300">US-12.03</span> when
          a project id and dev JWT are configured. Real-time updates follow in{" "}
          <span className="font-medium">US-12.10</span>.
        </p>
      </div>

      {!projectId && <ProjectScopeCard onSaved={() => setProjectNonce((n) => n + 1)} />}

      {projectId && !hasToken && (
        <Card className="border-teal-200/80 dark:border-teal-900/50">
          <CardTitle className="text-base">JWT required</CardTitle>
          <CardDescription>
            Add a development JWT from <span className="font-medium">Runs → Dev token</span> to populate this
            dashboard.
          </CardDescription>
        </Card>
      )}

      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load dashboard data</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {loading && hasToken && projectId && <p className="text-sm text-zinc-500">Loading…</p>}

      <div className="grid gap-6 md:grid-cols-3">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle>Recent workflows</CardTitle>
            <CardDescription>Latest pipelines in the configured project.</CardDescription>
          </CardHeader>
          {workflows.length === 0 && hasToken && projectId && !loading ? (
            <p className="text-sm text-zinc-500">No workflows yet.</p>
          ) : (
            <ul className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {workflows.slice(0, 6).map((w) => (
                <li key={w.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0">
                    <Link
                      to={`/app/workflows/${w.id}`}
                      className="group flex items-center gap-1 font-medium text-zinc-900 dark:text-zinc-100"
                    >
                      <span className="truncate">{w.name}</span>
                      <ArrowUpRight className="h-4 w-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
                    </Link>
                    <p className="text-xs text-zinc-500">Updated {formatShortDateTime(w.updatedAt)}</p>
                  </div>
                  <Pill>{w.status}</Pill>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Quick actions</CardTitle>
            <CardDescription>US-12.03 entry points.</CardDescription>
          </CardHeader>
          <div className="flex flex-col gap-2">
            <Link
              to="/app/workflows"
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-3 text-sm font-medium text-white shadow-sm transition-colors hover:bg-teal-500 dark:bg-teal-500 dark:hover:bg-teal-400"
            >
              <PlayCircle className="h-4 w-4" aria-hidden />
              Browse workflows
            </Link>
            <Link
              to="/app/runs"
              className="inline-flex items-center justify-center rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm font-medium text-zinc-800 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:hover:bg-zinc-800"
            >
              View all runs
            </Link>
          </div>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Active runs</CardTitle>
            <CardDescription>Pending and running executions for this tenant.</CardDescription>
          </CardHeader>
          {activeRuns.length === 0 ? (
            <p className="text-sm text-zinc-500">No active runs.</p>
          ) : (
            <ul className="space-y-4">
              {activeRuns.slice(0, 6).map((r) => (
                <li key={r.id}>
                  <Link
                    to={`/app/runs/${r.id}`}
                    className="block rounded-xl border border-zinc-100 p-4 transition-colors hover:border-teal-200 hover:bg-teal-50/40 dark:border-zinc-800 dark:hover:border-teal-900/60 dark:hover:bg-teal-950/20"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-medium text-zinc-900 dark:text-zinc-100">
                        {pipelineNames.get(r.pipelineId) ?? "Pipeline"}
                      </p>
                      <Pill>{r.status}</Pill>
                    </div>
                    <p className="mt-1 text-xs text-zinc-500">
                      {formatShortDateTime(r.startedAt ?? r.createdAt)} · {formatExecutionWallDuration(r)}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TriangleAlert className="h-5 w-5 text-amber-500" aria-hidden />
              Recent failures
            </CardTitle>
            <CardDescription>Latest failed runs (US-12.09 adds log drill-down).</CardDescription>
          </CardHeader>
          {failedRuns.length === 0 ? (
            <p className="text-sm text-zinc-500">No recent failures.</p>
          ) : (
            <ul className="space-y-3">
              {failedRuns.map((f) => (
                <li
                  key={f.id}
                  className="rounded-xl border border-rose-100 bg-rose-50/50 p-4 dark:border-rose-900/40 dark:bg-rose-950/30"
                >
                  <Link to={`/app/runs/${f.id}`} className="font-medium text-rose-900 dark:text-rose-200">
                    {pipelineNames.get(f.pipelineId) ?? "Pipeline"} · {f.id.slice(0, 8)}…
                  </Link>
                  <p className="mt-1 text-sm text-rose-800/90 dark:text-rose-300/90">
                    Failed · {formatShortDateTime(f.completedAt ?? f.startedAt ?? f.createdAt)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
