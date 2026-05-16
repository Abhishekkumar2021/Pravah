import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Ban, ChevronRight, KeyRound } from "lucide-react";
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
import { Input } from "@/components/ui/Input";
import {
  ApiError,
  cancelExecution,
  getDevBearerToken,
  getExecution,
  getPipeline,
  setDevBearerToken,
  type ExecutionResponse,
} from "@/lib/api";
import { cn } from "@/lib/cn";

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
  const [tokenDraft, setTokenDraft] = useState(getDevBearerToken() ?? "");
  const [showToken, setShowToken] = useState(false);
  const [pipelineName, setPipelineName] = useState<string | null>(null);
  const reloadGeneration = useRef(0);

  const reload = useCallback(async () => {
    if (!executionId) {
      return;
    }
    const gen = ++reloadGeneration.current;
    setLoading(true);
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
      setError(formatLoadError(e));
    } finally {
      if (gen === reloadGeneration.current) {
        setLoading(false);
      }
    }
  }, [executionId]);

  useEffect(() => {
    void reload();
  }, [reload]);

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

  function saveToken() {
    setDevBearerToken(tokenDraft.trim() || null);
    setShowToken(false);
    void reload();
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
            Layout for <span className="font-medium text-neutral-700 dark:text-neutral-300">US-12.08</span> (timeline,
            logs, retry/cancel). Refresh for status until <span className="font-medium">US-12.10</span> WebSocket.
            Cancel calls the gateway when a bearer token is configured—see dev panel.
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
        <Card className="border-blue-200/80 dark:border-blue-900/50">
          <CardHeader>
            <CardTitle className="text-base">Local development token</CardTitle>
            <CardDescription>
              Stored in <code className="rounded bg-neutral-100 px-1 dark:bg-neutral-800">localStorage</code> as{" "}
              <code className="rounded bg-neutral-100 px-1 dark:bg-neutral-800">pravah.devBearerToken</code>. Required for
              live API calls through the Vite proxy.
            </CardDescription>
          </CardHeader>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Input
              type="password"
              value={tokenDraft}
              onChange={(e) => setTokenDraft(e.target.value)}
              placeholder="Paste JWT from your auth setup"
              className="min-h-10 flex-1 font-mono text-[12px]"
              autoComplete="off"
            />
            <Button type="button" onClick={saveToken}>
              Save &amp; reload
            </Button>
          </div>
        </Card>
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

          <Card>
            <CardHeader>
              <CardTitle>Stage timeline</CardTitle>
              <CardDescription>
                Gantt-style chart can replace this vertical timeline. Errors surface per job for US-12.08/12.09.
              </CardDescription>
            </CardHeader>
            <ol className="relative ms-3 border-s border-neutral-200 dark:border-neutral-800">
              {data.jobs.map((job) => (
                <li key={job.id} className="mb-8 ms-8 last:mb-2">
                  <span
                    className={cn(
                      "absolute -start-1.5 mt-1.5 flex h-3 w-3 rounded-full border border-white dark:border-neutral-950",
                      job.status.toLowerCase() === "succeeded" && "bg-emerald-500",
                      job.status.toLowerCase() === "failed" && "bg-rose-500",
                      job.status.toLowerCase() === "running" && "bg-blue-500",
                      job.status.toLowerCase() === "cancelled" && "bg-amber-500",
                      !["succeeded", "failed", "running", "cancelled"].includes(job.status.toLowerCase()) &&
                        "bg-neutral-400",
                    )}
                  />
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-neutral-900 dark:text-neutral-100">{job.stageName}</p>
                    <StatusBadge status={job.status} />
                    <span className="text-xs text-neutral-500">attempt {job.attempt}</span>
                  </div>
                  <p className="mt-1 font-mono text-xs text-neutral-500">
                    {job.stageId} · {job.id}
                  </p>
                </li>
              ))}
            </ol>
          </Card>
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
