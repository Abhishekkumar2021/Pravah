import * as CheckboxPrimitive from "@radix-ui/react-checkbox";
import { Check } from "lucide-react";
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
        "inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-[6px] border border-neutral-300 bg-white shadow-[0_0_0_1px_rgba(15,23,42,0.02)] transition-[background-color,border-color,box-shadow]",
        "hover:border-neutral-400",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950",
        "disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:border-neutral-300",
        "data-[state=checked]:border-blue-600 data-[state=checked]:bg-blue-600",
        "dark:border-neutral-600 dark:bg-neutral-900 dark:hover:border-neutral-500",
        "dark:data-[state=checked]:border-blue-500 dark:data-[state=checked]:bg-blue-500",
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center text-white">
        <Check className="h-3 w-3" strokeWidth={2.5} aria-hidden />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
});
