import { normalizeJobStatus, statusBadgeClass } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

export function StatusBadge({ status }: { status: string }) {
  const key = normalizeJobStatus(status);
  const cls = statusBadgeClass[key];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold capitalize leading-none tracking-wide",
        cls,
      )}
    >
      <span
        className={cn(
          "h-1.5 w-1.5 rounded-full",
          key === "succeeded" && "bg-emerald-500",
          key === "failed" && "bg-rose-500",
          key === "running" && "animate-pulse bg-blue-500",
          key === "pending" && "bg-amber-500",
          key === "cancelled" && "bg-neutral-400",
        )}
      />
      {status}
    </span>
  );
}

type PillVariant = "default" | "blue" | "green" | "amber" | "rose";

const pillVariants: Record<PillVariant, string> = {
  default: "bg-neutral-100 text-neutral-600 dark:bg-neutral-800 dark:text-neutral-300",
  blue: "bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300",
  green: "bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300",
  amber: "bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300",
  rose: "bg-rose-100 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300",
};

export function Pill({
  children,
  variant = "default",
  className,
}: {
  children: ReactNode;
  variant?: PillVariant;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium leading-tight",
        pillVariants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
