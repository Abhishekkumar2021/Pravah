import { AlertTriangle, CheckCircle2, ChevronDown, XCircle } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/cn";
import { Button } from "@/components/ui/Button";
import { useEditorStore, type ValidationError } from "./editorStore";

type ValidationPanelProps = {
  onJumpToNode?: (nodeId: string) => void;
};

export function ValidationPanel({ onJumpToNode }: ValidationPanelProps) {
  const [expanded, setExpanded] = useState(true);
  const validationErrors = useEditorStore((s) => s.validationErrors);
  const nodes = useEditorStore((s) => s.nodes);
  const selectNode = useEditorStore((s) => s.selectNode);

  const errors = validationErrors.filter((e) => e.severity === "error");
  const warnings = validationErrors.filter((e) => e.severity === "warning");

  if (validationErrors.length === 0 && nodes.length > 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 dark:border-emerald-900/50 dark:bg-emerald-950/30">
        <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
        <p className="text-sm font-medium text-emerald-700 dark:text-emerald-300">
          Workflow is valid
        </p>
      </div>
    );
  }

  if (validationErrors.length === 0) {
    return null;
  }

  const handleJump = (error: ValidationError) => {
    if (error.nodeId) {
      selectNode(error.nodeId);
      onJumpToNode?.(error.nodeId);
    }
  };

  return (
    <div
      className={cn(
        "rounded-lg border",
        errors.length > 0
          ? "border-rose-200 bg-rose-50 dark:border-rose-900/50 dark:bg-rose-950/30"
          : "border-amber-200 bg-amber-50 dark:border-amber-900/50 dark:bg-amber-950/30"
      )}
    >
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center justify-between px-3 py-2"
      >
        <div className="flex items-center gap-2">
          {errors.length > 0 ? (
            <XCircle className="h-4 w-4 text-rose-600 dark:text-rose-400" />
          ) : (
            <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400" />
          )}
          <p
            className={cn(
              "text-sm font-medium",
              errors.length > 0
                ? "text-rose-700 dark:text-rose-300"
                : "text-amber-700 dark:text-amber-300"
            )}
          >
            {errors.length > 0
              ? `${errors.length} error${errors.length !== 1 ? "s" : ""}`
              : `${warnings.length} warning${warnings.length !== 1 ? "s" : ""}`}
            {errors.length > 0 && warnings.length > 0 && `, ${warnings.length} warning${warnings.length !== 1 ? "s" : ""}`}
          </p>
        </div>
        <ChevronDown
          className={cn(
            "h-4 w-4 transition-transform",
            errors.length > 0 ? "text-rose-500" : "text-amber-500",
            expanded && "rotate-180"
          )}
        />
      </button>

      {expanded && (
        <div className="border-t border-neutral-200/50 px-3 py-2 dark:border-neutral-700/50">
          <ul className="space-y-1.5">
            {errors.map((error, index) => (
              <li key={`error-${index}`} className="flex items-start gap-2">
                <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-rose-500" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-rose-700 dark:text-rose-300">
                    {error.message}
                  </p>
                  {error.nodeId && (
                    <Button
                      type="button"
                      variant="ghost"
                      className="mt-0.5 h-auto p-0 text-[10px] font-medium text-rose-600 hover:text-rose-800 hover:underline dark:text-rose-400 dark:hover:text-rose-200"
                      onClick={() => handleJump(error)}
                    >
                      Jump to stage →
                    </Button>
                  )}
                </div>
              </li>
            ))}
            {warnings.map((warning, index) => (
              <li key={`warning-${index}`} className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-amber-700 dark:text-amber-300">
                    {warning.message}
                  </p>
                  {warning.nodeId && (
                    <Button
                      type="button"
                      variant="ghost"
                      className="mt-0.5 h-auto p-0 text-[10px] font-medium text-amber-600 hover:text-amber-800 hover:underline dark:text-amber-400 dark:hover:text-amber-200"
                      onClick={() => handleJump(warning)}
                    >
                      Jump to stage →
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
