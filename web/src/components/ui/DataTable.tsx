import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

type DataTableProps = {
  "aria-label"?: string;
  className?: string;
  children: ReactNode;
  footer?: ReactNode;
};

export function DataTable({
  "aria-label": ariaLabel = "Data table",
  className,
  children,
  footer,
}: DataTableProps) {
  return (
    <div
      className={cn(
        "overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-sm dark:border-neutral-800 dark:bg-neutral-950",
        className,
      )}
    >
      <div className="-mx-px overflow-x-auto" role="region" aria-label={ariaLabel}>
        {children}
      </div>
      {footer !== undefined && footer !== null && <div>{footer}</div>}
    </div>
  );
}
