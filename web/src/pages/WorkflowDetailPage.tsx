import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import { ApiError, getDevBearerToken, getPipeline, listExecutions, type PipelineDetailResponse } from "@/lib/api";
import { formatShortDateTime, formatExecutionWallDuration } from "@/lib/format";

const tabs = ["Overview", "Runs", "Schedule", "Settings"] as const;

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/i;

export function WorkflowDetailPage() {
  const { workflowId } = useParams();
  const [tab, setTab] = useState<(typeof tabs)[number]>("Overview");
  const [pipeline, setPipeline] = useState<PipelineDetailResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [recentRuns, setRecentRuns] = useState<
    { id: string; status: string; label: string }[]
  >([]);
  const [runsError, setRunsError] = useState<string | null>(null);

  const isUuid = workflowId ? UUID_RE.test(workflowId) : false;

  useEffect(() => {
    if (!workflowId || !isUuid) {
      setPipeline(null);
      setLoadError(null);
      setRecentRuns([]);
      setRunsError(null);
      return;
    }
    if (!getDevBearerToken()) {
      setLoadError("Add a development JWT (Runs → Dev token) to load this workflow.");
      setPipeline(null);
      setRecentRuns([]);
      setRunsError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    setRunsError(null);

    void getPipeline(workflowId)
      .then((p) => {
        if (!cancelled) setPipeline(p);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setPipeline(null);
          setLoadError(e instanceof ApiError ? e.message : String(e));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    void listExecutions({ pipelineId: workflowId, page: 0, size: 8 })
      .then((res) => {
        if (cancelled) return;
        setRecentRuns(
          res.content.map((r) => ({
            id: r.id,
            status: r.status,
            label: `${formatShortDateTime(r.startedAt ?? r.createdAt)} · ${formatExecutionWallDuration(r)}`,
          })),
        );
        setRunsError(null);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setRecentRuns([]);
          setRunsError(e instanceof ApiError ? e.message : String(e));
        }
      });

    return () => {
      cancelled = true;
    };
  }, [workflowId, isUuid]);

  if (!workflowId) {
    return <p className="text-sm text-zinc-500">Missing workflow id.</p>;
  }

  if (!isUuid) {
    return (
      <Card className="border-amber-200/80 dark:border-amber-900/40">
        <CardTitle className="text-base">Invalid workflow id</CardTitle>
        <CardDescription>
          Use a pipeline UUID from the workflows list. Legacy mock paths like{" "}
          <span className="font-mono">wf-ingest</span> are no longer used.
        </CardDescription>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <nav className="text-sm text-zinc-500 dark:text-zinc-400" aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-1">
            <li>
              <Link to="/app/workflows" className="hover:text-teal-600 dark:hover:text-teal-400">
                Workflows
              </Link>
            </li>
            <li aria-hidden>/</li>
            <li className="font-medium text-zinc-800 dark:text-zinc-200">
              {pipeline?.name ?? workflowId}
            </li>
          </ol>
        </nav>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
              {pipeline?.name ?? "Workflow"}
            </h2>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
              Live header from <span className="font-medium">GET /api/v1/pipelines/{`{id}`}</span> (
              <span className="font-medium">US-12.05</span>).
            </p>
            {pipeline?.description && (
              <p className="mt-2 max-w-2xl text-sm text-zinc-600 dark:text-zinc-300">{pipeline.description}</p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {pipeline && <StatusBadge status={pipeline.status} />}
            <span className="rounded-full bg-zinc-100 px-3 py-1 font-mono text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
              v{pipeline?.currentVersion ?? "—"}
            </span>
          </div>
        </div>
      </div>

      {loadError && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load workflow</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{loadError}</CardDescription>
        </Card>
      )}

      {loading && !pipeline && !loadError && (
        <p className="text-sm text-zinc-500">Loading workflow…</p>
      )}

      <div className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="flex gap-1 overflow-x-auto" role="tablist" aria-label="Workflow sections">
          {tabs.map((t) => (
            <button
              key={t}
              type="button"
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={cn(
                "relative whitespace-nowrap px-4 py-3 text-sm font-medium transition-colors",
                tab === t
                  ? "text-teal-700 dark:text-teal-300"
                  : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              {t}
              {tab === t && (
                <span className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-teal-500 dark:bg-teal-400" />
              )}
            </button>
          ))}
        </div>
      </div>

      {tab === "Overview" && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Definition snapshot</CardTitle>
              <CardDescription>
                Visual DAG editor is <span className="font-medium">US-12.06</span>. Placeholder canvas below.
              </CardDescription>
            </CardHeader>
            <div className="flex aspect-[16/9] max-h-72 items-center justify-center rounded-xl border border-dashed border-zinc-300 bg-zinc-50 text-sm text-zinc-500 dark:border-zinc-700 dark:bg-zinc-900/50 dark:text-zinc-400">
              DAG canvas
            </div>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Recent runs</CardTitle>
              <CardDescription>Latest executions for this pipeline (US-12.07).</CardDescription>
            </CardHeader>
            {runsError ? (
              <p className="text-sm text-rose-600 dark:text-rose-400">{runsError}</p>
            ) : recentRuns.length === 0 ? (
              <p className="text-sm text-zinc-500">No runs yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {recentRuns.map((r) => (
                  <li key={r.id}>
                    <Link
                      className="font-medium text-teal-700 hover:underline dark:text-teal-300"
                      to={`/app/runs/${r.id}`}
                    >
                      {r.id.slice(0, 8)}…
                    </Link>
                    <p className="text-xs capitalize text-zinc-500">
                      {r.status} · {r.label}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}

      {tab === "Runs" && (
        <Card>
          <CardHeader>
            <CardTitle>Runs</CardTitle>
            <CardDescription>
              Open the <Link className="font-medium text-teal-600 hover:underline dark:text-teal-400" to="/app/runs">Runs</Link> page and filter by this pipeline in a later iteration.
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      {tab !== "Overview" && tab !== "Runs" && (
        <Card>
          <CardHeader>
            <CardTitle>{tab}</CardTitle>
            <CardDescription>Content for this tab is not implemented yet.</CardDescription>
          </CardHeader>
        </Card>
      )}
    </div>
  );
}
