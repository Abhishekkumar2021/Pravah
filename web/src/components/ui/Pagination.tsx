import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

type PaginationProps = {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  className?: string;
};

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
  className,
}: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex flex-wrap items-center justify-between gap-4 border-t border-neutral-100 px-4 py-4 dark:border-neutral-800",
        className,
      )}
    >
      <p className="text-[13px] text-neutral-500 dark:text-neutral-400">
        <span className="font-medium text-neutral-700 dark:text-neutral-300">
          Page {page + 1}
        </span>{" "}
        of {totalPages}
        <span className="mx-2">·</span>
        <span className="tabular-nums">{totalElements.toLocaleString()}</span> total
      </p>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={page <= 0}
          aria-label="Previous page"
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
          Previous
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={page >= totalPages - 1}
          aria-label="Next page"
          onClick={() => onPageChange(page + 1)}
        >
          Next
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>
    </nav>
  );
}
