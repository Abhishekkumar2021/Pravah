import { useId, useMemo } from "react";
import type { JobSummary } from "@/lib/api";
import { jobStatusBarClass, normalizeJobStatus, sortJobsByStage } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { StatusBadge } from "@/components/ui/Badge";

type RunStageGanttProps = {
  jobs: JobSummary[];
  className?: string;
};

type GanttBar = {
  job: JobSummary;
  startPct: number;
  widthPct: number;
  durationMs: number | null;
};

/**
 * Compute Gantt bar positions based on job timing (US-02.09).
 * Falls back to equal-width bars when timing data is unavailable.
 */
function computeGanttBars(jobs: JobSummary[]): GanttBar[] {
  if (jobs.length === 0) return [];

  const withTiming = jobs.filter((j) => j.startedAt != null);
  if (withTiming.length === 0) {
    return jobs.map((job, i) => ({
      job,
      startPct: (i / jobs.length) * 100,
      widthPct: 100 / jobs.length,
      durationMs: null,
    }));
  }

  const times = jobs.map((j) => {
    const start = j.startedAt ? new Date(j.startedAt).getTime() : null;
    const end = j.completedAt
      ? new Date(j.completedAt).getTime()
      : j.startedAt
        ? Date.now()
        : null;
    return { job: j, start, end };
  });

  const allStarts = times.filter((t) => t.start != null).map((t) => t.start!);
  const allEnds = times.filter((t) => t.end != null).map((t) => t.end!);

  const minStart = Math.min(...allStarts);
  const maxEnd = Math.max(...allEnds, Date.now());
  const totalSpan = maxEnd - minStart || 1;

  return times.map(({ job, start, end }) => {
    if (start == null) {
      return { job, startPct: 0, widthPct: 0, durationMs: null };
    }
    const startPct = ((start - minStart) / totalSpan) * 100;
    const duration = (end ?? Date.now()) - start;
    const widthPct = Math.max(2, (duration / totalSpan) * 100);
    return { job, startPct, widthPct, durationMs: duration };
  });
}

function formatDuration(ms: number | null): string {
  if (ms == null) return "";
  if (ms < 1000) return `${ms}ms`;
  const sec = Math.floor(ms / 1000);
  if (sec < 60) return `${sec}s`;
  const min = Math.floor(sec / 60);
  const remSec = sec % 60;
  return remSec > 0 ? `${min}m ${remSec}s` : `${min}m`;
}

/**
 * Gantt-style stage timeline with proportional bar widths (US-02.09, US-12.08).
 * Bar positions and widths are computed from actual job timing when available.
 */
export function RunStageGantt({ jobs, className }: RunStageGanttProps) {
  const ganttLabelId = useId();
  const ordered = sortJobsByStage(jobs);
  const bars = useMemo(() => computeGanttBars(ordered), [ordered]);

  if (ordered.length === 0) {
    return (
      <p className="text-sm text-neutral-500 dark:text-neutral-400">No stages for this run yet.</p>
    );
  }

  const hasTimingData = bars.some((b) => b.durationMs != null);

  return (
    <div className={cn("space-y-4", className)}>
      <p id={ganttLabelId} className="sr-only">
        Stage progress by status
      </p>

      {hasTimingData ? (
        <div
          className="relative h-8 w-full overflow-hidden rounded-md bg-neutral-100 dark:bg-neutral-900"
          aria-hidden
        >
          {bars.map((bar) => (
            <div
              key={bar.job.id}
              className={cn(
                "absolute top-1 h-6 rounded transition-all duration-300",
                jobStatusBarClass(bar.job.status),
                normalizeJobStatus(bar.job.status) === "running" && "animate-pulse",
              )}
              style={{
                left: `${bar.startPct}%`,
                width: `${bar.widthPct}%`,
                minWidth: "0.5rem",
              }}
              title={`${bar.job.stageName}: ${bar.job.status}${bar.durationMs != null ? ` (${formatDuration(bar.durationMs)})` : ""}`}
            />
          ))}
        </div>
      ) : (
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
      )}

      <ol aria-labelledby={ganttLabelId} className="sr-only">
        {ordered.map((job) => (
          <li key={job.id}>
            {job.stageName}: {job.status}
          </li>
        ))}
      </ol>

      <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {bars.map((bar) => (
          <li
            key={bar.job.id}
            className="flex items-center justify-between gap-2 rounded-lg border border-neutral-200 bg-white px-3 py-2 dark:border-neutral-800 dark:bg-neutral-950"
          >
            <div className="flex min-w-0 flex-col gap-0.5">
              <span className="truncate text-sm font-medium text-neutral-900 dark:text-neutral-100">
                {bar.job.stageName}
              </span>
              {bar.durationMs != null && (
                <span className="text-xs text-neutral-500 dark:text-neutral-400">
                  {formatDuration(bar.durationMs)}
                </span>
              )}
            </div>
            <StatusBadge status={bar.job.status} />
          </li>
        ))}
      </ul>
    </div>
  );
}
