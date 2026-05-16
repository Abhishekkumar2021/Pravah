import { cn } from "@/lib/cn";
import type { HTMLAttributes } from "react";

export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-lg bg-neutral-200/80 dark:bg-neutral-800/70",
        className,
      )}
      {...props}
    />
  );
}

type TableSkeletonProps = {
  headers: string[];
  rows?: number;
};

export function TableSkeleton({ headers, rows = 6 }: TableSkeletonProps) {
  const colCount = headers.length;
  return (
    <table className="table-data" aria-busy="true" aria-label="Loading">
      <thead>
        <tr>
          {headers.map((h) => (
            <th key={h}>{h}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {Array.from({ length: rows }, (_, ri) => (
          <tr key={ri}>
            {Array.from({ length: colCount }, (_, ci) => (
              <td key={ci} className={cn(ci === colCount - 1 && "text-right")}>
                <Skeleton
                  className={cn(
                    "h-4",
                    ci === 0 && "max-w-[14rem]",
                    ci === 1 && "max-w-[5rem]",
                    ci >= 2 && "max-w-[7rem]",
                    ci === colCount - 1 && "ml-auto max-w-[5rem]",
                  )}
                />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

type ListRowSkeletonProps = {
  rows?: number;
};

export function DashboardWorkflowListSkeleton({ rows = 4 }: ListRowSkeletonProps) {
  return (
    <ul
      className="divide-y divide-neutral-100 dark:divide-neutral-800"
      aria-busy="true"
      aria-label="Loading"
    >
      {Array.from({ length: rows }, (_, i) => (
        <li key={i} className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0">
          <div className="min-w-0 flex-1 space-y-2.5">
            <Skeleton className="h-4 max-w-[16rem]" />
            <Skeleton className="h-3 max-w-[10rem]" />
          </div>
          <Skeleton className="h-6 w-16 shrink-0 rounded-full" />
        </li>
      ))}
    </ul>
  );
}

export function DashboardActiveRunsSkeleton({ rows = 3 }: ListRowSkeletonProps) {
  return (
    <ul className="space-y-4" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <li key={i}>
          <div className="rounded-xl border border-neutral-200 p-4 dark:border-neutral-800">
            <div className="flex items-center justify-between gap-3">
              <Skeleton className="h-4 max-w-[12rem]" />
              <Skeleton className="h-6 w-16 shrink-0 rounded-full" />
            </div>
            <Skeleton className="mt-3 h-3 max-w-[14rem]" />
          </div>
        </li>
      ))}
    </ul>
  );
}

export function DashboardFailuresSkeleton({ rows = 2 }: ListRowSkeletonProps) {
  return (
    <ul className="space-y-3" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <li
          key={i}
          className="rounded-xl border border-rose-100 bg-rose-50/50 p-4 dark:border-rose-900/40 dark:bg-rose-950/30"
        >
          <Skeleton className="h-4 max-w-[14rem] bg-rose-200/60 dark:bg-rose-800/40" />
          <Skeleton className="mt-2.5 h-3 max-w-[10rem] bg-rose-200/60 dark:bg-rose-800/40" />
        </li>
      ))}
    </ul>
  );
}
