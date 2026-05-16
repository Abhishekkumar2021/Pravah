import { useCallback, useMemo } from "react";
import { Select } from "@/components/ui/Select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/ToggleGroup";
import { cn } from "@/lib/cn";
import {
  formatTimeValue,
  HOUR_OPTIONS,
  isPm,
  MINUTE_OPTIONS,
  pad,
  parseTime,
  snapMinute,
  toHour12,
  toHour24,
} from "@/components/ui/timePickerUtils";

type TimePickerProps = {
  value: string;
  onChange: (value: string) => void;
  id?: string;
  className?: string;
  disabled?: boolean;
};

export function TimePicker({ value, onChange, id, className, disabled }: TimePickerProps) {
  const { hour, minute } = useMemo(() => parseTime(value), [value]);
  const hour12 = toHour12(hour);
  const pm = isPm(hour);
  const minuteSnapped = snapMinute(minute);

  const setTime = useCallback(
    (h: number, m: number) => {
      onChange(formatTimeValue(h, m));
    },
    [onChange],
  );

  return (
    <div
      role="group"
      aria-label="Time"
      className={cn(
        "inline-flex min-h-10 flex-wrap items-center gap-2 rounded-lg border border-neutral-200 bg-white px-2 py-1.5",
        "shadow-sm transition-colors duration-150",
        "focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-500/20",
        "dark:border-neutral-700 dark:bg-neutral-950 dark:focus-within:border-blue-500",
        disabled && "pointer-events-none opacity-60",
        className,
      )}
    >
      <Select
        id={id}
        aria-label="Hour"
        value={String(hour12)}
        onValueChange={(h) => setTime(toHour24(Number(h), pm), minuteSnapped)}
        options={HOUR_OPTIONS}
        disabled={disabled}
        className="h-8 w-[4.5rem] border-0 bg-transparent pl-2.5 pr-8 shadow-none hover:border-transparent focus-visible:ring-0 dark:bg-transparent"
      />
      <span
        className="select-none text-sm font-semibold text-neutral-300 dark:text-neutral-600"
        aria-hidden
      >
        :
      </span>
      <Select
        aria-label="Minute"
        value={pad(minuteSnapped)}
        onValueChange={(m) => setTime(hour, Number(m))}
        options={MINUTE_OPTIONS}
        disabled={disabled}
        className="h-8 w-[4.5rem] border-0 bg-transparent pl-2.5 pr-8 shadow-none hover:border-transparent focus-visible:ring-0 dark:bg-transparent"
      />
      <ToggleGroup
        type="single"
        value={pm ? "pm" : "am"}
        onValueChange={(v) => {
          if (v === "am" || v === "pm") setTime(toHour24(hour12, v === "pm"), minuteSnapped);
        }}
        disabled={disabled}
        aria-label="AM or PM"
        className="ml-0.5 shrink-0 bg-neutral-100/80 dark:bg-neutral-800/80"
      >
        <ToggleGroupItem value="am" className="h-8 min-w-[2.5rem] px-2.5 text-[12px]">
          AM
        </ToggleGroupItem>
        <ToggleGroupItem value="pm" className="h-8 min-w-[2.5rem] px-2.5 text-[12px]">
          PM
        </ToggleGroupItem>
      </ToggleGroup>
    </div>
  );
}
