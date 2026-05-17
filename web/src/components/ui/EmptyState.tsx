import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

type EmptyStateProps = {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
};

export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center rounded-xl border border-dashed border-neutral-200/80 bg-gradient-to-b from-neutral-50/80 to-white px-6 py-16 text-center",
        "dark:border-neutral-800/80 dark:from-neutral-900/50 dark:to-neutral-950",
        className,
      )}
    >
      {icon && (
        <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-neutral-100 to-white text-neutral-400 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.8),0_1px_3px_rgba(0,0,0,0.05)] ring-1 ring-neutral-200/50 dark:from-neutral-800 dark:to-neutral-900 dark:text-neutral-500 dark:shadow-[inset_0_1px_0_0_rgba(255,255,255,0.05)] dark:ring-neutral-700/50">
          {icon}
        </div>
      )}
      <h3 className="text-[15px] font-semibold tracking-tight text-neutral-900 dark:text-neutral-100">{title}</h3>
      {description && (
        <p className="mt-2 max-w-sm text-[13px] leading-relaxed text-neutral-500 dark:text-neutral-400">
          {description}
        </p>
      )}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

type EmptyStateCompactProps = {
  message: string;
  className?: string;
};

export function EmptyStateCompact({ message, className }: EmptyStateCompactProps) {
  return (
    <p className={cn("py-10 text-center text-[13px] text-neutral-500 dark:text-neutral-400", className)}>
      {message}
    </p>
  );
}
