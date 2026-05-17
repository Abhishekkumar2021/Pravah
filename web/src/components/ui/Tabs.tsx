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
        "inline-flex items-center gap-1 rounded-lg bg-neutral-100/80 p-1 ring-1 ring-neutral-200/50 dark:bg-neutral-800/80 dark:ring-neutral-700/50",
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
        "inline-flex items-center justify-center whitespace-nowrap rounded-md px-4 py-2 text-[13px] font-medium transition-all duration-150",
        "text-neutral-600 hover:text-neutral-900 hover:bg-white/50",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/25 focus-visible:ring-offset-2",
        "disabled:pointer-events-none disabled:opacity-50",
        "data-[state=active]:bg-white data-[state=active]:text-neutral-900 data-[state=active]:shadow-sm data-[state=active]:ring-1 data-[state=active]:ring-neutral-200/50",
        "dark:text-neutral-400 dark:hover:text-neutral-200 dark:hover:bg-neutral-700/50",
        "dark:data-[state=active]:bg-neutral-900 dark:data-[state=active]:text-neutral-100 dark:data-[state=active]:ring-neutral-700/50",
        "dark:focus-visible:ring-offset-neutral-900",
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
      className={cn(
        "mt-4 outline-none",
        "focus-visible:ring-2 focus-visible:ring-blue-500/20 focus-visible:ring-offset-2",
        className,
      )}
      {...props}
    />
  );
}

export function TabsListUnderline({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn(
        "flex gap-1 border-b border-neutral-200 dark:border-neutral-800",
        className,
      )}
      {...props}
    />
  );
}

export function TabsTriggerUnderline({
  className,
  ...props
}: ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        "relative whitespace-nowrap px-4 py-3 text-[13px] font-medium transition-colors",
        "text-neutral-500 hover:text-neutral-800",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/25 focus-visible:ring-inset",
        "disabled:pointer-events-none disabled:opacity-50",
        "data-[state=active]:text-blue-600",
        "after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:rounded-full after:bg-blue-600 after:opacity-0 after:transition-opacity",
        "data-[state=active]:after:opacity-100",
        "dark:text-neutral-400 dark:hover:text-neutral-200",
        "dark:data-[state=active]:text-blue-400 dark:after:bg-blue-400",
        className,
      )}
      {...props}
    />
  );
}
