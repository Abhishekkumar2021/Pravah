import type { JobSummary } from "@/lib/api";
import { isFailedJob } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";

type RunLogPanelProps = {
  job: JobSummary;
  className?: string;
};

/**
 * Placeholder until execution-service exposes log streaming (US-02.03 / US-12.09).
 */
export function RunLogPanel({ job, className }: RunLogPanelProps) {
  const failed = isFailedJob(job.status);

  return (
    <div
      className={cn(
        "rounded-lg border border-neutral-200 bg-neutral-950 px-3 py-3 font-mono text-[12px] leading-relaxed text-neutral-300 dark:border-neutral-800",
        failed && "border-rose-800/60",
        className,
      )}
    >
      {failed && (
        <p className="text-rose-400">
          [error] Stage <span className="text-rose-200">{job.stageName}</span> ended with status{" "}
          {job.status}.
        </p>
      )}
      <p className={cn(failed && "mt-2 text-neutral-500")}>
        Log streaming is not connected yet (US-02.03). When available, stdout/stderr for job{" "}
        <span className="text-neutral-400">{job.id}</span> will appear here.
      </p>
    </div>
  );
}
