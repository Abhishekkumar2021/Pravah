import { cn } from "@/lib/cn";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Size = "sm" | "md" | "lg";

const sizes: Record<Size, string> = {
  sm: "h-8 w-8 rounded-md",
  md: "h-9 w-9 rounded-lg",
  lg: "h-10 w-10 rounded-lg",
};

export type IconButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  size?: Size;
  "aria-label": string;
};

export function IconButton({
  className,
  type = "button",
  size = "md",
  children,
  ...props
}: IconButtonProps) {
  return (
    <button
      type={type}
      className={cn(
        "inline-flex shrink-0 items-center justify-center border border-neutral-200 bg-white text-neutral-600 shadow-sm transition-all duration-150",
        "hover:bg-neutral-50 hover:text-neutral-900",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/25 focus-visible:ring-offset-2 focus-visible:ring-offset-white",
        "active:bg-neutral-100",
        "disabled:pointer-events-none disabled:opacity-50",
        "dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-400",
        "dark:hover:bg-neutral-800 dark:hover:text-neutral-200",
        "dark:focus-visible:ring-offset-neutral-950",
        sizes[size],
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
