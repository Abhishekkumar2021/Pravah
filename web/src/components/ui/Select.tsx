import * as SelectPrimitive from "@radix-ui/react-select";
import { Check, ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/cn";
import { inputBaseClass } from "./Input";

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
  "relative flex h-9 w-full min-w-0 cursor-pointer items-center pl-3 pr-9 text-left",
);

const invalidClass =
  "border-rose-500 focus-visible:border-rose-500 focus-visible:ring-rose-500/20 dark:border-rose-500";

const contentClass = cn(
  "surface-elevated z-50 overflow-hidden rounded-xl border border-neutral-200 bg-white p-1 shadow-lg",
  "min-w-[var(--radix-select-trigger-width)] w-[var(--radix-select-trigger-width)]",
  "max-h-[min(16rem,var(--radix-select-content-available-height))]",
  "dark:border-neutral-800 dark:bg-neutral-950",
  "data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
);

const viewportClass = cn(
  "w-full min-w-[var(--radix-select-trigger-width)] p-0.5",
);

const itemClass = cn(
  "relative flex w-full cursor-pointer select-none items-center rounded-lg py-2 pr-8 pl-2.5 text-[13px] leading-snug text-neutral-800 outline-none",
  "focus:bg-blue-50 focus:text-blue-900 data-[highlighted]:bg-blue-50 data-[highlighted]:text-blue-900",
  "data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
  "dark:text-neutral-200 dark:focus:bg-blue-950/50 dark:focus:text-blue-100 dark:data-[highlighted]:bg-blue-950/50 dark:data-[highlighted]:text-blue-100",
);

const chevronClass =
  "pointer-events-none absolute top-1/2 right-2.5 h-4 w-4 -translate-y-1/2 text-neutral-400 dark:text-neutral-500";

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
  return (
    <SelectPrimitive.Root value={value} onValueChange={onValueChange} disabled={disabled} name={name}>
      <SelectPrimitive.Trigger
        id={id}
        aria-label={ariaLabel}
        aria-invalid={invalid || undefined}
        className={cn(triggerClass, invalid && invalidClass, className)}
      >
        <SelectPrimitive.Value placeholder={placeholder} className="block min-w-0 flex-1 truncate text-left" />
        <SelectPrimitive.Icon asChild>
          <ChevronDown className={chevronClass} aria-hidden />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>

      <SelectPrimitive.Portal>
        <SelectPrimitive.Content
          className={contentClass}
          position="popper"
          sideOffset={4}
          align="start"
        >
          <SelectScrollUpButton />
          <SelectPrimitive.Viewport className={viewportClass}>
            {options.map((option) => (
              <SelectPrimitive.Item
                key={option.value}
                value={option.value}
                disabled={option.disabled}
                className={itemClass}
              >
                <span className="absolute right-2 flex h-4 w-4 items-center justify-center">
                  <SelectPrimitive.ItemIndicator>
                    <Check className="h-3.5 w-3.5 text-blue-600 dark:text-blue-400" strokeWidth={2.5} />
                  </SelectPrimitive.ItemIndicator>
                </span>
                <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
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
    <SelectPrimitive.ScrollUpButton className="flex cursor-default items-center justify-center py-1 text-neutral-500">
      <ChevronUp className="h-4 w-4" aria-hidden />
    </SelectPrimitive.ScrollUpButton>
  );
}

function SelectScrollDownButton() {
  return (
    <SelectPrimitive.ScrollDownButton className="flex cursor-default items-center justify-center py-1 text-neutral-500">
      <ChevronDown className="h-4 w-4" aria-hidden />
    </SelectPrimitive.ScrollDownButton>
  );
}
