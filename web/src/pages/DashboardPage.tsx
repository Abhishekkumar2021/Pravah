import { useCallback, useEffect, useState } from "react";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  Clock,
  PlayCircle,
  RefreshCw,
  TriangleAlert,
  Workflow,
  Zap,
} from "lucide-react";
import { Link } from "react-router-dom";
import { IndicatorBadge, Pill } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  DashboardActiveRunsSkeleton,
  DashboardFailuresSkeleton,
  DashboardWorkflowListSkeleton,
} from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { AlphaSetupBanner } from "@/components/workspace/AlphaSetupBanner";
import {
  ApiError,
  listExecutions,
  listPipelines,
  type ExecutionListItem,
  type PipelineResponse,
} from "@/lib/api";
import { cn } from "@/lib/cn";
import { formatExecutionWallDuration, formatShortDateTime } from "@/lib/format";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";
import { getResolvedProjectId } from "@/lib/workspace";

export function DashboardPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [workflows, setWorkflows] = useState<PipelineResponse[]>([]);
  const [executions, setExecutions] = useState<ExecutionListItem[]>([]);
  const [pipelineNames, setPipelineNames] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const projectId = getResolvedProjectId();

  const load = useCallback(async () => {
    const pid = getResolvedProjectId();
    if (!pid) {
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
  const succeededCount = executions.filter((e) => e.status.toLowerCase() === "succeeded").length;

  const { liveConnected } = useExecutionRealtime({
    enabled: Boolean(projectId) && activeRuns.length > 0,
    onExecutionUpdated: () => {
      void load();
    },
  });

  return (
    <div className="space-y-8">
      {/* Page header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="page-title flex items-center gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-lg shadow-blue-500/25">
              <Activity className="h-5 w-5" />
            </span>
            Dashboard
          </h1>
          <p className="page-desc mt-2">
            Real-time overview of your workflows and executions
          </p>
        </div>

        <div className="flex items-center gap-3">
          {projectId && activeRuns.length > 0 && (
            <IndicatorBadge
              label={liveConnected ? "Live" : "Connecting…"}
              active={liveConnected}
              pulse
            />
          )}
          {projectId && (
            <Button
              type="button"
              variant="secondary"
              disabled={loading}
              onClick={() => void load()}
              className="gap-2"
            >
              <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden />
              Refresh
            </Button>
          )}
        </div>
      </div>

      <AlphaSetupBanner
        onProjectSaved={() => setProjectNonce((n) => n + 1)}
      />

      {error && (
        <Card className="border-rose-200 bg-rose-50/50 dark:border-rose-900/50 dark:bg-rose-950/30">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600 dark:bg-rose-900/50 dark:text-rose-400">
              <TriangleAlert className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-[15px] text-rose-800 dark:text-rose-200">
                Could not load dashboard
              </CardTitle>
              <CardDescription className="mt-1 text-rose-700/90 dark:text-rose-300/90">
                {error}
              </CardDescription>
            </div>
          </div>
        </Card>
      )}

      {/* Stats row */}
      {projectId && !loading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            icon={<Workflow className="h-5 w-5" />}
            label="Workflows"
            value={workflows.length}
            iconBg="bg-blue-100 text-blue-600 dark:bg-blue-950 dark:text-blue-400"
          />
          <StatCard
            icon={<Zap className="h-5 w-5" />}
            label="Active runs"
            value={activeRuns.length}
            iconBg="bg-amber-100 text-amber-600 dark:bg-amber-950 dark:text-amber-400"
          />
          <StatCard
            icon={<CheckCircle2 className="h-5 w-5" />}
            label="Succeeded"
            value={succeededCount}
            iconBg="bg-emerald-100 text-emerald-600 dark:bg-emerald-950 dark:text-emerald-400"
          />
          <StatCard
            icon={<TriangleAlert className="h-5 w-5" />}
            label="Failed"
            value={failedRuns.length}
            iconBg="bg-rose-100 text-rose-600 dark:bg-rose-950 dark:text-rose-400"
          />
        </div>
      )}

      {loading && projectId && <span className="sr-only">Loading dashboard…</span>}

      {/* Main content grid */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Recent workflows */}
        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Workflow className="h-4 w-4 text-neutral-400" />
                Recent workflows
              </CardTitle>
              <CardDescription>Latest pipelines in your project</CardDescription>
            </div>
            <Button variant="ghost" asChild className="gap-1.5 text-[12px]">
              <Link to="/app/workflows">
                View all
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </Button>
          </CardHeader>

          {loading && projectId ? (
            <DashboardWorkflowListSkeleton rows={4} />
          ) : workflows.length === 0 && projectId && !loading ? (
            <EmptyState
              icon={<Workflow className="h-7 w-7" />}
              title="No workflows yet"
              description="Create your first workflow to start automating tasks"
              action={
                <Button variant="primary" asChild>
                  <Link to="/app/workflows">Browse workflows</Link>
                </Button>
              }
            />
          ) : (
            <ul className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {workflows.slice(0, 6).map((w) => (
                <li
                  key={w.id}
                  className="flex items-center justify-between gap-4 px-1 py-3 first:pt-0 last:pb-0"
                >
                  <div className="min-w-0 flex-1">
                    <Link
                      to={`/app/workflows/${w.id}`}
                      className="group flex items-center gap-1.5 font-medium text-neutral-900 transition-colors hover:text-blue-600 dark:text-neutral-100 dark:hover:text-blue-400"
                    >
                      <span className="truncate">{w.name}</span>
                      <ArrowUpRight className="h-3.5 w-3.5 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
                    </Link>
                    <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-neutral-500">
                      <Clock className="h-3 w-3" />
                      {formatShortDateTime(w.updatedAt)}
                    </p>
                  </div>
                  <Pill>{w.status}</Pill>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* Quick actions */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Zap className="h-4 w-4 text-amber-500" />
              Quick actions
            </CardTitle>
            <CardDescription>Jump to common tasks</CardDescription>
          </CardHeader>
          <div className="flex flex-col gap-2">
            <Button variant="primary" asChild className="w-full justify-between">
              <Link to="/app/workflows">
                <span className="flex items-center gap-2">
                  <Workflow className="h-4 w-4" aria-hidden />
                  Browse workflows
                </span>
                <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button variant="secondary" asChild className="w-full justify-between">
              <Link to="/app/runs">
                <span className="flex items-center gap-2">
                  <PlayCircle className="h-4 w-4" aria-hidden />
                  View all runs
                </span>
                <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
          </div>
        </Card>
      </div>

      {/* Active runs and failures */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Active runs */}
        <Card>
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber-500" />
                Active runs
              </CardTitle>
              <CardDescription>Pending and running executions</CardDescription>
            </div>
            {activeRuns.length > 0 && (
              <Pill variant="amber">{activeRuns.length} active</Pill>
            )}
          </CardHeader>

          {loading && projectId ? (
            <DashboardActiveRunsSkeleton rows={3} />
          ) : activeRuns.length === 0 ? (
            <div className="flex flex-col items-center py-8 text-center">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
                <CheckCircle2 className="h-6 w-6 text-neutral-400" />
              </div>
              <p className="text-[13px] font-medium text-neutral-600 dark:text-neutral-400">
                No active runs
              </p>
              <p className="mt-1 text-[12px] text-neutral-500">
                All executions have completed
              </p>
            </div>
          ) : (
            <ul className="space-y-3">
              {activeRuns.slice(0, 6).map((r) => (
                <li key={r.id}>
                  <Link
                    to={`/app/runs/${r.id}`}
                    className="group block rounded-xl border border-neutral-200 p-3.5 transition-all hover:border-blue-200 hover:bg-blue-50/50 hover:shadow-sm dark:border-neutral-800 dark:hover:border-blue-900/50 dark:hover:bg-blue-950/20"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-medium text-neutral-900 group-hover:text-blue-700 dark:text-neutral-100 dark:group-hover:text-blue-300">
                        {pipelineNames.get(r.pipelineId) ?? "Pipeline"}
                      </p>
                      <Pill>{r.status}</Pill>
                    </div>
                    <p className="mt-1.5 flex items-center gap-1.5 text-[11px] text-neutral-500">
                      <Clock className="h-3 w-3" />
                      {formatShortDateTime(r.startedAt ?? r.createdAt)} · {formatExecutionWallDuration(r)}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* Recent failures */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TriangleAlert className="h-4 w-4 text-rose-500" />
              Recent failures
            </CardTitle>
            <CardDescription>Failed runs requiring attention</CardDescription>
          </CardHeader>

          {loading && projectId ? (
            <DashboardFailuresSkeleton rows={2} />
          ) : failedRuns.length === 0 ? (
            <div className="flex flex-col items-center py-8 text-center">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 dark:bg-emerald-950/50">
                <CheckCircle2 className="h-6 w-6 text-emerald-500" />
              </div>
              <p className="text-[13px] font-medium text-neutral-600 dark:text-neutral-400">
                No recent failures
              </p>
              <p className="mt-1 text-[12px] text-neutral-500">
                All recent executions succeeded
              </p>
            </div>
          ) : (
            <ul className="space-y-3">
              {failedRuns.map((f) => (
                <li key={f.id}>
                  <Link
                    to={`/app/runs/${f.id}`}
                    className="group block rounded-xl border border-rose-200 bg-rose-50/50 p-3.5 transition-colors hover:bg-rose-100/50 dark:border-rose-900/50 dark:bg-rose-950/30 dark:hover:bg-rose-950/50"
                  >
                    <p className="font-medium text-rose-900 dark:text-rose-200">
                      {pipelineNames.get(f.pipelineId) ?? "Pipeline"}
                    </p>
                    <p className="mt-1 text-[12px] text-rose-700/90 dark:text-rose-300/80">
                      <span className="font-mono">{f.id.slice(0, 8)}…</span>
                      <span className="mx-1.5">·</span>
                      {formatShortDateTime(f.completedAt ?? f.startedAt ?? f.createdAt)}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  iconBg,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  iconBg: string;
}) {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-950">
      <div className={cn("flex h-11 w-11 items-center justify-center rounded-xl", iconBg)}>
        {icon}
      </div>
      <div>
        <p className="text-2xl font-bold tabular-nums text-neutral-900 dark:text-neutral-50">
          {value}
        </p>
        <p className="text-[12px] text-neutral-500 dark:text-neutral-400">{label}</p>
      </div>
    </div>
  );
}
