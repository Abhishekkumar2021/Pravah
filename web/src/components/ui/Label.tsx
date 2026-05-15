import * as LabelPrimitive from "@radix-ui/react-label";
import { cn } from "@/lib/cn";
import type { ComponentPropsWithoutRef } from "react";

export function Label({ className, ...props }: ComponentPropsWithoutRef<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn(
        "text-[13px] font-medium text-neutral-700 dark:text-neutral-300",
        "peer-disabled:cursor-not-allowed peer-disabled:opacity-70",
        className,
      )}
      {...props}
    />
  );
}
