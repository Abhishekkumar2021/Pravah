import * as SwitchPrimitive from "@radix-ui/react-switch";
import { cn } from "@/lib/cn";
import { forwardRef, type ComponentPropsWithoutRef } from "react";

export type SwitchProps = ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>;

export const Switch = forwardRef<HTMLButtonElement, SwitchProps>(function Switch(
  { className, ...props },
  ref,
) {
  return (
    <SwitchPrimitive.Root
      ref={ref}
      className={cn(
        "peer inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-all duration-200",
        "bg-neutral-200 shadow-inner",
        "hover:bg-neutral-300",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-white",
        "disabled:cursor-not-allowed disabled:opacity-50",
        "data-[state=checked]:bg-blue-600 data-[state=checked]:hover:bg-blue-500",
        "dark:bg-neutral-700 dark:hover:bg-neutral-600",
        "dark:focus-visible:ring-offset-neutral-950",
        "dark:data-[state=checked]:bg-blue-500",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        className={cn(
          "pointer-events-none block h-5 w-5 rounded-full bg-white shadow-lg ring-0 transition-transform duration-200",
          "data-[state=checked]:translate-x-5 data-[state=unchecked]:translate-x-0",
        )}
      />
    </SwitchPrimitive.Root>
  );
});
