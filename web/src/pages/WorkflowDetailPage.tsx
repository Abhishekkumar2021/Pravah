import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Bell, Calendar, LayoutDashboard, Pencil, Play, Settings, Workflow } from "lucide-react";
import { AlertRulesPanel } from "@/components/alerts/AlertRulesPanel";
import { WorkflowRunsTable } from "@/components/runs/WorkflowRunsTable";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { WorkflowSettings } from "@/components/workflow/WorkflowSettings";
import { StatusBadge } from "@/components/ui/Badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { TriggerRunButton } from "@/components/workspace/TriggerRunButton";
import { WorkflowSchedulePanel } from "@/components/workspace/WorkflowSchedulePanel";
import { WorkflowDAG } from "@/components/workflow/WorkflowDAG";
import { PipelineEditor } from "@/components/workflow/editor";
import { publishPipeline, validatePipelineDefinition } from "@/lib/api";
import { stagesToYaml } from "@/lib/pipelineYaml";
import {
  ApiError,
  getPipeline,
  getPipelineVersionDefinition,
  listExecutions,
  type PipelineDetailResponse,
  type StageDefinition,
} from "@/lib/api";
import { formatShortDateTime, formatExecutionWallDuration } from "@/lib/format";

const tabs = [
  { id: "Overview", icon: LayoutDashboard },
  { id: "Editor", icon: Pencil },
  { id: "Runs", icon: Play },
  { id: "Schedule", icon: Calendar },
  { id: "Alerts", icon: Bell },
  { id: "Settings", icon: Settings },
] as const;

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/i;

export function WorkflowDetailPage() {
  const { workflowId } = useParams();
  const [tab, setTab] = useState<(typeof tabs)[number]["id"]>("Overview");
  const [pipeline, setPipeline] = useState<PipelineDetailResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [recentRuns, setRecentRuns] = useState<
    { id: string; status: string; label: string }[]
  >([]);
  const [runsError, setRunsError] = useState<string | null>(null);
  const [stages, setStages] = useState<StageDefinition[]>([]);

  const isUuid = workflowId ? UUID_RE.test(workflowId) : false;

  useEffect(() => {
    if (!workflowId || !isUuid) {
      setPipeline(null);
      setLoadError(null);
      setRecentRuns([]);
      setRunsError(null);
      setStages([]);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    setRunsError(null);

    void getPipeline(workflowId)
      .then((p) => {
        if (!cancelled) {
          setPipeline(p);
          void getPipelineVersionDefinition(workflowId, p.currentVersion)
            .then((def) => {
              if (!cancelled && def.definition?.stages) {
                setStages(def.definition.stages);
              }
            })
            .catch(() => {
              if (!cancelled) setStages([]);
            });
        }
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
    return <p className="text-sm text-neutral-500">Missing workflow id.</p>;
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
      <div className="flex flex-col gap-3">
        <nav className="text-sm text-neutral-500 dark:text-neutral-400" aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-1.5">
            <li>
              <Link to="/app/workflows" className="transition-colors hover:text-blue-600 dark:hover:text-blue-400">
                Workflows
              </Link>
            </li>
            <li aria-hidden className="text-neutral-300 dark:text-neutral-600">/</li>
            <li className="font-medium text-neutral-800 dark:text-neutral-200">
              {pipeline?.name ?? workflowId}
            </li>
          </ol>
        </nav>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="page-title flex items-center gap-3">
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-violet-600 text-white shadow-md shadow-violet-500/20 ring-1 ring-violet-400/20">
                <Workflow className="h-4 w-4" />
              </span>
              <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
                {pipeline?.name ?? "Workflow"}
              </span>
            </h2>
            {pipeline?.description && (
              <p className="mt-2 max-w-2xl text-[13px] leading-relaxed text-neutral-600 dark:text-neutral-300">{pipeline.description}</p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {pipeline && <StatusBadge status={pipeline.status} />}
            <span className="inline-flex items-center rounded-md bg-neutral-100/80 px-2 py-1 font-mono text-xs text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-400 dark:ring-neutral-700/50">
              v{pipeline?.currentVersion ?? "—"}
            </span>
            {pipeline && (
              <TriggerRunButton
                pipelineId={pipeline.id}
                pipelineVersion={pipeline.currentVersion}
                variant="primary"
                size="default"
                label="Run now"
                disabled={pipeline.status.toLowerCase() !== "active"}
              />
            )}
          </div>
        </div>
      </div>

      {loadError && (
        <Card className="border-rose-200/80 bg-gradient-to-br from-rose-50 to-white dark:border-rose-900/50 dark:from-rose-950/40 dark:to-neutral-950">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600 ring-1 ring-rose-200/50 dark:bg-rose-900/50 dark:text-rose-400 dark:ring-rose-800/50">
              <Workflow className="h-5 w-5" />
            </div>
            <div>
              <CardTitle className="text-[15px] text-rose-800 dark:text-rose-200">Could not load workflow</CardTitle>
              <CardDescription className="mt-1 text-rose-700/90 dark:text-rose-300/90">{loadError}</CardDescription>
            </div>
          </div>
        </Card>
      )}

      {loading && !pipeline && !loadError && (
        <p className="text-sm text-neutral-500">Loading workflow…</p>
      )}

      <Tabs value={tab} onValueChange={(v) => setTab(v as (typeof tabs)[number]["id"])}>
        <TabsList aria-label="Workflow sections">
          {tabs.map((t) => (
            <TabsTrigger key={t.id} value={t.id} className="gap-1.5">
              <t.icon className="h-3.5 w-3.5" aria-hidden />
              {t.id}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="Overview">
          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Pipeline stages</CardTitle>
                <CardDescription>
                  Visual DAG representation of pipeline stages (<span className="font-medium">US-12.05</span>).
                </CardDescription>
              </CardHeader>
              <WorkflowDAG stages={stages} className="max-h-80" />
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>Recent runs</CardTitle>
                <CardDescription>Latest executions for this pipeline (US-12.07).</CardDescription>
              </CardHeader>
              {runsError ? (
                <p className="text-sm text-rose-600 dark:text-rose-400">{runsError}</p>
              ) : recentRuns.length === 0 ? (
                <div className="flex flex-col items-center py-6 text-center">
                  <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
                    <Play className="h-4 w-4 text-neutral-400" />
                  </div>
                  <p className="text-[13px] text-neutral-500">No runs yet</p>
                </div>
              ) : (
                <ul className="space-y-2">
                  {recentRuns.map((r) => (
                    <li key={r.id}>
                      <Link
                        className="group block rounded-lg border border-neutral-200/80 p-3 transition-all hover:border-blue-200 hover:bg-blue-50/50 dark:border-neutral-800 dark:hover:border-blue-900/50 dark:hover:bg-blue-950/20"
                        to={`/app/runs/${r.id}`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-mono text-[12px] font-medium text-neutral-900 group-hover:text-blue-700 dark:text-neutral-100 dark:group-hover:text-blue-300">
                            {r.id.slice(0, 8)}…
                          </span>
                          <StatusBadge status={r.status} />
                        </div>
                        <p className="mt-1.5 text-[11px] text-neutral-500">
                          {r.label}
                        </p>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="Editor">
          {pipeline && workflowId ? (
            <div className="h-[calc(100vh-280px)] min-h-[500px]">
              <PipelineEditor
                pipelineId={workflowId}
                pipelineName={pipeline.name}
                pipelineDescription={pipeline.description ?? null}
                stages={stages}
                onPublish={async (updatedStages) => {
                  const yaml = stagesToYaml(pipeline.name, updatedStages, pipeline.description);
                  await validatePipelineDefinition(yaml);
                  await publishPipeline(workflowId, yaml);
                  const p = await getPipeline(workflowId);
                  setPipeline(p);
                  const def = await getPipelineVersionDefinition(workflowId, p.currentVersion);
                  if (def.definition?.stages) {
                    setStages(def.definition.stages);
                  }
                }}
              />
            </div>
          ) : null}
        </TabsContent>

        <TabsContent value="Runs">
          {workflowId && isUuid ? (
            <Card>
              <CardHeader>
                <CardTitle>Runs</CardTitle>
                <CardDescription>
                  Recent executions of this workflow
                </CardDescription>
              </CardHeader>
              <div className="p-4 pt-0">
                <WorkflowRunsTable pipelineId={workflowId} pipelineName={pipeline?.name} />
              </div>
            </Card>
          ) : null}
        </TabsContent>

        <TabsContent value="Schedule">
          {workflowId && isUuid ? (
            <WorkflowSchedulePanel pipelineId={workflowId} />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>Schedule</CardTitle>
                <CardDescription>Invalid workflow id.</CardDescription>
              </CardHeader>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="Alerts">
          {workflowId && isUuid ? (
            <AlertRulesPanel pipelineId={workflowId} embedded />
          ) : null}
        </TabsContent>

        <TabsContent value="Settings">
          {pipeline ? (
            <WorkflowSettings
              pipeline={pipeline}
              onUpdate={async (patch) => {
                console.log("Update workflow:", patch);
                // TODO: Implement API call to update pipeline
              }}
            />
          ) : null}
        </TabsContent>
      </Tabs>
    </div>
  );
}
