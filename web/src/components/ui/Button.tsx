import { Slot } from "@radix-ui/react-slot";
import { cn } from "@/lib/cn";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

const variants: Record<Variant, string> = {
  primary: cn(
    "bg-blue-600 text-white shadow-sm shadow-blue-600/15",
    "hover:bg-blue-500 active:bg-blue-700 active:scale-[0.98]",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2",
    "dark:bg-blue-600 dark:hover:bg-blue-500 dark:active:bg-blue-700 dark:shadow-blue-500/10",
  ),
  secondary: cn(
    "border border-neutral-200 bg-white text-neutral-700 shadow-sm",
    "hover:bg-neutral-50 hover:text-neutral-900 hover:border-neutral-300 active:bg-neutral-100 active:scale-[0.98]",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/20 focus-visible:ring-offset-2",
    "dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-200 dark:hover:bg-neutral-800 dark:hover:text-neutral-100 dark:hover:border-neutral-600",
  ),
  ghost: cn(
    "text-neutral-600",
    "hover:bg-neutral-100 hover:text-neutral-900 active:bg-neutral-200/80",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/15 focus-visible:ring-offset-2",
    "dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-neutral-100 dark:active:bg-neutral-700/80",
  ),
  danger: cn(
    "bg-rose-600 text-white shadow-sm shadow-rose-600/15",
    "hover:bg-rose-500 active:bg-rose-700 active:scale-[0.98]",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40 focus-visible:ring-offset-2",
    "dark:bg-rose-600 dark:hover:bg-rose-500 dark:shadow-rose-500/10",
  ),
};

const sizes: Record<Size, string> = {
  sm: "h-8 gap-1.5 rounded-lg px-3 text-[12px]",
  md: "h-10 gap-2 rounded-lg px-4 text-[13px]",
  lg: "h-11 gap-2.5 rounded-xl px-5 text-[14px]",
};

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  asChild?: boolean;
  children: ReactNode;
};

export function Button({
  className,
  variant = "primary",
  size = "md",
  asChild = false,
  type = "button",
  children,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot : "button";
  return (
    <Comp
      type={asChild ? undefined : type}
      className={cn(
        "inline-flex items-center justify-center font-medium transition-all duration-150",
        "disabled:pointer-events-none disabled:opacity-50",
        "focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950",
        sizes[size],
        variants[variant],
        className,
      )}
      {...props}
    >
      {children}
    </Comp>
  );
}
