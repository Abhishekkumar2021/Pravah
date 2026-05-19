import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { Box, Circle, Container, Database, FileCode2, Sparkles, AlertTriangle } from "lucide-react";
import { memo } from "react";
import { cn } from "@/lib/cn";
import { useEditorStore, type StageNodeData, type StageType } from "./editorStore";

function stageTypeIcon(type: StageType) {
  const iconClass = "h-4 w-4 shrink-0";
  switch (type) {
    case "sql":
      return <Database className={iconClass} />;
    case "container":
      return <Container className={iconClass} />;
    case "python":
      return <FileCode2 className={iconClass} />;
    case "dbt":
      return <Box className={iconClass} />;
    case "spark":
      return <Sparkles className={iconClass} />;
    default:
      return <Circle className={iconClass} />;
  }
}

function nodeColorClass(type: StageType, selected: boolean, hasError: boolean): string {
  const base = (() => {
    switch (type) {
      case "sql":
        return "border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-950/40";
      case "container":
        return "border-violet-300 bg-violet-50 dark:border-violet-700 dark:bg-violet-950/40";
      case "python":
        return "border-blue-300 bg-blue-50 dark:border-blue-700 dark:bg-blue-950/40";
      case "dbt":
        return "border-orange-300 bg-orange-50 dark:border-orange-700 dark:bg-orange-950/40";
      case "spark":
        return "border-rose-300 bg-rose-50 dark:border-rose-700 dark:bg-rose-950/40";
      default:
        return "border-emerald-300 bg-emerald-50 dark:border-emerald-700 dark:bg-emerald-950/40";
    }
  })();

  if (hasError) {
    return cn(base, "border-rose-500 dark:border-rose-500 ring-2 ring-rose-500/30");
  }

  if (selected) {
    return cn(base, "ring-2 ring-blue-500 ring-offset-2 dark:ring-offset-neutral-950");
  }

  return base;
}

function StageNodeComponent({ data, selected }: NodeProps<Node<StageNodeData>>) {
  const validationErrors = useEditorStore((state) => state.validationErrors);
  const nodeErrors = validationErrors.filter((e) => e.nodeId === data.stage.id);
  const hasError = nodeErrors.some((e) => e.severity === "error");
  const hasWarning = !hasError && nodeErrors.some((e) => e.severity === "warning");

  return (
    <div
      className={cn(
        "min-w-[160px] rounded-lg border-2 px-3 py-2.5 shadow-sm transition-all duration-150",
        "hover:shadow-md",
        nodeColorClass(data.stageType, !!selected, hasError),
      )}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!h-3 !w-3 !bg-neutral-400 !border-2 !border-white dark:!border-neutral-900 hover:!bg-blue-500 transition-colors"
      />
      
      <div className="flex items-center gap-2">
        <div className={cn(
          "flex h-8 w-8 items-center justify-center rounded-md",
          hasError 
            ? "bg-rose-100 text-rose-600 dark:bg-rose-900/50 dark:text-rose-400"
            : hasWarning
            ? "bg-amber-100 text-amber-600 dark:bg-amber-900/50 dark:text-amber-400"
            : "bg-white/60 text-neutral-600 dark:bg-neutral-800/60 dark:text-neutral-300"
        )}>
          {hasError || hasWarning ? (
            <AlertTriangle className="h-4 w-4" />
          ) : (
            stageTypeIcon(data.stageType)
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-neutral-900 dark:text-neutral-100">
            {data.label}
          </p>
          <p className="text-[10px] font-medium uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
            {data.stageType}
          </p>
        </div>
      </div>

      {nodeErrors.length > 0 && (
        <div className="mt-2 border-t border-neutral-200/60 pt-2 dark:border-neutral-700/60">
          <p className={cn(
            "truncate text-[10px]",
            hasError ? "text-rose-600 dark:text-rose-400" : "text-amber-600 dark:text-amber-400"
          )}>
            {nodeErrors[0].message}
          </p>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Bottom}
        className="!h-3 !w-3 !bg-neutral-400 !border-2 !border-white dark:!border-neutral-900 hover:!bg-blue-500 transition-colors"
      />
    </div>
  );
}

export const StageNode = memo(StageNodeComponent);
