import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Download,
  Redo2,
  Save,
  Undo2,
  Clipboard,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/Tooltip";
import {
  useEditorStore,
  selectCanUndo,
  selectCanRedo,
  selectHasErrors,
} from "./editorStore";

type EditorToolbarProps = {
  onPublish: () => void;
  publishing: boolean;
  publishError: string | null;
  publishSuccess: string | null;
};

export function EditorToolbar({
  onPublish,
  publishing,
  publishError,
  publishSuccess,
}: EditorToolbarProps) {
  const canUndo = useEditorStore(selectCanUndo);
  const canRedo = useEditorStore(selectCanRedo);
  const hasErrors = useEditorStore(selectHasErrors);
  const nodes = useEditorStore((s) => s.nodes);
  const validationErrors = useEditorStore((s) => s.validationErrors);
  const clipboard = useEditorStore((s) => s.clipboard);
  const selectedNodeId = useEditorStore((s) => s.selectedNodeId);

  const undo = useEditorStore((s) => s.undo);
  const redo = useEditorStore((s) => s.redo);
  const toYaml = useEditorStore((s) => s.toYaml);
  const pipelineName = useEditorStore((s) => s.pipelineName);
  const copySelectedNode = useEditorStore((s) => s.copySelectedNode);
  const pasteNode = useEditorStore((s) => s.pasteNode);

  const errorCount = validationErrors.filter((e) => e.severity === "error").length;
  const warningCount = validationErrors.filter((e) => e.severity === "warning").length;

  const downloadYaml = () => {
    const yaml = toYaml();
    const blob = new Blob([yaml], { type: "text/yaml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${pipelineName.replace(/\s+/g, "-").toLowerCase()}.yaml`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-neutral-200 bg-white p-2 shadow-sm dark:border-neutral-800 dark:bg-neutral-900">
      {/* Left side: undo/redo, copy/paste */}
      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              className="h-9 w-9 p-0"
              disabled={!canUndo}
              onClick={undo}
            >
              <Undo2 className="h-4 w-4" />
              <span className="sr-only">Undo (Cmd+Z)</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Undo (⌘Z)</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              className="h-9 w-9 p-0"
              disabled={!canRedo}
              onClick={redo}
            >
              <Redo2 className="h-4 w-4" />
              <span className="sr-only">Redo (Cmd+Shift+Z)</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Redo (⌘⇧Z)</TooltipContent>
        </Tooltip>

        <div className="mx-1 h-5 w-px bg-neutral-200 dark:bg-neutral-700" />

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              className="h-9 w-9 p-0"
              disabled={!selectedNodeId}
              onClick={copySelectedNode}
            >
              <Copy className="h-4 w-4" />
              <span className="sr-only">Copy (Cmd+C)</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Copy stage (⌘C)</TooltipContent>
        </Tooltip>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              className="h-9 w-9 p-0"
              disabled={!clipboard}
              onClick={() => pasteNode()}
            >
              <Clipboard className="h-4 w-4" />
              <span className="sr-only">Paste (Cmd+V)</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Paste stage (⌘V)</TooltipContent>
        </Tooltip>
      </div>

      {/* Center: validation status */}
      <div className="flex items-center gap-2">
        {errorCount > 0 ? (
          <div className="flex items-center gap-1.5 rounded-md bg-rose-50 px-2 py-1 text-xs font-medium text-rose-700 dark:bg-rose-950/50 dark:text-rose-300">
            <AlertTriangle className="h-3.5 w-3.5" />
            {errorCount} error{errorCount !== 1 && "s"}
          </div>
        ) : warningCount > 0 ? (
          <div className="flex items-center gap-1.5 rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
            <AlertTriangle className="h-3.5 w-3.5" />
            {warningCount} warning{warningCount !== 1 && "s"}
          </div>
        ) : nodes.length > 0 ? (
          <div className="flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300">
            <CheckCircle2 className="h-3.5 w-3.5" />
            Valid
          </div>
        ) : null}

        {publishSuccess && (
          <p className="text-xs font-medium text-emerald-600 dark:text-emerald-400">
            {publishSuccess}
          </p>
        )}
        {publishError && (
          <p className="max-w-[200px] truncate text-xs font-medium text-rose-600 dark:text-rose-400">
            {publishError}
          </p>
        )}
      </div>

      {/* Right side: export and publish */}
      <div className="flex items-center gap-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="secondary"
              className="h-9 gap-1.5 px-3"
              onClick={downloadYaml}
              disabled={nodes.length === 0}
            >
              <Download className="h-4 w-4" />
              <span className="hidden sm:inline">Export YAML</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Download workflow as YAML</TooltipContent>
        </Tooltip>

        <Button
          type="button"
          variant="primary"
          className="h-9 gap-1.5 px-3"
          disabled={publishing || nodes.length === 0 || hasErrors}
          onClick={onPublish}
        >
          <Save className="h-4 w-4" />
          {publishing ? "Publishing…" : "Publish"}
        </Button>
      </div>
    </div>
  );
}
