import * as SeparatorPrimitive from "@radix-ui/react-separator";
import { cn } from "@/lib/cn";
import { forwardRef, type ComponentPropsWithoutRef } from "react";

export type SeparatorProps = ComponentPropsWithoutRef<typeof SeparatorPrimitive.Root>;

export const Separator = forwardRef<HTMLDivElement, SeparatorProps>(function Separator(
  { className, orientation = "horizontal", decorative = true, ...props },
  ref,
) {
  return (
    <SeparatorPrimitive.Root
      ref={ref}
      decorative={decorative}
      orientation={orientation}
      className={cn(
        "shrink-0 bg-neutral-200 dark:bg-neutral-800",
        orientation === "horizontal" ? "h-px w-full" : "h-full w-px",
        className,
      )}
      {...props}
    />
  );
});
