import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Connection,
  type OnNodesChange,
  type OnEdgesChange,
  useReactFlow,
  ReactFlowProvider,
  Panel,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useRef, useState, type DragEvent } from "react";
import type { StageDefinition } from "@/lib/api";
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

  const initialize = useEditorStore((s) => s.initialize);
  const onNodesChange = useEditorStore((s) => s.onNodesChange);
  const onEdgesChange = useEditorStore((s) => s.onEdgesChange);
  const connectNodes = useEditorStore((s) => s.connectNodes);
  const addNode = useEditorStore((s) => s.addNode);
  const getStages = useEditorStore((s) => s.getStages);
  const selectNode = useEditorStore((s) => s.selectNode);

  const nodes = useEditorStore((s) => s.nodes);
  const edges = useEditorStore((s) => s.edges);

  useKeyboardShortcuts();

  // Initialize store
  useEffect(() => {
    initialize(pipelineId, pipelineName, pipelineDescription, stages);
  }, [initialize, pipelineId, pipelineName, pipelineDescription, stages]);

  const handleNodesChange: OnNodesChange = useCallback(
    (changes) => {
      onNodesChange(changes);
    },
    [onNodesChange]
  );

  const handleEdgesChange: OnEdgesChange = useCallback(
    (changes) => {
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
    <div className="flex h-full flex-col gap-4">
      <EditorToolbar
        onPublish={handlePublish}
        publishing={publishing}
        publishError={publishError}
        publishSuccess={publishSuccess}
      />

      <div className="flex flex-1 gap-4 overflow-hidden rounded-lg border border-neutral-200 bg-white shadow-sm dark:border-neutral-800 dark:bg-neutral-900">
        {/* Left sidebar: Palette */}
        <div className="w-64 shrink-0 overflow-y-auto border-r border-neutral-200 p-4 dark:border-neutral-800">
          <StagePalette />
        </div>

        {/* Center: Canvas */}
        <div
          ref={containerRef}
          className="flex-1"
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
            className="bg-neutral-50 dark:bg-neutral-950"
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

        {/* Right sidebar: Config */}
        <div className="w-80 shrink-0 overflow-y-auto border-l border-neutral-200 p-4 dark:border-neutral-800">
          <StageConfigPanel />
        </div>
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
