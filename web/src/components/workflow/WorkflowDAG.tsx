import { useMemo } from "react";
import { Box, Circle, Container } from "lucide-react";
import type { StageDefinition } from "@/lib/api";
import { cn } from "@/lib/cn";

type WorkflowDAGProps = {
  stages: StageDefinition[];
  className?: string;
};

type StageNode = {
  id: string;
  name: string;
  type: string;
  dependsOn: string[];
  level: number;
  column: number;
};

type Edge = {
  from: string;
  to: string;
};

function getStageTypeIcon(type: string) {
  switch (type.toLowerCase()) {
    case "container":
      return <Container className="h-4 w-4" />;
    case "sql":
      return <Box className="h-4 w-4" />;
    default:
      return <Circle className="h-4 w-4" />;
  }
}

function getStageTypeColor(type: string) {
  switch (type.toLowerCase()) {
    case "container":
      return "bg-violet-100 border-violet-300 text-violet-700 dark:bg-violet-950 dark:border-violet-800 dark:text-violet-300";
    case "sql":
      return "bg-amber-100 border-amber-300 text-amber-700 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-300";
    case "echo":
      return "bg-emerald-100 border-emerald-300 text-emerald-700 dark:bg-emerald-950 dark:border-emerald-800 dark:text-emerald-300";
    default:
      return "bg-neutral-100 border-neutral-300 text-neutral-700 dark:bg-neutral-800 dark:border-neutral-700 dark:text-neutral-300";
  }
}

function buildDAGLayout(stages: StageDefinition[]): { nodes: StageNode[]; edges: Edge[] } {
  if (stages.length === 0) {
    return { nodes: [], edges: [] };
  }

  const stageMap = new Map<string, StageDefinition>();
  for (const s of stages) {
    stageMap.set(s.id, s);
  }

  const levels = new Map<string, number>();
  const edges: Edge[] = [];

  function computeLevel(stageId: string, visited: Set<string>): number {
    if (levels.has(stageId)) {
      return levels.get(stageId)!;
    }
    if (visited.has(stageId)) {
      return 0;
    }
    visited.add(stageId);

    const stage = stageMap.get(stageId);
    const deps = stage?.dependsOn ?? stage?.depends_on ?? [];

    if (deps.length === 0) {
      levels.set(stageId, 0);
      return 0;
    }

    let maxParentLevel = -1;
    for (const dep of deps) {
      edges.push({ from: dep, to: stageId });
      const parentLevel = computeLevel(dep, visited);
      maxParentLevel = Math.max(maxParentLevel, parentLevel);
    }
    const level = maxParentLevel + 1;
    levels.set(stageId, level);
    return level;
  }

  for (const stage of stages) {
    computeLevel(stage.id, new Set());
  }

  const levelGroups = new Map<number, string[]>();
  for (const [stageId, level] of levels) {
    if (!levelGroups.has(level)) {
      levelGroups.set(level, []);
    }
    levelGroups.get(level)!.push(stageId);
  }

  const nodes: StageNode[] = [];
  for (const [level, stageIds] of levelGroups) {
    stageIds.forEach((stageId, column) => {
      const stage = stageMap.get(stageId)!;
      nodes.push({
        id: stage.id,
        name: stage.name ?? stage.id,
        type: stage.type ?? "unknown",
        dependsOn: stage.dependsOn ?? stage.depends_on ?? [],
        level,
        column,
      });
    });
  }

  const uniqueEdges = Array.from(new Set(edges.map((e) => `${e.from}:${e.to}`))).map((key) => {
    const [from, to] = key.split(":");
    return { from, to };
  });

  return { nodes, edges: uniqueEdges };
}

export function WorkflowDAG({ stages, className }: WorkflowDAGProps) {
  const { nodes, edges } = useMemo(() => buildDAGLayout(stages), [stages]);

  if (nodes.length === 0) {
    return (
      <div
        className={cn(
          "flex items-center justify-center rounded-xl border border-dashed border-neutral-300 bg-neutral-50 py-12 text-sm text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900/50 dark:text-neutral-400",
          className,
        )}
      >
        No stages defined
      </div>
    );
  }

  const maxLevel = Math.max(...nodes.map((n) => n.level));
  const maxColumnByLevel = new Map<number, number>();
  for (const node of nodes) {
    maxColumnByLevel.set(
      node.level,
      Math.max(maxColumnByLevel.get(node.level) ?? 0, node.column),
    );
  }

  const nodeWidth = 160;
  const nodeHeight = 56;
  const levelGap = 100;
  const columnGap = 40;
  const padding = 32;

  const maxColumns = Math.max(...Array.from(maxColumnByLevel.values())) + 1;
  const svgWidth = maxColumns * nodeWidth + (maxColumns - 1) * columnGap + padding * 2;
  const svgHeight = (maxLevel + 1) * nodeHeight + maxLevel * levelGap + padding * 2;

  const getNodePosition = (node: StageNode) => {
    const totalInLevel = (maxColumnByLevel.get(node.level) ?? 0) + 1;
    const levelWidth = totalInLevel * nodeWidth + (totalInLevel - 1) * columnGap;
    const startX = (svgWidth - levelWidth) / 2;
    const x = startX + node.column * (nodeWidth + columnGap);
    const y = padding + node.level * (nodeHeight + levelGap);
    return { x, y };
  };

  const nodePositions = new Map<string, { x: number; y: number }>();
  for (const node of nodes) {
    nodePositions.set(node.id, getNodePosition(node));
  }

  return (
    <div className={cn("overflow-auto rounded-xl border border-neutral-200 bg-neutral-50 dark:border-neutral-800 dark:bg-neutral-900/50", className)}>
      <svg width={svgWidth} height={svgHeight} className="min-w-full">
        <defs>
          <marker
            id="arrowhead"
            markerWidth="10"
            markerHeight="7"
            refX="9"
            refY="3.5"
            orient="auto"
          >
            <polygon
              points="0 0, 10 3.5, 0 7"
              className="fill-neutral-400 dark:fill-neutral-600"
            />
          </marker>
        </defs>

        {edges.map((edge) => {
          const fromPos = nodePositions.get(edge.from);
          const toPos = nodePositions.get(edge.to);
          if (!fromPos || !toPos) return null;

          const x1 = fromPos.x + nodeWidth / 2;
          const y1 = fromPos.y + nodeHeight;
          const x2 = toPos.x + nodeWidth / 2;
          const y2 = toPos.y;

          const midY = (y1 + y2) / 2;

          return (
            <path
              key={`${edge.from}-${edge.to}`}
              d={`M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`}
              fill="none"
              className="stroke-neutral-300 dark:stroke-neutral-700"
              strokeWidth={2}
              markerEnd="url(#arrowhead)"
            />
          );
        })}

        {nodes.map((node) => {
          const pos = nodePositions.get(node.id)!;
          return (
            <g key={node.id} transform={`translate(${pos.x}, ${pos.y})`}>
              <rect
                width={nodeWidth}
                height={nodeHeight}
                rx={8}
                className={cn(
                  "fill-white stroke-neutral-200 dark:fill-neutral-950 dark:stroke-neutral-800",
                )}
                strokeWidth={1.5}
              />
              <foreignObject x={0} y={0} width={nodeWidth} height={nodeHeight}>
                <div className="flex h-full flex-col items-center justify-center gap-1 px-2">
                  <div
                    className={cn(
                      "flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-[10px] font-medium",
                      getStageTypeColor(node.type),
                    )}
                  >
                    {getStageTypeIcon(node.type)}
                    {node.type}
                  </div>
                  <p className="max-w-full truncate text-center text-xs font-medium text-neutral-800 dark:text-neutral-200">
                    {node.name}
                  </p>
                </div>
              </foreignObject>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
