import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

type PaginationProps = {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  className?: string;
};

export function Pagination({ page, totalPages, totalElements, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex flex-wrap items-center justify-between gap-3 border-t border-neutral-200 px-4 py-3 dark:border-neutral-800",
        className,
      )}
    >
      <p className="text-[13px] text-neutral-500">
        Page {page + 1} of {totalPages} · {totalElements} total
      </p>
      <div className="flex gap-2">
        <Button
          type="button"
          variant="secondary"
          className="h-8 px-3 text-[12px]"
          disabled={page <= 0}
          aria-label="Previous page"
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button
          type="button"
          variant="secondary"
          className="h-8 px-3 text-[12px]"
          disabled={page >= totalPages - 1}
          aria-label="Next page"
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </nav>
  );
}
