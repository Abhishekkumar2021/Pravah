import * as ToggleGroupPrimitive from "@radix-ui/react-toggle-group";
import { cn } from "@/lib/cn";
import { forwardRef, type ComponentPropsWithoutRef, type ReactNode } from "react";

export type ToggleGroupProps = ComponentPropsWithoutRef<typeof ToggleGroupPrimitive.Root>;

export const ToggleGroup = forwardRef<HTMLDivElement, ToggleGroupProps>(function ToggleGroup(
  { className, ...props },
  ref,
) {
  return (
    <ToggleGroupPrimitive.Root
      ref={ref}
      className={cn(
        "inline-flex items-center gap-1 rounded-lg bg-neutral-100 p-1 dark:bg-neutral-800",
        className,
      )}
      {...props}
    />
  );
});

export type ToggleGroupItemProps = ComponentPropsWithoutRef<typeof ToggleGroupPrimitive.Item> & {
  children: ReactNode;
};

export const ToggleGroupItem = forwardRef<HTMLButtonElement, ToggleGroupItemProps>(
  function ToggleGroupItem({ className, children, ...props }, ref) {
    return (
      <ToggleGroupPrimitive.Item
        ref={ref}
        className={cn(
          "inline-flex items-center justify-center rounded-md px-3 py-1.5 text-[13px] font-medium transition-all",
          "text-neutral-600 hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-neutral-100",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40",
          "disabled:pointer-events-none disabled:opacity-50",
          "data-[state=on]:bg-white data-[state=on]:text-neutral-900 data-[state=on]:shadow-sm",
          "dark:data-[state=on]:bg-neutral-700 dark:data-[state=on]:text-neutral-100",
          className,
        )}
        {...props}
      >
        {children}
      </ToggleGroupPrimitive.Item>
    );
  },
);
