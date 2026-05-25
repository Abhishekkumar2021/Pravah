import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export type PageHeaderProps = {
  icon: LucideIcon;
  /** Tailwind gradient + ring classes for the icon badge */
  iconAccent?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
};

const DEFAULT_ICON_ACCENT =
  "from-blue-600 to-blue-700 shadow-blue-600/20 ring-blue-500/20";

/** Consistent page header used across app routes (no extra page padding). */
export function PageHeader({
  icon: Icon,
  iconAccent = DEFAULT_ICON_ACCENT,
  title,
  description,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between",
        className,
      )}
    >
      <div>
        <h1 className="page-title flex items-center gap-3">
          <span
            className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-lg ring-1",
              iconAccent,
            )}
          >
            <Icon className="h-5 w-5" aria-hidden />
          </span>
          <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
            {title}
          </span>
        </h1>
        {description ? <p className="page-desc max-w-2xl">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2 shrink-0">{actions}</div> : null}
    </div>
  );
}
