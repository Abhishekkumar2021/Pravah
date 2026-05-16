import { useId } from "react";
import type { JobSummary } from "@/lib/api";
import { jobStatusBarClass, normalizeJobStatus, sortJobsByStage } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { StatusBadge } from "@/components/ui/Badge";

type RunStageGanttProps = {
  jobs: JobSummary[];
  className?: string;
};

/**
 * Compact Gantt-style stage row (US-12.08). Bar widths are equal until timing data exists.
 */
export function RunStageGantt({ jobs, className }: RunStageGanttProps) {
  const ganttLabelId = useId();
  const ordered = sortJobsByStage(jobs);
  if (ordered.length === 0) {
    return (
      <p className="text-sm text-neutral-500 dark:text-neutral-400">No stages for this run yet.</p>
    );
  }

  return (
    <div className={cn("space-y-4", className)}>
      <p id={ganttLabelId} className="sr-only">
        Stage progress by status
      </p>
<div
        className="flex h-3 w-full overflow-hidden rounded-md bg-neutral-100 dark:bg-neutral-900"
        aria-hidden
      >
        {ordered.map((job) => (
          <div
            key={job.id}
            className={cn(
              "min-w-[2rem] flex-1 border-r border-white/20 last:border-r-0 dark:border-neutral-950/40",
              jobStatusBarClass(job.status),
              normalizeJobStatus(job.status) === "running" && "animate-pulse",
            )}
            title={`${job.stageName}: ${job.status}`}
          />
        ))}
      </div>
      <ol aria-labelledby={ganttLabelId} className="sr-only">
        {ordered.map((job) => (
          <li key={job.id}>
            {job.stageName}: {job.status}
          </li>
        ))}
      </ol>
      <ul className="grid gap-2 sm:grid-cols-2">
        {ordered.map((job) => (
          <li
            key={job.id}
            className="flex items-center justify-between gap-2 rounded-lg border border-neutral-200 px-3 py-2 dark:border-neutral-800"
          >
            <span className="truncate text-sm font-medium text-neutral-900 dark:text-neutral-100">
              {job.stageName}
            </span>
            <StatusBadge status={job.status} />
          </li>
        ))}
      </ul>
    </div>
  );
}
