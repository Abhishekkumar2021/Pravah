import { normalizeJobStatus, statusBadgeClass } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

/** Shared layout: tight start (dot), slightly more end (label). */
const badgeWithDot =
  "inline-flex items-center gap-1 rounded-full py-0.5 pl-1.5 pr-2 text-[11px] leading-none";

const badgeDot = "h-1.5 w-1.5 shrink-0 rounded-full";

export function StatusBadge({ status }: { status: string }) {
  const key = normalizeJobStatus(status);
  const cls = statusBadgeClass[key];
  return (
    <span
      className={cn(
        badgeWithDot,
        "font-semibold capitalize tracking-wide",
        cls,
      )}
    >
      <span
        className={cn(
          badgeDot,
          key === "succeeded" && "bg-emerald-500",
          key === "failed" && "bg-rose-500",
          key === "running" && "animate-pulse bg-blue-500",
          key === "pending" && "bg-amber-500",
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
        "border font-semibold uppercase tracking-wide",
        active
          ? "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-300"
          : "border-neutral-200 bg-neutral-50 text-neutral-500 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400",
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
              ? "animate-pulse bg-emerald-500"
              : "bg-emerald-500"
            : "bg-neutral-400",
        )}
      />
      {label}
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
        "inline-flex items-center justify-center rounded-full px-1.5 py-0.5 text-[11px] font-medium leading-none capitalize",
        pillVariants[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
