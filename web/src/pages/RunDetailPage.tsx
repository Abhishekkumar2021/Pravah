import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Ban, ChevronRight, KeyRound } from "lucide-react";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  cancelExecution,
  getDevBearerToken,
  getExecution,
  setDevBearerToken,
  type ExecutionResponse,
} from "@/lib/api";
import { cn } from "@/lib/cn";

function isCancellable(status: string) {
  const s = status.toLowerCase();
  return s === "pending" || s === "running";
}

export function RunDetailPage() {
  const { executionId } = useParams();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [data, setData] = useState<ExecutionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [tokenDraft, setTokenDraft] = useState(getDevBearerToken() ?? "");
  const [showToken, setShowToken] = useState(false);

  useEffect(() => {
    if (!executionId) {
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    void getExecution(executionId)
      .then((d) => {
        if (!cancelled) {
          setData(d);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [executionId]);

  async function onConfirmCancel() {
    if (!executionId) {
      return;
    }
    setActionError(null);
    setCancelling(true);
    try {
      const next = await cancelExecution(executionId);
      setData(next);
      dialogRef.current?.close();
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : String(e));
    } finally {
      setCancelling(false);
    }
  }

  function saveToken() {
    setDevBearerToken(tokenDraft.trim() || null);
    setShowToken(false);
    if (executionId) {
      setLoading(true);
      void getExecution(executionId)
        .then(setData)
        .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
        .finally(() => setLoading(false));
    }
  }

  if (!executionId) {
    return <p className="text-sm text-zinc-500">Missing execution id.</p>;
  }

  return (
    <div className="space-y-6">
      <nav className="text-sm text-zinc-500 dark:text-zinc-400" aria-label="Breadcrumb">
        <ol className="flex flex-wrap items-center gap-1">
          <li>
            <Link to="/app/runs" className="hover:text-teal-600 dark:hover:text-teal-400">
              Runs
            </Link>
          </li>
          <li aria-hidden>
            <ChevronRight className="inline h-4 w-4" />
          </li>
          <li className="font-mono text-xs text-zinc-700 dark:text-zinc-300">{executionId}</li>
        </ol>
      </nav>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Run detail</h2>
          <p className="mt-1 max-w-2xl text-sm text-zinc-500 dark:text-zinc-400">
            Layout for <span className="font-medium text-zinc-700 dark:text-zinc-300">US-12.08</span> (timeline,
            logs, retry/cancel). Cancel calls the gateway when a bearer token is configured—see dev panel.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
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
              dialogRef.current?.showModal();
            }}
          >
            <Ban className="h-4 w-4" aria-hidden />
            Cancel run
          </Button>
        </div>
      </div>

      {showToken && (
        <Card className="border-teal-200/80 dark:border-teal-900/50">
          <CardHeader>
            <CardTitle className="text-base">Local development token</CardTitle>
            <CardDescription>
              Stored in <code className="rounded bg-zinc-100 px-1 dark:bg-zinc-800">localStorage</code> as{" "}
              <code className="rounded bg-zinc-100 px-1 dark:bg-zinc-800">pravah.devBearerToken</code>. Required for
              live API calls through the Vite proxy.
            </CardDescription>
          </CardHeader>
          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              type="password"
              value={tokenDraft}
              onChange={(e) => setTokenDraft(e.target.value)}
              placeholder="Paste JWT from your auth setup"
              className="min-h-10 flex-1 rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
              autoComplete="off"
            />
            <Button type="button" onClick={saveToken}>
              Save &amp; reload
            </Button>
          </div>
        </Card>
      )}

      {loading && <p className="text-sm text-zinc-500">Loading execution…</p>}
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
            <span className="text-sm text-zinc-500 dark:text-zinc-400">
              Pipeline <span className="font-mono text-zinc-700 dark:text-zinc-300">{data.pipelineId}</span> · v
              {data.pipelineVersion}
            </span>
            <span className="text-sm text-zinc-500 dark:text-zinc-400">
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
            <ol className="relative ms-3 border-s border-zinc-200 dark:border-zinc-800">
              {data.jobs.map((job) => (
                <li key={job.id} className="mb-8 ms-8 last:mb-2">
                  <span
                    className={cn(
                      "absolute -start-1.5 mt-1.5 flex h-3 w-3 rounded-full border border-white dark:border-zinc-950",
                      job.status.toLowerCase() === "succeeded" && "bg-emerald-500",
                      job.status.toLowerCase() === "failed" && "bg-rose-500",
                      job.status.toLowerCase() === "running" && "bg-sky-500",
                      job.status.toLowerCase() === "cancelled" && "bg-amber-500",
                      !["succeeded", "failed", "running", "cancelled"].includes(job.status.toLowerCase()) &&
                        "bg-zinc-400",
                    )}
                  />
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-zinc-900 dark:text-zinc-100">{job.stageName}</p>
                    <StatusBadge status={job.status} />
                    <span className="text-xs text-zinc-500">attempt {job.attempt}</span>
                  </div>
                  <p className="mt-1 font-mono text-xs text-zinc-500">
                    {job.stageId} · {job.id}
                  </p>
                </li>
              ))}
            </ol>
          </Card>
        </>
      )}

      <dialog
        ref={dialogRef}
        className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 text-zinc-900 shadow-xl backdrop:bg-black/40 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
      >
        <h3 className="text-lg font-semibold">Cancel this run?</h3>
        <p className="mt-2 text-sm text-zinc-600 dark:text-zinc-400">
          Non-terminal jobs will move to <strong>cancelled</strong> and an outbox event will be emitted (US-02.04).
        </p>
        {actionError && (
          <p className="mt-3 rounded-lg bg-rose-50 p-2 text-sm text-rose-800 dark:bg-rose-950/50 dark:text-rose-200">
            {actionError}
          </p>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => dialogRef.current?.close()}>
            Dismiss
          </Button>
          <Button type="button" variant="danger" disabled={cancelling} onClick={() => void onConfirmCancel()}>
            {cancelling ? "Cancelling…" : "Confirm cancel"}
          </Button>
        </div>
      </dialog>
    </div>
  );
}
