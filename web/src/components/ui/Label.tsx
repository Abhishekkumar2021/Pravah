import * as LabelPrimitive from "@radix-ui/react-label";
import { cn } from "@/lib/cn";
import type { ComponentPropsWithoutRef } from "react";

export function Label({ className, ...props }: ComponentPropsWithoutRef<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn(
        "text-[13px] font-medium leading-none text-neutral-700 dark:text-neutral-300",
        "peer-disabled:cursor-not-allowed peer-disabled:opacity-60",
        className,
      )}
      {...props}
    />
  );
}

export function LabelHint({
  className,
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <span className={cn("text-[12px] font-normal text-neutral-500 dark:text-neutral-400", className)}>
      {children}
    </span>
  );
}
