import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Calendar, LayoutDashboard, Pencil, Play, Settings } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Pill, StatusBadge } from "@/components/ui/Badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { TriggerRunButton } from "@/components/workspace/TriggerRunButton";
import { WorkflowSchedulePanel } from "@/components/workspace/WorkflowSchedulePanel";
import { WorkflowDAG } from "@/components/workflow/WorkflowDAG";
import { WorkflowDAGEditor } from "@/components/workflow/WorkflowDAGEditor";
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
      <div className="flex flex-col gap-2">
        <nav className="text-sm text-neutral-500 dark:text-neutral-400" aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-1">
            <li>
              <Link to="/app/workflows" className="hover:text-blue-600 dark:hover:text-blue-400">
                Workflows
              </Link>
            </li>
            <li aria-hidden>/</li>
            <li className="font-medium text-neutral-800 dark:text-neutral-200">
              {pipeline?.name ?? workflowId}
            </li>
          </ol>
        </nav>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="page-title">{pipeline?.name ?? "Workflow"}</h2>
            <p className="page-desc mt-1">
              Live header from <span className="font-medium">GET /api/v1/pipelines/{`{id}`}</span> (
              <span className="font-medium">US-12.05</span>).
            </p>
            {pipeline?.description && (
              <p className="mt-2 max-w-2xl text-[13px] text-neutral-600 dark:text-neutral-300">{pipeline.description}</p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {pipeline && <StatusBadge status={pipeline.status} />}
            <Pill className="font-mono text-xs normal-case">
              v{pipeline?.currentVersion ?? "—"}
            </Pill>
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
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load workflow</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{loadError}</CardDescription>
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
                <p className="text-sm text-neutral-500">No runs yet.</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {recentRuns.map((r) => (
                    <li key={r.id}>
                      <Link
                        className="font-medium text-blue-700 hover:underline dark:text-blue-300"
                        to={`/app/runs/${r.id}`}
                      >
                        {r.id.slice(0, 8)}…
                      </Link>
                      <p className="text-xs capitalize text-neutral-500">
                        {r.status} · {r.label}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="Editor">
          {pipeline && workflowId ? (
            <Card>
              <CardHeader>
                <CardTitle>Visual editor</CardTitle>
                <CardDescription>
                  Drag stages, connect dependencies, and publish (<span className="font-medium">US-12.06</span>).
                </CardDescription>
              </CardHeader>
              <WorkflowDAGEditor
                pipelineId={workflowId}
                pipelineName={pipeline.name}
                pipelineDescription={pipeline.description}
                initialStages={stages}
                onPublished={() => {
                  void getPipeline(workflowId).then((p) => {
                    setPipeline(p);
                    return getPipelineVersionDefinition(workflowId, p.currentVersion);
                  }).then((def) => {
                    if (def.definition?.stages) setStages(def.definition.stages);
                  });
                }}
              />
            </Card>
          ) : null}
        </TabsContent>

        <TabsContent value="Runs">
          <Card>
            <CardHeader>
              <CardTitle>Runs</CardTitle>
              <CardDescription>
                Open the{" "}
                <Link className="font-medium text-blue-600 hover:underline dark:text-blue-400" to="/app/runs">
                  Runs
                </Link>{" "}
                page and filter by this pipeline in a later iteration.
              </CardDescription>
            </CardHeader>
          </Card>
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

        <TabsContent value="Settings">
          <Card>
            <CardHeader>
              <CardTitle>Settings</CardTitle>
              <CardDescription>Content for this tab is not implemented yet.</CardDescription>
            </CardHeader>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
