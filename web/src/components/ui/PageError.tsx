import type { LucideIcon } from "lucide-react";
import { TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { cn } from "@/lib/cn";

type PageErrorProps = {
  title: string;
  message: string;
  onRetry?: () => void;
  retryLabel?: string;
  icon?: LucideIcon;
  className?: string;
};

/** Inline page-level error (rose card + optional retry). Prefer over toast-only for load failures. */
export function PageError({
  title,
  message,
  onRetry,
  retryLabel = "Try again",
  icon: Icon = TriangleAlert,
  className,
}: PageErrorProps) {
  return (
    <Card
      role="alert"
      className={cn(
        "border-rose-200/80 bg-gradient-to-br from-rose-50 to-white dark:border-rose-900/50 dark:from-rose-950/40 dark:to-neutral-950",
        className,
      )}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600 ring-1 ring-rose-200/50 dark:bg-rose-900/50 dark:text-rose-400 dark:ring-rose-800/50">
            <Icon className="h-5 w-5" aria-hidden />
          </div>
          <div>
            <CardTitle className="text-[15px] text-rose-800 dark:text-rose-200">{title}</CardTitle>
            <CardDescription className="mt-1 text-rose-700/90 dark:text-rose-300/90">
              {message}
            </CardDescription>
          </div>
        </div>
        {onRetry ? (
          <Button type="button" variant="secondary" size="sm" onClick={onRetry} className="shrink-0">
            {retryLabel}
          </Button>
        ) : null}
      </div>
    </Card>
  );
}
