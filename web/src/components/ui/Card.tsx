import { cn } from "@/lib/cn";
import type { HTMLAttributes, ReactNode } from "react";

export function Card({
  className,
  children,
  ...props
}: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <div
      className={cn(
        "rounded-xl border border-neutral-200/80 bg-white p-5 shadow-sm",
        "ring-1 ring-neutral-950/[0.03]",
        "dark:border-neutral-800 dark:bg-neutral-950 dark:ring-white/[0.03]",
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return <div className={cn("mb-5 flex flex-col gap-1", className)}>{children}</div>;
}

export function CardTitle({
  className,
  children,
  as: Component = "h3",
}: {
  className?: string;
  children: ReactNode;
  as?: "h2" | "h3" | "h4";
}) {
  return (
    <Component
      className={cn(
        "text-[15px] font-semibold tracking-tight text-neutral-900 dark:text-neutral-50",
        className,
      )}
    >
      {children}
    </Component>
  );
}

export function CardDescription({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <p
      className={cn(
        "text-[13px] leading-relaxed text-neutral-500 dark:text-neutral-400",
        className,
      )}
    >
      {children}
    </p>
  );
}

export function CardContent({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return <div className={cn("", className)}>{children}</div>;
}

export function CardFooter({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "mt-5 flex items-center gap-3 border-t border-neutral-100 pt-5 dark:border-neutral-800",
        className,
      )}
    >
      {children}
    </div>
  );
}
