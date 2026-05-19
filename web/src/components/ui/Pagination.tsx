import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { useState, type KeyboardEvent } from "react";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

type PaginationProps = {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  className?: string;
  showJumpToPage?: boolean;
};

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPageChange,
  className,
  showJumpToPage = true,
}: PaginationProps) {
  const [jumpValue, setJumpValue] = useState("");
  const [showJumpInput, setShowJumpInput] = useState(false);

  if (totalPages <= 1) {
    return null;
  }

  const handleJump = () => {
    const parsed = parseInt(jumpValue, 10);
    if (!isNaN(parsed) && parsed >= 1 && parsed <= totalPages) {
      onPageChange(parsed - 1);
      setJumpValue("");
      setShowJumpInput(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleJump();
    } else if (e.key === "Escape") {
      setJumpValue("");
      setShowJumpInput(false);
    }
  };

  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex flex-wrap items-center justify-between gap-4 border-t border-neutral-100 px-4 py-4 dark:border-neutral-800",
        className,
      )}
    >
      <div className="flex items-center gap-3">
        <p className="text-[13px] text-neutral-500 dark:text-neutral-400">
          <span className="font-medium text-neutral-700 dark:text-neutral-300">
            Page {page + 1}
          </span>{" "}
          of {totalPages}
          <span className="mx-2">·</span>
          <span className="tabular-nums">{totalElements.toLocaleString()}</span> total
        </p>
        
        {showJumpToPage && totalPages > 5 && (
          showJumpInput ? (
            <div className="flex items-center gap-1">
              <input
                type="number"
                min={1}
                max={totalPages}
                value={jumpValue}
                onChange={(e) => setJumpValue(e.target.value)}
                onKeyDown={handleKeyDown}
                onBlur={() => {
                  if (!jumpValue) setShowJumpInput(false);
                }}
                placeholder={`1-${totalPages}`}
                className="h-7 w-16 rounded-md border border-neutral-200 bg-white px-2 text-xs tabular-nums shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500/20 dark:border-neutral-700 dark:bg-neutral-900"
                autoFocus
              />
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={handleJump}
                disabled={!jumpValue || isNaN(parseInt(jumpValue, 10))}
              >
                Go
              </Button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setShowJumpInput(true)}
              className="text-[11px] font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
            >
              Jump to page
            </button>
          )
        )}
      </div>
      
      <div className="flex items-center gap-1">
        {totalPages > 2 && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={page <= 0}
            aria-label="First page"
            onClick={() => onPageChange(0)}
            className="h-8 w-8 p-0"
          >
            <ChevronsLeft className="h-4 w-4" />
          </Button>
        )}
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={page <= 0}
          aria-label="Previous page"
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="h-4 w-4" />
          <span className="hidden sm:inline">Previous</span>
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={page >= totalPages - 1}
          aria-label="Next page"
          onClick={() => onPageChange(page + 1)}
        >
          <span className="hidden sm:inline">Next</span>
          <ChevronRight className="h-4 w-4" />
        </Button>
        {totalPages > 2 && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={page >= totalPages - 1}
            aria-label="Last page"
            onClick={() => onPageChange(totalPages - 1)}
            className="h-8 w-8 p-0"
          >
            <ChevronsRight className="h-4 w-4" />
          </Button>
        )}
      </div>
    </nav>
  );
}
