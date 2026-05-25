import { useId, useMemo } from "react";
import type { JobSummary } from "@/lib/api";
import {
  computeGanttTimeline,
  formatGanttDuration,
  type GanttSegment,
  type GanttSegmentKind,
} from "@/lib/ganttTimeline";
import { jobStatusBarClass, normalizeJobStatus } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { StatusBadge } from "@/components/ui/Badge";

type RunStageGanttProps = {
  jobs: JobSummary[];
  className?: string;
};

function segmentClass(kind: GanttSegmentKind, status: string): string {
  if (kind === "queued") {
    return "bg-neutral-300/90 dark:bg-neutral-600/90";
  }
  if (kind === "pending") {
    return "border border-dashed border-neutral-300 bg-neutral-100 dark:border-neutral-600 dark:bg-neutral-800/60";
  }
  return cn(
    jobStatusBarClass(status),
    normalizeJobStatus(status) === "running" && "animate-pulse",
  );
}

function SegmentBar({ segment, status, title }: { segment: GanttSegment; status: string; title: string }) {
  return (
    <div
      className={cn("absolute top-1 h-6 rounded-sm", segmentClass(segment.kind, status))}
      style={{
        left: `${segment.startPct}%`,
        width: `${segment.widthPct}%`,
        minWidth: segment.kind === "pending" ? undefined : "0.35rem",
      }}
      title={title}
    />
  );
}

/**
 * Multi-row Gantt chart: one lane per stage, proportional to queued/start/end timestamps (US-02.09).
 * Parallel stages overlap in time on separate rows.
 */
export function RunStageGantt({ jobs, className }: RunStageGanttProps) {
  const ganttLabelId = useId();
  const timeline = useMemo(() => computeGanttTimeline(jobs), [jobs]);

  if (timeline.rows.length === 0) {
    return (
      <p className="text-sm text-neutral-500 dark:text-neutral-400">No stages for this run yet.</p>
    );
  }

  return (
    <div className={cn("space-y-4", className)}>
      <p id={ganttLabelId} className="sr-only">
        Stage timeline with one row per stage
      </p>

      <div className="overflow-x-auto rounded-lg border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-950">
        {timeline.hasTimingData && (
          <div className="grid grid-cols-[minmax(9rem,12rem)_1fr] border-b border-neutral-200 dark:border-neutral-800">
            <div className="px-3 py-2 text-xs font-medium uppercase tracking-wide text-neutral-500">
              Stage
            </div>
            <div className="relative h-8 border-l border-neutral-200 px-3 dark:border-neutral-800">
              {timeline.ticks.map((tick) => (
                <span
                  key={tick.pct}
                  className="absolute top-2 -translate-x-1/2 text-[10px] tabular-nums text-neutral-500"
                  style={{ left: `${tick.pct}%` }}
                >
                  {tick.label}
                </span>
              ))}
            </div>
          </div>
        )}

        <ol aria-labelledby={ganttLabelId} className="divide-y divide-neutral-100 dark:divide-neutral-800">
          {timeline.rows.map((row) => {
            const titleParts = [row.job.stageName, row.job.status];
            if (row.waitMs != null && row.waitMs > 0) {
              titleParts.push(`wait ${formatGanttDuration(row.waitMs)}`);
            }
            if (row.durationMs != null) {
              titleParts.push(`run ${formatGanttDuration(row.durationMs)}`);
            }
            const barTitle = titleParts.join(" · ");

            return (
              <li
                key={row.job.id}
                className="grid grid-cols-[minmax(9rem,12rem)_1fr] items-center gap-3 px-3 py-2.5"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-neutral-900 dark:text-neutral-100">
                    {row.job.stageName}
                  </p>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <StatusBadge status={row.job.status} />
                    {row.durationMs != null && (
                      <span className="text-[11px] tabular-nums text-neutral-500 dark:text-neutral-400">
                        {formatGanttDuration(row.durationMs)}
                      </span>
                    )}
                  </div>
                </div>

                <div
                  className="relative h-8 rounded-md bg-neutral-100 dark:bg-neutral-900"
                  aria-hidden={!timeline.hasTimingData}
                >
                  {row.segments.map((segment, index) => (
                    <SegmentBar
                      key={`${row.job.id}-${segment.kind}-${index}`}
                      segment={segment}
                      status={row.job.status}
                      title={barTitle}
                    />
                  ))}
                  {!timeline.hasTimingData && (
                    <span className="absolute inset-0 flex items-center justify-center text-[11px] text-neutral-500">
                      Awaiting timestamps
                    </span>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      </div>

      {timeline.hasTimingData && (
        <div className="flex flex-wrap items-center gap-4 text-xs text-neutral-500 dark:text-neutral-400">
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-6 rounded-sm bg-neutral-300 dark:bg-neutral-600" />
            Queued / waiting
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-6 rounded-sm bg-blue-500" />
            Running
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-6 rounded-sm bg-emerald-500" />
            Succeeded
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block h-2.5 w-6 rounded-sm bg-rose-500" />
            Failed
          </span>
        </div>
      )}
    </div>
  );
}
