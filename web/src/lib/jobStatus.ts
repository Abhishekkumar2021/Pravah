import type { JobSummary } from "@/lib/api";

export type NormalizedJobStatus =
  | "pending"
  | "queued"
  | "running"
  | "succeeded"
  | "failed"
  | "cancelled"
  | "skipped"
  | "unknown";

export function normalizeJobStatus(status: string): NormalizedJobStatus {
  const s = status.toLowerCase();
  if (
    s === "pending" ||
    s === "queued" ||
    s === "running" ||
    s === "succeeded" ||
    s === "failed" ||
    s === "cancelled" ||
    s === "canceled" ||
    s === "skipped"
  ) {
    return s === "canceled" ? "cancelled" : s;
  }
  return "unknown";
}

export function isFailedJob(status: string): boolean {
  return normalizeJobStatus(status) === "failed";
}

/** Tailwind classes for {@link StatusBadge}. */
export const statusBadgeClass: Record<NormalizedJobStatus, string> = {
  pending: "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300",
  queued: "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300",
  running: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300",
  succeeded: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  failed: "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300",
  cancelled: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  skipped:
    "border border-dashed border-neutral-300 bg-neutral-50 text-neutral-600 dark:border-neutral-600 dark:bg-neutral-900 dark:text-neutral-400",
  unknown: "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300",
};

/** Tailwind segment fill for Gantt bars. */
export function jobStatusBarClass(status: string): string {
  switch (normalizeJobStatus(status)) {
    case "succeeded":
      return "bg-emerald-500";
    case "failed":
      return "bg-rose-500";
    case "running":
      return "bg-blue-500";
    case "cancelled":
      return "bg-amber-500";
    case "skipped":
      return "bg-neutral-300 dark:bg-neutral-600";
    case "queued":
    case "pending":
    default:
      return "bg-neutral-300 dark:bg-neutral-600";
  }
}

export function jobStatusBorderClass(status: string): string | undefined {
  if (isFailedJob(status)) {
    return "border-rose-300 ring-1 ring-rose-200 dark:border-rose-800 dark:ring-rose-900/50";
  }
  return undefined;
}

export function sortJobsByStage(jobs: JobSummary[]): JobSummary[] {
  return [...jobs].sort((a, b) => a.stageId.localeCompare(b.stageId));
}
