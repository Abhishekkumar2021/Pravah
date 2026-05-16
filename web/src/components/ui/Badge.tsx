import { normalizeJobStatus, statusBadgeClass } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

export function StatusBadge({ status }: { status: string }) {
  const key = normalizeJobStatus(status);
  const cls = statusBadgeClass[key];
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium capitalize leading-tight",
        cls,
      )}
    >
      {status}
    </span>
  );
}

export function Pill({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-lg bg-neutral-100 px-2 py-0.5 text-[11px] font-medium text-neutral-600 dark:bg-neutral-800 dark:text-neutral-300",
        className,
      )}
    >
      {children}
    </span>
  );
}
