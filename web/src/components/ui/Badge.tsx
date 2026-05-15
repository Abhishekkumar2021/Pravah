import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

const styles: Record<string, string> = {
  pending: "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300",
  queued: "bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300",
  running: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300",
  succeeded: "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300",
  failed: "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300",
  cancelled: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  canceled: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
};

function normalize(status: string) {
  return status.toLowerCase();
}

export function StatusBadge({ status }: { status: string }) {
  const key = normalize(status);
  const cls = styles[key] ?? styles.pending;
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
