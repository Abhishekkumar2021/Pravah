import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import { Check, Minus } from "lucide-react";
import { cn } from "@/lib/cn";
import { forwardRef, type ComponentPropsWithoutRef } from "react";

export type CheckboxProps = ComponentPropsWithoutRef<typeof CheckboxPrimitive.Root>;

export const Checkbox = forwardRef<HTMLButtonElement, CheckboxProps>(function Checkbox(
  { className, ...props },
  ref,
) {
  return (
    <CheckboxPrimitive.Root
      ref={ref}
      className={cn(
        "peer inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-md border-2 border-neutral-300 bg-white transition-all duration-150",
        "hover:border-neutral-400",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-white",
        "disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:border-neutral-300",
        "data-[state=checked]:border-blue-600 data-[state=checked]:bg-blue-600",
        "data-[state=indeterminate]:border-blue-600 data-[state=indeterminate]:bg-blue-600",
        "dark:border-neutral-600 dark:bg-neutral-900",
        "dark:hover:border-neutral-500",
        "dark:focus-visible:ring-offset-neutral-950",
        "dark:data-[state=checked]:border-blue-500 dark:data-[state=checked]:bg-blue-500",
        "dark:data-[state=indeterminate]:border-blue-500 dark:data-[state=indeterminate]:bg-blue-500",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center text-white">
        {props.checked === "indeterminate" ? (
          <Minus className="h-3.5 w-3.5" strokeWidth={3} aria-hidden />
        ) : (
          <Check className="h-3.5 w-3.5" strokeWidth={3} aria-hidden />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
});
