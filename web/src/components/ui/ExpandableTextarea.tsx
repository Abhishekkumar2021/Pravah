import { Maximize2 } from "lucide-react";
import { useId, useState, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/cn";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import { IconButton } from "@/components/ui/IconButton";

const baseClass = cn(
  "w-full rounded-lg border bg-white px-3 py-2 text-sm shadow-sm transition-colors",
  "placeholder:text-neutral-400",
  "hover:border-neutral-300 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20",
  "dark:bg-neutral-900 dark:text-neutral-100 dark:hover:border-neutral-600",
  "border-neutral-200 dark:border-neutral-700",
);

const invalidClass = cn(
  "border-rose-500 focus:border-rose-500 focus:ring-rose-500/20",
  "dark:border-rose-500",
);

export type ExpandableTextareaProps = Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  "className"
> & {
  invalid?: boolean;
  mono?: boolean;
  expandTitle?: string;
  expandDescription?: string;
  className?: string;
  /** Minimum height in compact mode (Tailwind class). */
  minHeightClass?: string;
};

export function ExpandableTextarea({
  invalid,
  mono = false,
  expandTitle,
  expandDescription,
  className,
  minHeightClass = "min-h-[7rem]",
  id: idProp,
  rows = 6,
  ...props
}: ExpandableTextareaProps) {
  const generatedId = useId();
  const id = idProp ?? generatedId;
  const [expanded, setExpanded] = useState(false);

  const textareaClass = cn(
    baseClass,
    mono && "font-mono text-[13px]",
    invalid && invalidClass,
    minHeightClass,
    className,
  );

  const expandButton = (
    <IconButton
      type="button"
      size="sm"
      aria-label="Expand editor"
      className="absolute right-2 top-2 bg-white/90 backdrop-blur-sm dark:bg-neutral-900/90"
      onClick={() => setExpanded(true)}
    >
      <Maximize2 className="h-3.5 w-3.5" aria-hidden />
    </IconButton>
  );

  return (
    <>
      <div className="relative mt-1.5">
        <textarea id={id} rows={rows} className={cn(textareaClass, "pr-10")} {...props} />
        {expandButton}
      </div>

      <Dialog open={expanded} onOpenChange={setExpanded}>
        <DialogContent
          className={cn(
            "flex max-h-[90dvh] w-[min(1200px,calc(100vw-2rem))] max-w-none flex-col",
            "h-[min(85dvh,900px)] p-0",
          )}
          showClose
        >
          <DialogHeader className="border-b border-neutral-200 px-6 py-4 dark:border-neutral-800">
            <DialogTitle>{expandTitle ?? "Editor"}</DialogTitle>
            {expandDescription && <DialogDescription>{expandDescription}</DialogDescription>}
          </DialogHeader>
          <div className="flex min-h-0 flex-1 flex-col p-4 pt-3">
            <textarea
              aria-label={expandTitle ?? props["aria-label"] ?? "Expanded editor"}
              className={cn(textareaClass, "min-h-0 flex-1 resize-none")}
              {...props}
            />
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
