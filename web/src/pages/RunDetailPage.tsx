import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Ban, ChevronRight, KeyRound, RotateCcw } from "lucide-react";
import { StatusBadge } from "@/components/ui/Badge";
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
import { DevTokenCard } from "@/components/workspace/DevTokenCard";
import {
  ApiError,
  cancelExecution,
  getDevBearerToken,
  getExecution,
  getPipeline,
  type ExecutionResponse,
} from "@/lib/api";
import { RunJobStages } from "@/components/runs/RunJobStages";
import { RunStageGantt } from "@/components/runs/RunStageGantt";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/Tooltip";
import { cn } from "@/lib/cn";
import { isFailedJob } from "@/lib/jobStatus";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";

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
  const [cancelOpen, setCancelOpen] = useState(false);
  const [data, setData] = useState<ExecutionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [showToken, setShowToken] = useState(false);
  const [pipelineName, setPipelineName] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("timeline");
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
    setActiveTab("timeline");
  }, [executionId]);

  useEffect(() => {
    if (!data || tabInitializedForExecution.current === data.id) {
      return;
    }
    tabInitializedForExecution.current = data.id;
    setActiveTab(data.jobs.some((j) => isFailedJob(j.status)) ? "stages" : "timeline");
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
    if (!data?.pipelineId || !getDevBearerToken()) {
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
        <ol className="flex flex-wrap items-center gap-1">
          <li>
            <Link to="/app/runs" className="hover:text-blue-600 dark:hover:text-blue-400">
              Runs
            </Link>
          </li>
          <li aria-hidden>
            <ChevronRight className="inline h-4 w-4" />
          </li>
          <li className="font-mono text-xs text-neutral-700 dark:text-neutral-300">{executionId}</li>
        </ol>
      </nav>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="page-title">
            {pipelineName ? `Run · ${pipelineName}` : "Run detail"}
          </h2>
          <p className="page-desc max-w-2xl">
            Stage timeline, per-job status, and expandable log panels. Live status uses WebSocket when a dev token is set.
            {data && (
              <>
                {" "}
                <Link
                  to={`/app/workflows/${data.pipelineId}`}
                  className="font-medium text-blue-600 hover:underline dark:text-blue-400"
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
          <Button type="button" variant="secondary" onClick={() => setShowToken((s) => !s)}>
            <KeyRound className="h-4 w-4" aria-hidden />
            Dev token
          </Button>
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="inline-flex flex-col items-center gap-0.5">
                <Button type="button" variant="secondary" disabled aria-disabled="true">
                  <RotateCcw className="h-4 w-4" aria-hidden />
                  Retry
                </Button>
                <span className="text-[10px] leading-none text-neutral-500 dark:text-neutral-400">
                  US-02.06
                </span>
              </span>
            </TooltipTrigger>
            <TooltipContent>
              Retry from failed stage is planned for US-02.06 (not in alpha yet).
            </TooltipContent>
          </Tooltip>
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

      {showToken && (
        <DevTokenCard
          onSaved={() => {
            setShowToken(false);
            void reload();
          }}
        />
      )}

      {loading && <p className="text-sm text-neutral-500">Loading execution…</p>}
      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load execution</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {data && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={data.status} />
            {liveWsEnabled && (
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
            <span className="text-sm text-neutral-500 dark:text-neutral-400">
              Pipeline <span className="font-mono text-neutral-700 dark:text-neutral-300">{data.pipelineId}</span> · v
              {data.pipelineVersion}
            </span>
            <span className="text-sm text-neutral-500 dark:text-neutral-400">
              Trigger: {data.triggerType}
              {data.triggeredBy && (
                <>
                  {" "}
                  · <span className="font-mono">{data.triggeredBy}</span>
                </>
              )}
            </span>
          </div>

          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList aria-label="Run detail sections">
              <TabsTrigger value="timeline">Timeline</TabsTrigger>
              <TabsTrigger value="stages">Stages &amp; logs</TabsTrigger>
            </TabsList>
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
                  <RunJobStages jobs={data.jobs} />
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
