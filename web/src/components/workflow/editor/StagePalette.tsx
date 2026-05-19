import { Box, Circle, Container, Database, FileCode2, GripVertical, Sparkles } from "lucide-react";
import type { DragEvent } from "react";
import { cn } from "@/lib/cn";
import { STAGE_TYPES, STAGE_TYPE_META, useEditorStore, type StageType } from "./editorStore";

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

type PaletteItemProps = {
  type: StageType;
  label: string;
  description: string;
  onAdd: () => void;
};

function PaletteItem({ type, label, description, onAdd }: PaletteItemProps) {
  const handleDragStart = (event: DragEvent<HTMLButtonElement>) => {
    event.dataTransfer.setData("application/pravah-stage-type", type);
    event.dataTransfer.effectAllowed = "copy";
  };

  return (
    <button
      type="button"
      draggable
      onDragStart={handleDragStart}
      onClick={onAdd}
      className={cn(
        "group flex w-full items-center gap-2 rounded-lg border border-neutral-200 bg-white px-3 py-2.5 text-left",
        "shadow-sm transition-all duration-150",
        "hover:border-blue-300 hover:bg-blue-50/50 hover:shadow-md",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2",
        "active:scale-[0.98]",
        "dark:border-neutral-700 dark:bg-neutral-800/50 dark:hover:border-blue-700 dark:hover:bg-blue-950/30"
      )}
    >
      <GripVertical className="h-4 w-4 shrink-0 text-neutral-300 group-hover:text-neutral-400 dark:text-neutral-600" />
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-neutral-100 text-neutral-600 group-hover:bg-blue-100 group-hover:text-blue-600 dark:bg-neutral-700 dark:text-neutral-300 dark:group-hover:bg-blue-900/50 dark:group-hover:text-blue-400">
        {stageTypeIcon(type)}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-neutral-900 dark:text-neutral-100">{label}</p>
        <p className="truncate text-[10px] text-neutral-500 dark:text-neutral-400">{description}</p>
      </div>
    </button>
  );
}

export function StagePalette() {
  const addNode = useEditorStore((state) => state.addNode);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
          Add Stage
        </p>
        <p className="text-[10px] text-neutral-400 dark:text-neutral-500">
          Drag or click
        </p>
      </div>
      
      <div className="grid gap-2">
        {STAGE_TYPES.map((type) => {
          const meta = STAGE_TYPE_META[type];
          return (
            <PaletteItem
              key={type}
              type={type}
              label={meta.label}
              description={meta.description}
              onAdd={() => addNode(type)}
            />
          );
        })}
      </div>
    </div>
  );
}
