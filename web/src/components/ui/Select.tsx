import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/cn";
import { inputBaseClass } from "./Input";

/** Sentinel for optional Select fields (Radix forbids empty-string item values). */
export const SELECT_UNSET_VALUE = "__pravah_unset__";

export type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

export type SelectProps = {
  value: string;
  onValueChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  "aria-label": string;
  className?: string;
  disabled?: boolean;
  invalid?: boolean;
  id?: string;
  name?: string;
};

const triggerClass = cn(
  inputBaseClass,
  "relative flex w-full cursor-pointer items-center pl-3 pr-10 text-left",
);

const invalidClass = cn(
  "border-rose-500 hover:border-rose-500",
  "focus-visible:border-rose-500 focus-visible:ring-rose-500/20",
  "dark:border-rose-500",
);

const contentClass = cn(
  "z-50 overflow-hidden rounded-xl border border-neutral-200 bg-white shadow-xl",
  "min-w-[max(var(--radix-select-trigger-width),5.5rem)] w-[var(--radix-select-trigger-width)]",
  "max-h-[min(18rem,var(--radix-select-content-available-height))]",
  "dark:border-neutral-700 dark:bg-neutral-900",
  "data-[state=open]:animate-in data-[state=closed]:animate-out",
  "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
  "data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
  "data-[side=bottom]:slide-in-from-top-2 data-[side=top]:slide-in-from-bottom-2",
);

const viewportClass = "p-1.5";

const itemClass = cn(
  "relative flex w-full cursor-pointer select-none items-center justify-between rounded-lg py-2.5 pl-3 pr-3 text-[13px] text-neutral-700 outline-none transition-colors",
  "hover:bg-neutral-100 focus:bg-neutral-100",
  "data-[highlighted]:bg-neutral-100",
  "data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
  "dark:text-neutral-200 dark:hover:bg-neutral-800 dark:focus:bg-neutral-800 dark:data-[highlighted]:bg-neutral-800",
);

const itemTextClass = "min-w-0 flex-1 truncate pr-3 text-left";

const itemIndicatorClass =
  "ml-auto flex h-4 w-4 shrink-0 items-center justify-center self-center";

const chevronClass = cn(
  "pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400",
  "dark:text-neutral-500",
);

export function Select({
  value,
  onValueChange,
  options,
  placeholder = "Select…",
  className,
  disabled,
  invalid,
  id,
  name,
  "aria-label": ariaLabel,
}: SelectProps) {
  const selectableOptions = options.filter((option) => option.value !== "");
  const rootValue = value === "" ? undefined : value;

  return (
    <SelectPrimitive.Root
      value={rootValue}
      onValueChange={onValueChange}
      disabled={disabled}
      name={name}
    >
      <SelectPrimitive.Trigger
        id={id}
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        className={cn(triggerClass, invalid && invalidClass, className)}
      >
        <SelectPrimitive.Value placeholder={placeholder} className="block min-w-0 flex-1 truncate" />
        <SelectPrimitive.Icon asChild>
          <ChevronDown className={chevronClass} aria-hidden />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>

      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          className={contentClass}
          position="popper"
          sideOffset={6}
          align="start"
        >
          <SelectScrollUpButton />
          <SelectPrimitive.Viewport className={viewportClass}>
            {selectableOptions.map((option) => (
              <SelectPrimitive.Item
                key={option.value}
                value={option.value}
                disabled={option.disabled}
                className={itemClass}
              >
                <SelectPrimitive.ItemText className={itemTextClass}>
                  {option.label}
                </SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className={itemIndicatorClass}>
                  <Check className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" strokeWidth={2.5} />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
          <SelectScrollDownButton />
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}

function SelectScrollUpButton() {
  return (
    <SelectPrimitive.ScrollUpButton className="flex cursor-default items-center justify-center py-1.5 text-neutral-400">
      <ChevronUp className="h-4 w-4" aria-hidden />
    </SelectPrimitive.ScrollUpButton>
  );
}

function SelectScrollDownButton() {
  return (
    <SelectPrimitive.ScrollDownButton className="flex cursor-default items-center justify-center py-1.5 text-neutral-400">
      <ChevronDown className="h-4 w-4" aria-hidden />
    </SelectPrimitive.ScrollDownButton>
  );
}
