import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { PageError } from "@/components/ui/PageError";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  ApiError,
  createPipelineTrigger,
  disablePipelineTrigger,
  enablePipelineTrigger,
  listPipelineTriggerHistory,
  listPipelineTriggers,
  testPipelineTrigger,
  type PipelineTriggerResponse,
  type TriggerDispatchHistoryResponse,
} from "@/lib/api";

type WorkflowTriggersPanelProps = {
  pipelineId: string;
};

function TriggerListSkeleton() {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Loading triggers">
      {Array.from({ length: 2 }, (_, i) => (
        <div key={i} className="rounded-lg border border-neutral-200 p-3 dark:border-neutral-800">
          <Skeleton className="h-5 w-48" />
          <Skeleton className="mt-2 h-3 w-full max-w-md" />
        </div>
      ))}
    </div>
  );
}

export function WorkflowTriggersPanel({ pipelineId }: WorkflowTriggersPanelProps) {
  const [triggers, setTriggers] = useState<PipelineTriggerResponse[]>([]);
  const [history, setHistory] = useState<TriggerDispatchHistoryResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [triggerType, setTriggerType] = useState<"webhook" | "kafka">("webhook");
  const [kafkaTopic, setKafkaTopic] = useState("");
  const [lastSecret, setLastSecret] = useState<string | null>(null);
  const [testMessage, setTestMessage] = useState<string | null>(null);

  const loadTriggers = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const list = await listPipelineTriggers(pipelineId);
      setTriggers(list);
      if (list.length > 0) {
        setSelectedId((prev) => prev ?? list[0].id);
      }
    } catch (e) {
      setLoadError(e instanceof ApiError ? e.message : String(e));
      setTriggers([]);
    } finally {
      setLoading(false);
    }
  }, [pipelineId]);

  const loadHistory = useCallback(async (triggerId: string) => {
    try {
      const rows = await listPipelineTriggerHistory(triggerId);
      setHistory(rows);
    } catch {
      setHistory([]);
    }
  }, []);

  useEffect(() => {
    void loadTriggers();
  }, [loadTriggers]);

  useEffect(() => {
    if (selectedId) {
      void loadHistory(selectedId);
    }
  }, [selectedId, loadHistory]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setActionError(null);
    try {
      const config = triggerType === "kafka" ? { topic: kafkaTopic.trim() } : {};
      const created = await createPipelineTrigger({
        pipelineId,
        name: name.trim(),
        triggerType,
        config,
      });
      if (created.webhookSecret) {
        setLastSecret(created.webhookSecret);
      }
      setName("");
      setKafkaTopic("");
      await loadTriggers();
      setSelectedId(created.id);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : String(err));
    }
  }

  async function handleTest(triggerId: string) {
    setActionError(null);
    setTestMessage(null);
    try {
      const result = await testPipelineTrigger(triggerId, { test: true });
      await loadHistory(triggerId);
      setTestMessage(`Test run started: ${result.executionId}`);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : String(err));
    }
  }

  return (
    <div className="space-y-6">
      {actionError ? (
        <p
          className="rounded-lg border border-rose-200/80 bg-rose-50/80 px-3 py-2 text-[13px] text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-200"
          role="alert"
        >
          {actionError}
        </p>
      ) : null}

      {testMessage ? (
        <p className="rounded-lg border border-emerald-200/80 bg-emerald-50/80 px-3 py-2 text-[13px] text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-200">
          {testMessage}
        </p>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Create event trigger</CardTitle>
          <CardDescription>Webhook or Kafka triggers (US-03.06, US-03.07).</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={(e) => void handleCreate(e)} className="grid gap-3 sm:grid-cols-2">
            <Input
              placeholder="Trigger name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <select
              className="h-10 rounded-md border border-neutral-200 bg-white px-3 text-sm dark:border-neutral-800 dark:bg-neutral-950"
              value={triggerType}
              onChange={(e) => setTriggerType(e.target.value as "webhook" | "kafka")}
            >
              <option value="webhook">Webhook</option>
              <option value="kafka">Kafka</option>
            </select>
            {triggerType === "kafka" && (
              <Input
                className="sm:col-span-2"
                placeholder="Kafka topic"
                value={kafkaTopic}
                onChange={(e) => setKafkaTopic(e.target.value)}
                required
              />
            )}
            <Button type="submit" className="sm:col-span-2 w-fit">
              Create trigger
            </Button>
          </form>
          {lastSecret ? (
            <p className="mt-3 text-xs text-neutral-500 dark:text-neutral-400">
              Webhook secret (shown once): <code>{lastSecret}</code>
            </p>
          ) : null}
        </CardContent>
      </Card>

      {loadError ? (
        <PageError title="Could not load triggers" message={loadError} onRetry={() => void loadTriggers()} />
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Triggers</CardTitle>
            <CardDescription>
              {loading ? "Loading triggers…" : `${triggers.length} configured`}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              <TriggerListSkeleton />
            ) : triggers.length === 0 ? (
              <p className="text-sm text-neutral-500 dark:text-neutral-400">No triggers yet.</p>
            ) : (
              triggers.map((t) => (
                <div
                  key={t.id}
                  className={`rounded-lg border p-3 ${
                    selectedId === t.id
                      ? "border-blue-500 ring-1 ring-blue-500/20 dark:border-blue-400"
                      : "border-neutral-200 dark:border-neutral-800"
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <button
                      type="button"
                      className="text-left font-medium"
                      onClick={() => setSelectedId(t.id)}
                    >
                      {t.name} · {t.triggerType}
                    </button>
                    <div className="flex gap-2">
                      <Button size="sm" variant="secondary" onClick={() => void handleTest(t.id)}>
                        Test
                      </Button>
                      {t.enabled ? (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => void disablePipelineTrigger(t.id).then(loadTriggers)}
                        >
                          Disable
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => void enablePipelineTrigger(t.id).then(loadTriggers)}
                        >
                          Enable
                        </Button>
                      )}
                    </div>
                  </div>
                  {t.webhookUrl ? (
                    <p className="mt-1 break-all text-xs text-neutral-500 dark:text-neutral-400">
                      URL: {t.webhookUrl}
                    </p>
                  ) : null}
                </div>
              ))
            )}
          </CardContent>
        </Card>
      )}

      {selectedId && !loadError ? (
        <Card>
          <CardHeader>
            <CardTitle>Dispatch history</CardTitle>
            <CardDescription>Recent trigger firings and failures.</CardDescription>
          </CardHeader>
          <CardContent>
            {history.length === 0 ? (
              <p className="text-sm text-neutral-500 dark:text-neutral-400">No dispatch history yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {history.map((h) => (
                  <li
                    key={h.id}
                    className="flex flex-wrap gap-2 border-b border-neutral-200/60 pb-2 dark:border-neutral-800/60"
                  >
                    <span>{new Date(h.createdAt).toLocaleString()}</span>
                    <span className="font-medium">{h.status}</span>
                    {h.executionId ? <span>run {h.executionId}</span> : null}
                    {h.errorMessage ? <span className="text-rose-600">{h.errorMessage}</span> : null}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
