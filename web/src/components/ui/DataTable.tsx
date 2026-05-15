import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

type DataTableProps = {
  /** Scrollable table region label (SR + browser accessibility). */
  "aria-label"?: string;
  className?: string;
  children: ReactNode;
  /** Renders below the scroll area with a top border (empty states, hints). */
  footer?: ReactNode;
};

export function DataTable({
  "aria-label": ariaLabel = "Data table",
  className,
  children,
  footer,
}: DataTableProps) {
  return (
    <div className={cn("surface-card overflow-hidden", className)}>
      <div className="overflow-x-auto" role="region" aria-label={ariaLabel}>
        {children}
      </div>
      {footer !== undefined && footer !== null && (
        <div className="border-t border-neutral-100 dark:border-neutral-800">{footer}</div>
      )}
    </div>
  );
}
