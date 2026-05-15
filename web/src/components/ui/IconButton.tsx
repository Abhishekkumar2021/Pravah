import { cn } from "@/lib/cn";
import type { ButtonHTMLAttributes, ReactNode } from "react";

export type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  /** Accessible label (required for icon-only buttons). */
  "aria-label": string;
};

export function IconButton({ className, type = "button", children, ...props }: IconButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        "inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-neutral-200 bg-white text-neutral-700 transition-colors",
        "hover:bg-neutral-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/25 focus-visible:ring-offset-2 focus-visible:ring-offset-white",
        "disabled:pointer-events-none disabled:opacity-50",
        "dark:border-neutral-700 dark:bg-neutral-950 dark:text-neutral-200 dark:hover:bg-neutral-900 dark:focus-visible:ring-offset-neutral-950",
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
