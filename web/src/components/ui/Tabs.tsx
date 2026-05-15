import * as TabsPrimitive from "@radix-ui/react-tabs";
import { cn } from "@/lib/cn";
import type { ComponentPropsWithoutRef } from "react";

export const Tabs = TabsPrimitive.Root;

export function TabsList({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn(
        "flex gap-1 overflow-x-auto border-b border-neutral-200 dark:border-neutral-800",
        className,
      )}
      {...props}
    />
  );
}

export function TabsTrigger({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        "relative whitespace-nowrap rounded-lg px-3 py-2 text-[13px] font-medium transition-colors",
        "text-neutral-500 hover:text-neutral-800 dark:text-neutral-400 dark:hover:text-neutral-200",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/25 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950",
        "data-[state=active]:text-blue-700 dark:data-[state=active]:text-blue-300",
        "after:absolute after:inset-x-2 after:-bottom-px after:h-0.5 after:rounded-full after:bg-blue-500 after:opacity-0 after:transition-opacity dark:after:bg-blue-400",
        "data-[state=active]:after:opacity-100",
        "disabled:pointer-events-none disabled:opacity-50",
        className,
      )}
      {...props}
    />
  );
}

export function TabsContent({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      className={cn("mt-6 outline-none focus-visible:ring-2 focus-visible:ring-blue-500/20", className)}
      {...props}
    />
  );
}
