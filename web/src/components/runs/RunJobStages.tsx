import { ChevronDown } from "lucide-react";
import { useEffect, useState } from "react";
import type { JobSummary } from "@/lib/api";
import { isFailedJob, jobStatusBorderClass, sortJobsByStage } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { RunJobOutputPanel } from "./RunJobOutputPanel";
import { RunLogPanel } from "./RunLogPanel";

type RunJobStagesProps = {
  executionId: string;
  jobs: JobSummary[];
  canRetry?: boolean;
  onRetryFromStage?: (stageId: string) => void;
};

function initialExpanded(jobs: JobSummary[]): Set<string> {
  return new Set(jobs.filter((j) => isFailedJob(j.status)).map((j) => j.id));
}

export function RunJobStages({ executionId, jobs, canRetry, onRetryFromStage }: RunJobStagesProps) {
  const ordered = sortJobsByStage(jobs);
  const [expanded, setExpanded] = useState<Set<string>>(() => initialExpanded(ordered));

  useEffect(() => {
    setExpanded((prev) => {
      const next = new Set(prev);
      for (const job of jobs) {
        if (isFailedJob(job.status)) {
          next.add(job.id);
        }
      }
      return next;
    });
  }, [jobs]);

  function toggle(jobId: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(jobId)) {
        next.delete(jobId);
      } else {
        next.add(jobId);
      }
      return next;
    });
  }

  if (ordered.length === 0) {
    return <p className="text-sm text-neutral-500">No jobs yet.</p>;
  }

  return (
    <ul className="space-y-3">
      {ordered.map((job) => {
        const isOpen = expanded.has(job.id);
        const panelId = `job-logs-${job.id}`;
        return (
          <li
            key={job.id}
            className={cn(
              "rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-950",
              jobStatusBorderClass(job.status),
            )}
          >
            <div className="flex flex-wrap items-center gap-2 px-4 py-3">
              <Button
                type="button"
                variant="ghost"
                className="h-8 gap-1 px-2 text-neutral-700 dark:text-neutral-200"
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => toggle(job.id)}
              >
                <ChevronDown
                  className={cn("h-4 w-4 transition-transform", isOpen && "rotate-180")}
                  aria-hidden
                />
                <span className="font-medium">{job.stageName}</span>
              </Button>
              <StatusBadge status={job.status} />
              <span className="text-xs text-neutral-500">
                attempt {job.attempt} of {job.maxAttempts}
              </span>
              <span className="ms-auto flex items-center gap-2">
                {canRetry && isFailedJob(job.status) && onRetryFromStage ? (
                  <Button
                    type="button"
                    variant="secondary"
                    className="h-8 px-2 text-xs"
                    onClick={() => onRetryFromStage(job.stageId)}
                  >
                    Retry from here
                  </Button>
                ) : null}
                <span className="font-mono text-xs text-neutral-500">{job.stageId}</span>
              </span>
            </div>
            {isOpen ? (
              <div id={panelId} className="border-t border-neutral-200 px-4 py-3 dark:border-neutral-800">
                <RunJobOutputPanel output={job.output} />
                <RunLogPanel executionId={executionId} job={job} active />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}
