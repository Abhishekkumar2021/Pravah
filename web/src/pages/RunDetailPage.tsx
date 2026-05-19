import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Ban, ChevronRight, Clock, Hash, PlayCircle, RotateCcw, Settings2 } from "lucide-react";
import { IndicatorBadge, StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import {
  ApiError,
  cancelExecution,
  getExecution,
  getPipeline,
  retryExecution,
  type ExecutionResponse,
} from "@/lib/api";
import { RunJobStages } from "@/components/runs/RunJobStages";
import { RunStageGantt } from "@/components/runs/RunStageGantt";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { isFailedJob } from "@/lib/jobStatus";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";
import { formatShortDateTime, formatExecutionWallDuration } from "@/lib/format";

function isCancellable(status: string) {
  const s = status.toLowerCase();
  return s === "pending" || s === "running";
}

function formatLoadError(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return String(e);
}

export function RunDetailPage() {
  const { executionId } = useParams();
  const navigate = useNavigate();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [data, setData] = useState<ExecutionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [pipelineName, setPipelineName] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("summary");
  const reloadGeneration = useRef(0);
  const tabInitializedForExecution = useRef<string | null>(null);

  const reload = useCallback(async (opts?: { soft?: boolean }) => {
    if (!executionId) {
      return;
    }
    const gen = ++reloadGeneration.current;
    if (!opts?.soft) {
      setLoading(true);
    }
    setError(null);
    try {
      const d = await getExecution(executionId);
      if (gen !== reloadGeneration.current) {
        return;
      }
      setData(d);
    } catch (e: unknown) {
      if (gen !== reloadGeneration.current) {
        return;
      }
      if (!opts?.soft) {
        setError(formatLoadError(e));
      }
    } finally {
      if (gen === reloadGeneration.current && !opts?.soft) {
        setLoading(false);
      }
    }
  }, [executionId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    tabInitializedForExecution.current = null;
    setActiveTab("summary");
  }, [executionId]);

  useEffect(() => {
    if (!data || tabInitializedForExecution.current === data.id) {
      return;
    }
    tabInitializedForExecution.current = data.id;
    setActiveTab(data.jobs.some((j) => isFailedJob(j.status)) ? "stages" : "summary");
  }, [data]);

  const liveWsEnabled = Boolean(data && isCancellable(data.status));
  const { liveConnected } = useExecutionRealtime({
    executionId,
    enabled: liveWsEnabled,
    onExecutionUpdated: () => {
      void reload({ soft: true });
    },
  });

  useEffect(() => {
    if (!data?.pipelineId) {
      setPipelineName(null);
      return;
    }
    let cancelled = false;
    void getPipeline(data.pipelineId)
      .then((p) => {
        if (!cancelled) {
          setPipelineName(p.name);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPipelineName(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [data?.pipelineId]);

  async function onConfirmCancel() {
    if (!executionId) {
      return;
    }
    setActionError(null);
    setCancelling(true);
    try {
      const next = await cancelExecution(executionId);
      setData(next);
      setCancelOpen(false);
    } catch (e: unknown) {
      setActionError(formatLoadError(e));
    } finally {
      setCancelling(false);
    }
  }

  if (!executionId) {
    return <p className="text-sm text-neutral-500">Missing execution id.</p>;
  }

  return (
    <div className="space-y-6">
      <nav className="text-sm text-neutral-500 dark:text-neutral-400" aria-label="Breadcrumb">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/app/runs" className="transition-colors hover:text-blue-600 dark:hover:text-blue-400">
              Runs
            </Link>
          </li>
          <li aria-hidden className="text-neutral-300 dark:text-neutral-600">
            <ChevronRight className="inline h-4 w-4" />
          </li>
          <li className="font-mono text-xs text-neutral-700 dark:text-neutral-300">{executionId}</li>
        </ol>
      </nav>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="page-title flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-600 text-white shadow-md shadow-emerald-500/20 ring-1 ring-emerald-400/20">
              <PlayCircle className="h-4 w-4" />
            </span>
            <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
              {pipelineName ? `Run · ${pipelineName}` : "Run detail"}
            </span>
          </h2>
          <p className="page-desc mt-2 max-w-2xl">
            Stage timeline, per-job status, and expandable log panels.
            {data && (
              <>
                {" "}
                <Link
                  to={`/app/workflows/${data.pipelineId}`}
                  className="font-medium text-blue-600 transition-colors hover:text-blue-500 hover:underline dark:text-blue-400"
                >
                  Open workflow
                </Link>
                .
              </>
            )}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="secondary" disabled={loading} onClick={() => void reload()}>
            Refresh
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={
              !data ||
              data.status.toLowerCase() !== "failed" ||
              !data.jobs.some((j) => isFailedJob(j.status)) ||
              retrying
            }
            onClick={() => {
              if (!data) return;
              const failed = data.jobs.find((j) => isFailedJob(j.status));
              if (!failed) return;
              setActionError(null);
              setRetrying(true);
              void retryExecution(data.id, failed.stageId)
                .then((created) => {
                  navigate(`/app/runs/${created.id}`);
                })
                .catch((e: unknown) => {
                  setActionError(formatLoadError(e));
                })
                .finally(() => setRetrying(false));
            }}
          >
            <RotateCcw className="h-4 w-4" aria-hidden />
            {retrying ? "Retrying…" : "Retry from failed stage"}
          </Button>
          <Button
            type="button"
            variant="danger"
            disabled={!data || !isCancellable(data.status)}
            onClick={() => {
              setActionError(null);
              setCancelOpen(true);
            }}
          >
            <Ban className="h-4 w-4" aria-hidden />
            Cancel run
          </Button>
        </div>
      </div>

      {loading && <p className="text-sm text-neutral-500">Loading execution…</p>}
      {error && (
        <Card className="border-rose-200/80 bg-gradient-to-br from-rose-50 to-white dark:border-rose-900/50 dark:from-rose-950/40 dark:to-neutral-950">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600 ring-1 ring-rose-200/50 dark:bg-rose-900/50 dark:text-rose-400 dark:ring-rose-800/50">
              <PlayCircle className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-[15px] text-rose-800 dark:text-rose-200">Could not load execution</CardTitle>
              <CardDescription className="mt-1 text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
            </div>
          </div>
        </Card>
      )}

      {data && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={data.status} />
            {liveWsEnabled && (
              <IndicatorBadge
                label={liveConnected ? "Live" : "Live…"}
                active={liveConnected}
                pulse
              />
            )}
            <span className="inline-flex items-center gap-1.5 text-sm text-neutral-500 dark:text-neutral-400">
              Pipeline 
              <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-1.5 py-0.5 font-mono text-[11px] text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
                v{data.pipelineVersion}
              </span>
            </span>
            <span className="text-sm text-neutral-500 dark:text-neutral-400">
              Trigger: <span className="font-medium text-neutral-700 dark:text-neutral-300">{data.triggerType}</span>
              {data.triggeredBy && (
                <>
                  {" "}
                  · <span className="font-mono text-xs">{data.triggeredBy}</span>
                </>
              )}
            </span>
            {data.retryOf && (
              <span className="text-sm text-neutral-500 dark:text-neutral-400">
                Retry of{" "}
                <Link
                  to={`/app/runs/${data.retryOf}`}
                  className="font-mono text-xs text-blue-600 transition-colors hover:text-blue-500 hover:underline dark:text-blue-400"
                >
                  {data.retryOf.slice(0, 8)}…
                </Link>
              </span>
            )}
            {data.retryCount > 0 && (
              <span className="inline-flex items-center gap-1 text-sm text-neutral-500 dark:text-neutral-400">
                Retries: 
                <span className="inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-amber-100/80 px-1.5 text-[11px] font-medium text-amber-700 ring-1 ring-amber-200/50 dark:bg-amber-950/60 dark:text-amber-300 dark:ring-amber-800/50">
                  {data.retryCount}
                </span>
              </span>
            )}
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList aria-label="Run detail sections">
              <TabsTrigger value="summary" className="gap-1.5">
                <Hash className="h-3.5 w-3.5" />
                Summary
              </TabsTrigger>
              <TabsTrigger value="timeline" className="gap-1.5">
                <Clock className="h-3.5 w-3.5" />
                Timeline
              </TabsTrigger>
              <TabsTrigger value="stages" className="gap-1.5">
                <PlayCircle className="h-3.5 w-3.5" />
                Stages
              </TabsTrigger>
            </TabsList>

            <TabsContent value="summary">
              <div className="grid gap-6 lg:grid-cols-2">
                {/* Run details card */}
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Hash className="h-4 w-4 text-neutral-400" />
                      Run details
                    </CardTitle>
                    <CardDescription>Execution metadata and timing</CardDescription>
                  </CardHeader>
                  <div className="divide-y divide-neutral-100 dark:divide-neutral-800">
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Execution ID</span>
                      <span className="font-mono text-sm text-neutral-900 dark:text-neutral-100">{data.id}</span>
                    </div>
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Status</span>
                      <StatusBadge status={data.status} />
                    </div>
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Pipeline version</span>
                      <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-1.5 py-0.5 font-mono text-xs text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
                        v{data.pipelineVersion}
                      </span>
                    </div>
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Trigger type</span>
                      <span className="text-sm font-medium text-neutral-900 dark:text-neutral-100">{data.triggerType}</span>
                    </div>
                    {data.triggeredBy && (
                      <div className="flex items-center justify-between px-6 py-3">
                        <span className="text-sm text-neutral-500">Triggered by</span>
                        <span className="font-mono text-sm text-neutral-900 dark:text-neutral-100">{data.triggeredBy}</span>
                      </div>
                    )}
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Created</span>
                      <span className="text-sm text-neutral-900 dark:text-neutral-100">{formatShortDateTime(data.createdAt)}</span>
                    </div>
                    {data.startedAt && (
                      <div className="flex items-center justify-between px-6 py-3">
                        <span className="text-sm text-neutral-500">Started</span>
                        <span className="text-sm text-neutral-900 dark:text-neutral-100">{formatShortDateTime(data.startedAt)}</span>
                      </div>
                    )}
                    {data.completedAt && (
                      <div className="flex items-center justify-between px-6 py-3">
                        <span className="text-sm text-neutral-500">Completed</span>
                        <span className="text-sm text-neutral-900 dark:text-neutral-100">{formatShortDateTime(data.completedAt)}</span>
                      </div>
                    )}
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Duration</span>
                      <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-1.5 py-0.5 font-mono text-xs text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
                        {formatExecutionWallDuration({ createdAt: data.createdAt, completedAt: data.completedAt ?? undefined })}
                      </span>
                    </div>
                    <div className="flex items-center justify-between px-6 py-3">
                      <span className="text-sm text-neutral-500">Total stages</span>
                      <span className="text-sm font-medium text-neutral-900 dark:text-neutral-100">{data.jobs.length}</span>
                    </div>
                  </div>
                </Card>

                {/* Parameters card */}
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                      <Settings2 className="h-4 w-4 text-neutral-400" />
                      Parameters
                    </CardTitle>
                    <CardDescription>Runtime parameters for this execution</CardDescription>
                  </CardHeader>
                  {data.parameters && Object.keys(data.parameters).length > 0 ? (
                    <div className="divide-y divide-neutral-100 dark:divide-neutral-800">
                      {Object.entries(data.parameters).map(([key, value]) => (
                        <div key={key} className="flex items-center justify-between px-6 py-3">
                          <span className="font-mono text-sm text-neutral-500">{key}</span>
                          <span className="font-mono text-sm text-neutral-900 dark:text-neutral-100">
                            {typeof value === "boolean" ? (value ? "true" : "false") : String(value)}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex flex-col items-center py-8 text-center">
                      <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
                        <Settings2 className="h-5 w-5 text-neutral-400" />
                      </div>
                      <p className="text-sm text-neutral-500">No parameters</p>
                      <p className="mt-1 text-xs text-neutral-400">
                        This run was triggered without any custom parameters.
                      </p>
                    </div>
                  )}
                </Card>
              </div>
            </TabsContent>

            <TabsContent value="timeline">
              <Card>
                <CardHeader>
                  <CardTitle>Stage timeline</CardTitle>
                  <CardDescription>
                    Gantt overview by stage. Bar lengths are equal until start/end timestamps are available from the API.
                  </CardDescription>
                </CardHeader>
                <div className="px-6 pb-6">
                  <RunStageGantt jobs={data.jobs} />
                </div>
              </Card>
            </TabsContent>

            <TabsContent value="stages">
              <Card>
                <CardHeader>
                  <CardTitle>Stages</CardTitle>
                  <CardDescription>
                    Expand a stage for logs. Failed stages are highlighted; log streaming ships with US-02.03.
                  </CardDescription>
                </CardHeader>
                <div className="px-6 pb-6">
                  <RunJobStages
                    executionId={data.id}
                    jobs={data.jobs}
                    canRetry={data.status.toLowerCase() === "failed"}
                    onRetryFromStage={
                      data.status.toLowerCase() === "failed"
                        ? (stageId) => {
                            setActionError(null);
                            setRetrying(true);
                            void retryExecution(data.id, stageId)
                              .then((created) => navigate(`/app/runs/${created.id}`))
                              .catch((e: unknown) => setActionError(formatLoadError(e)))
                              .finally(() => setRetrying(false));
                          }
                        : undefined
                    }
                  />
                </div>
              </Card>
            </TabsContent>
          </Tabs>
        </>
      )}

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent aria-describedby={actionError ? "cancel-run-error" : undefined}>
          <DialogHeader>
            <DialogTitle>Cancel this run?</DialogTitle>
            <DialogDescription>
              Non-terminal jobs will move to <strong>cancelled</strong> and an outbox event will be emitted (US-02.04).
            </DialogDescription>
          </DialogHeader>
          {actionError && (
            <p
              id="cancel-run-error"
              className="mt-3 rounded-lg bg-rose-50 p-2 text-[13px] text-rose-800 dark:bg-rose-950/50 dark:text-rose-200"
            >
              {actionError}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={() => setCancelOpen(false)}>
              Dismiss
            </Button>
            <Button type="button" variant="danger" disabled={cancelling} onClick={() => void onConfirmCancel()}>
              {cancelling ? "Cancelling…" : "Confirm cancel"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
