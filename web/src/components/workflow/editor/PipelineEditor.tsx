import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Connection,
  useReactFlow,
  ReactFlowProvider,
  Panel,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useRef, useState, type DragEvent } from "react";
import type { StageDefinition } from "@/lib/api";
import { cn } from "@/lib/cn";
import { useFullscreen } from "@/hooks/useFullscreen";
import { useEditorStore, type StageType } from "./editorStore";
import { StageNode } from "./StageNode";
import { StagePalette } from "./StagePalette";
import { StageConfigPanel } from "./StageConfigPanel";
import { EditorToolbar } from "./EditorToolbar";
import { ValidationPanel } from "./ValidationPanel";
import { useKeyboardShortcuts } from "./useKeyboardShortcuts";

const nodeTypes = {
  stageNode: StageNode,
};

type PipelineEditorContentProps = {
  pipelineId: string;
  pipelineName: string;
  pipelineDescription: string | null;
  stages: StageDefinition[];
  onPublish: (stages: StageDefinition[]) => Promise<void>;
};

function PipelineEditorContent({
  pipelineId,
  pipelineName,
  pipelineDescription,
  stages,
  onPublish,
}: PipelineEditorContentProps) {
  const reactFlowInstance = useReactFlow();
  const containerRef = useRef<HTMLDivElement>(null);
  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [publishSuccess, setPublishSuccess] = useState<string | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(true);
  const [configOpen, setConfigOpen] = useState(true);
  const [configExpanded, setConfigExpanded] = useState(false);
  const { isFullscreen, toggleFullscreen } = useFullscreen();

  const initialize = useEditorStore((s) => s.initialize);
  const onNodesChange = useEditorStore((s) => s.onNodesChange);
  const onEdgesChange = useEditorStore((s) => s.onEdgesChange);
  const connectNodes = useEditorStore((s) => s.connectNodes);
  const addNode = useEditorStore((s) => s.addNode);
  const getStages = useEditorStore((s) => s.getStages);
  const selectNode = useEditorStore((s) => s.selectNode);

  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  useKeyboardShortcuts({ suppressEscape: isFullscreen || configExpanded });

  useEffect(() => {
    if (!configOpen) {
      setConfigExpanded(false);
    }
  }, [configOpen]);

  useEffect(() => {
    if (!configExpanded) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        setConfigExpanded(false);
      }
    };
    window.addEventListener("keydown", onKeyDown, true);
    return () => window.removeEventListener("keydown", onKeyDown, true);
  }, [configExpanded]);

  // Initialize store
  useEffect(() => {
    initialize(pipelineId, pipelineName, pipelineDescription, stages);
  }, [initialize, pipelineId, pipelineName, pipelineDescription, stages]);

  const handleNodesChange = useCallback(
    (changes: Parameters<typeof onNodesChange>[0]) => {
      onNodesChange(changes);
    },
    [onNodesChange]
  );

  const handleEdgesChange = useCallback(
    (changes: Parameters<typeof onEdgesChange>[0]) => {
      onEdgesChange(changes);
    },
    [onEdgesChange]
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      connectNodes(connection);
    },
    [connectNodes]
  );

  // Drag and drop from palette
  const handleDragOver = useCallback((event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  }, []);

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();

      const stageType = event.dataTransfer.getData("application/pravah-stage-type");
      if (!stageType) return;

      const container = containerRef.current;
      if (!container) return;

      const rect = container.getBoundingClientRect();
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
      });

      addNode(stageType as StageType, position);
    },
    [reactFlowInstance, addNode]
  );

  const handlePublish = useCallback(async () => {
    setPublishing(true);
    setPublishError(null);
    setPublishSuccess(null);

    try {
      const currentStages = getStages();
      await onPublish(currentStages);
      setPublishSuccess("Published successfully!");
      setTimeout(() => setPublishSuccess(null), 3000);
    } catch (error) {
      setPublishError(error instanceof Error ? error.message : "Failed to publish");
    } finally {
      setPublishing(false);
    }
  }, [onPublish, getStages]);

  const handleJumpToNode = useCallback(
    (nodeId: string) => {
      selectNode(nodeId);
      const node = nodes.find((n) => n.id === nodeId);
      if (node) {
        reactFlowInstance.setCenter(node.position.x + 80, node.position.y + 40, {
          duration: 500,
          zoom: 1,
        });
      }
    },
    [nodes, selectNode, reactFlowInstance]
  );

  return (
    <div
      className={cn(
        "flex h-full flex-col gap-4",
        isFullscreen &&
          "fixed inset-0 z-[100] gap-3 bg-neutral-50 p-3 dark:bg-neutral-950 sm:p-4",
      )}
    >
      {isFullscreen && (
        <div className="flex shrink-0 items-center justify-between gap-3 px-1">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-neutral-500">Pipeline editor</p>
            <p className="text-sm font-semibold text-neutral-900 dark:text-neutral-100">{pipelineName}</p>
          </div>
          <p className="hidden text-xs text-neutral-500 sm:block">Press Esc to exit fullscreen</p>
        </div>
      )}

      <EditorToolbar
        onPublish={handlePublish}
        publishing={publishing}
        publishError={publishError}
        publishSuccess={publishSuccess}
        isFullscreen={isFullscreen}
        onToggleFullscreen={toggleFullscreen}
        paletteOpen={paletteOpen}
        onTogglePalette={() => setPaletteOpen((open) => !open)}
        configOpen={configOpen}
        onToggleConfig={() => setConfigOpen((open) => !open)}
      />

      <div className="relative flex flex-1 overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-sm dark:border-neutral-800 dark:bg-neutral-900">
        {paletteOpen && (
          <div className="w-64 shrink-0 overflow-y-auto border-r border-neutral-200 p-4 dark:border-neutral-800">
            <StagePalette />
          </div>
        )}

        <div
          ref={containerRef}
          className="relative min-w-0 flex-1 h-full min-h-[420px]"
          onDragOver={handleDragOver}
          onDrop={handleDrop}
        >
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={handleNodesChange}
            onEdgesChange={handleEdgesChange}
            onConnect={handleConnect}
            fitView
            fitViewOptions={{ padding: 0.3, maxZoom: 1 }}
            deleteKeyCode={null}
            proOptions={{ hideAttribution: true }}
            className="h-full bg-neutral-50 dark:bg-neutral-950"
          >
            <Background color="#e5e7eb" gap={16} />
            <Controls
              showInteractive={false}
              className="!bg-white !shadow-md dark:!bg-neutral-800 [&>button]:!border-neutral-200 [&>button]:!bg-white [&>button:hover]:!bg-neutral-100 dark:[&>button]:!border-neutral-700 dark:[&>button]:!bg-neutral-800 dark:[&>button:hover]:!bg-neutral-700 [&>button>svg]:!fill-neutral-600 dark:[&>button>svg]:!fill-neutral-300"
            />
            <MiniMap
              nodeColor={(node) => {
                const type = node.data?.stageType;
                switch (type) {
                  case "sql":
                    return "#fcd34d";
                  case "container":
                    return "#a78bfa";
                  case "python":
                    return "#60a5fa";
                  case "dbt":
                    return "#fb923c";
                  case "spark":
                    return "#fb7185";
                  default:
                    return "#4ade80";
                }
              }}
              className="!bg-white !shadow-md dark:!bg-neutral-800 !border !border-neutral-200 dark:!border-neutral-700"
              maskColor="rgba(0, 0, 0, 0.1)"
            />
            <Panel position="bottom-center" className="mb-2">
              <ValidationPanel onJumpToNode={handleJumpToNode} />
            </Panel>
          </ReactFlow>
        </div>

        {configExpanded && (
          <button
            type="button"
            aria-label="Close expanded configuration panel"
            className="absolute inset-0 z-10 bg-neutral-950/20 dark:bg-neutral-950/40"
            onClick={() => setConfigExpanded(false)}
          />
        )}

        {configOpen && (
          <aside
            className={cn(
              "shrink-0 overflow-y-auto border-l border-neutral-200 bg-white p-4 dark:border-neutral-800 dark:bg-neutral-900",
              configExpanded
                ? "absolute inset-y-0 right-0 z-20 w-[min(36rem,55vw)] shadow-2xl ring-1 ring-neutral-200/80 dark:ring-neutral-700/80"
                : "w-96",
            )}
          >
            <StageConfigPanel
              expanded={configExpanded}
              onToggleExpand={() => setConfigExpanded((value) => !value)}
            />
          </aside>
        )}
      </div>
    </div>
  );
}

type PipelineEditorProps = PipelineEditorContentProps;

export function PipelineEditor(props: PipelineEditorProps) {
  return (
    <ReactFlowProvider>
      <PipelineEditorContent {...props} />
    </ReactFlowProvider>
  );
}
