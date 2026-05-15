import { Slot } from "@radix-ui/react-slot";
import { cn } from "@/lib/cn";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const variants: Record<Variant, string> = {
  primary:
    "bg-blue-600 text-white shadow-sm hover:bg-blue-500 active:bg-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-50 dark:focus-visible:ring-offset-neutral-950 dark:bg-blue-600 dark:hover:bg-blue-500 dark:active:bg-blue-700",
  secondary:
    "border border-neutral-300 bg-white text-neutral-900 hover:bg-neutral-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/20 focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-50 dark:border-neutral-700 dark:bg-neutral-950 dark:text-neutral-100 dark:hover:bg-neutral-900 dark:focus-visible:ring-offset-neutral-950",
  ghost:
    "text-neutral-700 hover:bg-neutral-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/15 focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-50 dark:text-neutral-300 dark:hover:bg-neutral-900 dark:focus-visible:ring-offset-neutral-950",
  danger:
    "bg-rose-600 text-white hover:bg-rose-500 active:bg-rose-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/45 focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-50 dark:bg-rose-600 dark:hover:bg-rose-500 dark:focus-visible:ring-offset-neutral-950",
};

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  /** Merge styles onto child (e.g. `Link`) instead of rendering `<button>`. */
  asChild?: boolean;
  children: ReactNode;
};

export function Button({
  className,
  variant = "primary",
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
        "inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-[13px] font-medium transition-colors disabled:pointer-events-none disabled:opacity-50",
        variants[variant],
        className,
      )}
      {...props}
    >
      {children}
    </Comp>
  );
}
