import { DayPicker, type DayPickerProps } from "react-day-picker";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

export type CalendarProps = DayPickerProps;

export function Calendar({ className, classNames, showOutsideDays = true, ...props }: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn("p-3", className)}
      classNames={{
        months: "flex flex-col sm:flex-row gap-4",
        month: "flex flex-col gap-4",
        month_caption: "flex justify-center pt-1 relative items-center h-7",
        caption_label: "text-sm font-medium text-neutral-900 dark:text-neutral-100",
        nav: "flex items-center gap-1",
        button_previous:
          "absolute left-1 top-0 inline-flex h-7 w-7 items-center justify-center rounded-md border border-neutral-200 bg-transparent p-0 text-neutral-600 opacity-70 transition-opacity hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 dark:border-neutral-700 dark:text-neutral-400",
        button_next:
          "absolute right-1 top-0 inline-flex h-7 w-7 items-center justify-center rounded-md border border-neutral-200 bg-transparent p-0 text-neutral-600 opacity-70 transition-opacity hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 dark:border-neutral-700 dark:text-neutral-400",
        month_grid: "w-full border-collapse",
        weekdays: "flex",
        weekday:
          "w-9 text-[11px] font-medium text-neutral-500 dark:text-neutral-400 uppercase tracking-wide",
        week: "flex w-full mt-2",
        day: cn(
          "relative p-0 text-center text-sm focus-within:relative focus-within:z-20",
          "[&:has([aria-selected])]:bg-blue-100 dark:[&:has([aria-selected])]:bg-blue-900/30",
          "[&:has([aria-selected].day-outside)]:bg-blue-100/50 dark:[&:has([aria-selected].day-outside)]:bg-blue-900/20",
          "[&:has([aria-selected].day-range-end)]:rounded-r-md",
        ),
        day_button: cn(
          "inline-flex h-9 w-9 items-center justify-center rounded-md p-0 font-normal transition-colors",
          "hover:bg-neutral-100 hover:text-neutral-900 dark:hover:bg-neutral-800 dark:hover:text-neutral-100",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40",
          "aria-selected:opacity-100",
        ),
        range_end: "day-range-end",
        selected:
          "bg-blue-600 text-white hover:bg-blue-600 hover:text-white focus:bg-blue-600 focus:text-white dark:bg-blue-500",
        today:
          "bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-100 font-semibold",
        outside:
          "day-outside text-neutral-400 opacity-50 aria-selected:bg-blue-100/50 aria-selected:text-neutral-400 aria-selected:opacity-30 dark:text-neutral-500",
        disabled: "text-neutral-400 opacity-50 dark:text-neutral-600",
        range_middle:
          "aria-selected:bg-blue-100 aria-selected:text-blue-900 dark:aria-selected:bg-blue-900/30 dark:aria-selected:text-blue-100",
        hidden: "invisible",
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation }) =>
          orientation === "left" ? (
            <ChevronLeft className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          ),
      }}
      {...props}
    />
  );
}
