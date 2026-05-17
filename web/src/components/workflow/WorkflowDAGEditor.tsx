import {
  Background,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  Box,
  Circle,
  Container,
  Database,
  Download,
  FileCode2,
  Redo2,
  Save,
  Sparkles,
  Undo2,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { StageDefinition } from "@/lib/api";
import { publishPipeline, validatePipelineDefinition } from "@/lib/api";
import { cn } from "@/lib/cn";
import { stagesToYaml } from "@/lib/pipelineYaml";
import {
  STAGE_TYPE_OPTIONS,
  createStage,
  defaultConfigForType,
} from "@/lib/stageDefaults";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";

type StageNodeData = {
  stage: StageDefinition;
  label: string;
  stageType: string;
};

type WorkflowDAGEditorProps = {
  pipelineId: string;
  pipelineName: string;
  pipelineDescription?: string | null;
  initialStages: StageDefinition[];
  onPublished?: () => void;
  className?: string;
};

type EditorSnapshot = { nodes: Node<StageNodeData>[]; edges: Edge[] };

function stageTypeIcon(type: string) {
  switch (type.toLowerCase()) {
    case "sql":
      return <Database className="h-4 w-4 shrink-0" />;
    case "container":
      return <Container className="h-4 w-4 shrink-0" />;
    case "python":
      return <FileCode2 className="h-4 w-4 shrink-0" />;
    case "dbt":
      return <Box className="h-4 w-4 shrink-0" />;
    case "spark":
      return <Sparkles className="h-4 w-4 shrink-0" />;
    default:
      return <Circle className="h-4 w-4 shrink-0" />;
  }
}

function nodeColorClass(type: string): string {
  switch (type.toLowerCase()) {
    case "sql":
      return "border-amber-300 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/40";
    case "container":
      return "border-violet-300 bg-violet-50 dark:border-violet-800 dark:bg-violet-950/40";
    case "python":
      return "border-blue-300 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/40";
    case "dbt":
      return "border-orange-300 bg-orange-50 dark:border-orange-800 dark:bg-orange-950/40";
    case "spark":
      return "border-rose-300 bg-rose-50 dark:border-rose-800 dark:bg-rose-950/40";
    default:
      return "border-emerald-300 bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-950/40";
  }
}

function StageFlowNode({ data, selected }: NodeProps<Node<StageNodeData>>) {
  return (
    <div
      className={cn(
        "min-w-[148px] rounded-lg border-2 px-3 py-2.5 shadow-sm",
        nodeColorClass(data.stageType),
        selected && "ring-2 ring-blue-500 ring-offset-2 dark:ring-offset-neutral-950",
      )}
    >
      <Handle type="target" position={Position.Top} className="!h-2 !w-2 !bg-neutral-400" />
      <div className="flex items-center gap-2">
        {stageTypeIcon(data.stageType)}
        <div className="min-w-0">
          <p className="truncate text-xs font-semibold text-neutral-900 dark:text-neutral-100">
            {data.label}
          </p>
          <p className="text-[10px] uppercase tracking-wide text-neutral-500">{data.stageType}</p>
        </div>
      </div>
      <Handle type="source" position={Position.Bottom} className="!h-2 !w-2 !bg-neutral-400" />
    </div>
  );
}

const nodeTypes = { stage: StageFlowNode };

function stagesToNodes(stages: StageDefinition[]): Node<StageNodeData>[] {
  const colWidth = 220;
  const rowHeight = 120;
  return stages.map((stage, index) => ({
    id: stage.id,
    type: "stage",
    position: { x: (index % 3) * colWidth + 40, y: Math.floor(index / 3) * rowHeight + 40 },
    data: {
      stage,
      label: stage.name ?? stage.id,
      stageType: stage.type ?? "echo",
    },
  }));
}

function stagesToEdges(stages: StageDefinition[]): Edge[] {
  const edges: Edge[] = [];
  for (const stage of stages) {
    for (const dep of stage.dependsOn ?? stage.depends_on ?? []) {
      edges.push({ id: `${dep}->${stage.id}`, source: dep, target: stage.id, animated: true });
    }
  }
  return edges;
}

function nodesToStages(nodes: Node<StageNodeData>[], edges: Edge[]): StageDefinition[] {
  const depsByTarget = new Map<string, string[]>();
  for (const edge of edges) {
    const list = depsByTarget.get(edge.target) ?? [];
    list.push(edge.source);
    depsByTarget.set(edge.target, list);
  }
  return nodes.map((node) => ({
    ...node.data.stage,
    dependsOn: depsByTarget.get(node.data.stage.id) ?? [],
  }));
}

export function WorkflowDAGEditor({
  pipelineId,
  pipelineName,
  pipelineDescription,
  initialStages,
  onPublished,
  className,
}: WorkflowDAGEditorProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<StageNodeData>>(
    stagesToNodes(initialStages),
  );
  const [edges, setEdges, onEdgesChange] = useEdgesState(stagesToEdges(initialStages));
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [history, setHistory] = useState<EditorSnapshot[]>([]);
  const [future, setFuture] = useState<EditorSnapshot[]>([]);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [stageCounter, setStageCounter] = useState(initialStages.length + 1);

  useEffect(() => {
    setNodes(stagesToNodes(initialStages));
    setEdges(stagesToEdges(initialStages));
    setStageCounter(initialStages.length + 1);
  }, [initialStages, setNodes, setEdges]);

  const pushHistory = useCallback(() => {
    setHistory((h) => [...h.slice(-49), { nodes, edges }]);
    setFuture([]);
  }, [nodes, edges]);

  const onConnect = useCallback(
    (connection: Connection) => {
      pushHistory();
      setEdges((eds) => addEdge({ ...connection, animated: true }, eds));
    },
    [pushHistory, setEdges],
  );

  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedId) ?? null,
    [nodes, selectedId],
  );
  const stages = useMemo(() => nodesToStages(nodes, edges), [nodes, edges]);
  const yaml = stagesToYaml(pipelineName, stages, pipelineDescription);

  function undo() {
    if (history.length === 0) return;
    const prev = history[history.length - 1]!;
    setFuture((f) => [{ nodes, edges }, ...f]);
    setHistory((h) => h.slice(0, -1));
    setNodes(prev.nodes);
    setEdges(prev.edges);
  }

  function redo() {
    if (future.length === 0) return;
    const next = future[0]!;
    setHistory((h) => [...h, { nodes, edges }]);
    setFuture((f) => f.slice(1));
    setNodes(next.nodes);
    setEdges(next.edges);
  }

  function addStage(type: string) {
    pushHistory();
    const stage = createStage(type, stageCounter);
    setStageCounter((c) => c + 1);
    setNodes((nds) => [
      ...nds,
      {
        id: stage.id,
        type: "stage",
        position: { x: 80 + (nds.length % 3) * 200, y: 80 + Math.floor(nds.length / 3) * 120 },
        data: { stage, label: stage.name ?? stage.id, stageType: type },
      },
    ]);
  }

  function updateSelectedStage(patch: Partial<StageDefinition>) {
    if (!selectedNode) return;
    pushHistory();
    const oldId = selectedNode.id;
    setNodes((nds) =>
      nds.map((n) => {
        if (n.id !== oldId) return n;
        const updated = { ...n.data.stage, ...patch };
        const newId = patch.id?.trim() || oldId;
        return {
          ...n,
          id: newId,
          data: {
            stage: { ...updated, id: newId },
            label: updated.name ?? newId,
            stageType: updated.type ?? "echo",
          },
        };
      }),
    );
    if (patch.id && patch.id !== oldId) {
      setSelectedId(patch.id);
      setEdges((eds) =>
        eds.map((e) => ({
          ...e,
          id: e.id.replace(oldId, patch.id!),
          source: e.source === oldId ? patch.id! : e.source,
          target: e.target === oldId ? patch.id! : e.target,
        })),
      );
    }
  }

  function deleteSelected() {
    if (!selectedId) return;
    pushHistory();
    setNodes((nds) => nds.filter((n) => n.id !== selectedId));
    setEdges((eds) => eds.filter((e) => e.source !== selectedId && e.target !== selectedId));
    setSelectedId(null);
  }

  function downloadYaml() {
    const blob = new Blob([yaml], { type: "text/yaml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${pipelineName.replace(/\s+/g, "-").toLowerCase()}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function handlePublish() {
    setPublishing(true);
    setError(null);
    setMessage(null);
    try {
      await validatePipelineDefinition(yaml);
      await publishPipeline(pipelineId, yaml);
      setMessage("Published successfully");
      onPublished?.();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Publish failed");
    } finally {
      setPublishing(false);
    }
  }

  return (
    <div className={cn("flex flex-col gap-4 xl:flex-row", className)}>
      <aside className="w-full shrink-0 space-y-3 xl:w-52">
        <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500">Add stage</p>
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
          {STAGE_TYPE_OPTIONS.map((opt) => (
            <Button
              key={opt.type}
              type="button"
              variant="secondary"
              className="h-auto justify-start gap-2 px-3 py-2 text-left"
              onClick={() => addStage(opt.type)}
            >
              {stageTypeIcon(opt.type)}
              <span>
                <span className="block text-xs font-medium">{opt.label}</span>
                <span className="block text-[10px] font-normal text-neutral-500">
                  {opt.description}
                </span>
              </span>
            </Button>
          ))}
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={undo} disabled={history.length === 0}>
            <Undo2 className="h-3.5 w-3.5" aria-hidden />
            Undo
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={redo} disabled={future.length === 0}>
            <Redo2 className="h-3.5 w-3.5" aria-hidden />
            Redo
          </Button>
        </div>
      </aside>

      <div className="h-[min(520px,60vh)] min-h-[360px] flex-1 overflow-hidden rounded-xl border border-neutral-200 bg-neutral-50 dark:border-neutral-800 dark:bg-neutral-900/40">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          nodeTypes={nodeTypes}
          fitView
          onNodeClick={(_, node) => setSelectedId(node.id)}
          onPaneClick={() => setSelectedId(null)}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={16} />
          <Controls />
          <MiniMap zoomable pannable className="!bg-white dark:!bg-neutral-900" />
        </ReactFlow>
      </div>

      <aside className="w-full shrink-0 space-y-4 xl:w-72">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500">Stage config</p>
          {selectedNode ? (
            <div className="mt-3 space-y-3">
              <div>
                <Label htmlFor="stage-id">Stage ID</Label>
                <Input
                  id="stage-id"
                  value={selectedNode.data.stage.id}
                  onChange={(e) => updateSelectedStage({ id: e.target.value.trim() })}
                  className="mt-1 font-mono text-xs"
                />
              </div>
              <div>
                <Label htmlFor="stage-name">Name</Label>
                <Input
                  id="stage-name"
                  value={selectedNode.data.stage.name ?? ""}
                  onChange={(e) => updateSelectedStage({ name: e.target.value })}
                  className="mt-1"
                />
              </div>
              <div>
                <Label htmlFor="stage-type">Type</Label>
                <Select
                  id="stage-type"
                  aria-label="Stage type"
                  value={selectedNode.data.stage.type ?? "echo"}
                  onValueChange={(v) =>
                    updateSelectedStage({ type: v, config: defaultConfigForType(v) })
                  }
                  options={STAGE_TYPE_OPTIONS.map((o) => ({ value: o.type, label: o.label }))}
                  className="mt-1 w-full"
                />
              </div>
              <Button
                type="button"
                variant="secondary"
                className="w-full text-rose-600 dark:text-rose-400"
                onClick={deleteSelected}
              >
                Remove stage
              </Button>
            </div>
          ) : (
            <p className="mt-2 text-sm text-neutral-500">
              Select a stage, then drag from its bottom handle to another stage&apos;s top handle to
              set dependencies.
            </p>
          )}
        </div>
        <div className="space-y-2 border-t border-neutral-200 pt-4 dark:border-neutral-800">
          <Button type="button" variant="secondary" className="w-full gap-2" onClick={downloadYaml}>
            <Download className="h-4 w-4" aria-hidden />
            Export YAML
          </Button>
          <Button
            type="button"
            variant="primary"
            className="w-full gap-2"
            disabled={publishing || stages.length === 0}
            onClick={() => void handlePublish()}
          >
            <Save className="h-4 w-4" aria-hidden />
            {publishing ? "Publishing…" : "Validate & publish"}
          </Button>
          {message && <p className="text-xs text-emerald-600 dark:text-emerald-400">{message}</p>}
          {error && <p className="text-xs text-rose-600 dark:text-rose-400">{error}</p>}
        </div>
      </aside>
    </div>
  );
}
