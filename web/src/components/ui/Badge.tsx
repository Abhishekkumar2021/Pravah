import { normalizeJobStatus, statusBadgeClass } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

/** Shared layout: tight start (dot), slightly more end (label). */
const badgeWithDot =
  "inline-flex items-center gap-1.5 rounded-full py-1 pl-2 pr-2.5 text-[11px] leading-none";

const badgeDot = "h-1.5 w-1.5 shrink-0 rounded-full";

export function StatusBadge({ status }: { status: string }) {
  const key = normalizeJobStatus(status);
  const cls = statusBadgeClass[key];
  return (
    <span
      className={cn(
        badgeWithDot,
        "font-semibold capitalize tracking-wide backdrop-blur-sm",
        cls,
      )}
    >
      <span
        className={cn(
          badgeDot,
          key === "succeeded" && "bg-emerald-500 shadow-sm shadow-emerald-500/50",
          key === "failed" && "bg-rose-500 shadow-sm shadow-rose-500/50",
          key === "running" && "animate-pulse bg-blue-500 shadow-sm shadow-blue-500/50",
          key === "pending" && "bg-amber-500 shadow-sm shadow-amber-500/50",
          key === "queued" && "bg-neutral-400",
          key === "cancelled" && "bg-neutral-400",
          key === "skipped" && "bg-neutral-400",
          key === "unknown" && "bg-neutral-400",
        )}
      />
      {status}
    </span>
  );
}

/** Live / connecting indicator (WebSocket). */
export function IndicatorBadge({
  label,
  active,
  pulse = false,
}: {
  label: string;
  active: boolean;
  pulse?: boolean;
}) {
  return (
    <span
      className={cn(
        badgeWithDot,
        "border font-semibold uppercase tracking-wider",
        active
          ? "border-emerald-200/80 bg-emerald-50/90 text-emerald-700 backdrop-blur-sm dark:border-emerald-800/60 dark:bg-emerald-950/60 dark:text-emerald-300"
          : "border-neutral-200/80 bg-neutral-50/90 text-neutral-500 backdrop-blur-sm dark:border-neutral-700/60 dark:bg-neutral-900/60 dark:text-neutral-400",
      )}
      title={
        active
          ? "Connected to execution updates (WebSocket)"
          : "Connecting to execution updates…"
      }
    >
      <span
        className={cn(
          badgeDot,
          active
            ? pulse
              ? "animate-pulse bg-emerald-500 shadow-sm shadow-emerald-500/50"
              : "bg-emerald-500 shadow-sm shadow-emerald-500/50"
            : "bg-neutral-400",
        )}
      />
      {label}
    </span>
  );
}

type PillVariant = "default" | "blue" | "green" | "amber" | "rose";

const pillVariants: Record<PillVariant, string> = {
  default: "bg-neutral-100/80 text-neutral-600 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:text-neutral-300 dark:ring-neutral-700/50",
  blue: "bg-blue-100/80 text-blue-700 ring-1 ring-blue-200/50 dark:bg-blue-950/60 dark:text-blue-300 dark:ring-blue-800/50",
  green: "bg-emerald-100/80 text-emerald-700 ring-1 ring-emerald-200/50 dark:bg-emerald-950/60 dark:text-emerald-300 dark:ring-emerald-800/50",
  amber: "bg-amber-100/80 text-amber-700 ring-1 ring-amber-200/50 dark:bg-amber-950/60 dark:text-amber-300 dark:ring-amber-800/50",
  rose: "bg-rose-100/80 text-rose-700 ring-1 ring-rose-200/50 dark:bg-rose-950/60 dark:text-rose-300 dark:ring-rose-800/50",
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
        "inline-flex items-center justify-center rounded-full px-2 py-0.5 text-[11px] font-medium leading-none capitalize backdrop-blur-sm",
        pillVariants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
