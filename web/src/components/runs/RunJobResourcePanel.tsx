import { Cpu, HardDrive } from "lucide-react";

type RunJobResourcePanelProps = {
  output: Record<string, unknown> | null | undefined;
};

export function RunJobResourcePanel({ output }: RunJobResourcePanelProps) {
  if (!output || output.executor !== "container") {
    return null;
  }

  const durationMs = typeof output.duration_ms === "number" ? output.duration_ms : null;
  const image = typeof output.image === "string" ? output.image : null;

  if (!image && durationMs === null) {
    return null;
  }

  return (
    <div className="mb-3 flex flex-wrap items-center gap-4 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs dark:border-neutral-800 dark:bg-neutral-900">
      {image && (
        <span className="flex items-center gap-1.5 text-neutral-600 dark:text-neutral-400">
          <HardDrive className="h-3.5 w-3.5" aria-hidden />
          <span className="font-mono">{image}</span>
        </span>
      )}
      {durationMs !== null && (
        <span className="flex items-center gap-1.5 text-neutral-600 dark:text-neutral-400">
          <Cpu className="h-3.5 w-3.5" aria-hidden />
          <span>
            {durationMs < 1000
              ? `${durationMs}ms`
              : `${(durationMs / 1000).toFixed(1)}s`}
          </span>
        </span>
      )}
      {output.timed_out === true && (
        <span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-700 dark:bg-amber-900/50 dark:text-amber-400">
          Timed out
        </span>
      )}
    </div>
  );
}
