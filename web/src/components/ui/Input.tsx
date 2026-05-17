import { cn } from "@/lib/cn";
import { forwardRef, type InputHTMLAttributes } from "react";

export const inputBaseClass = cn(
  "h-10 w-full rounded-lg border border-neutral-200/80 bg-white px-3 text-[13px] text-neutral-900",
  "shadow-sm shadow-neutral-950/[0.02] outline-none transition-all duration-150",
  "placeholder:text-neutral-400",
  "hover:border-neutral-300 hover:bg-neutral-50/50",
  "focus-visible:border-blue-500 focus-visible:ring-2 focus-visible:ring-blue-500/20 focus-visible:bg-white",
  "disabled:cursor-not-allowed disabled:bg-neutral-50 disabled:opacity-60",
  "dark:border-neutral-700/80 dark:bg-neutral-900 dark:text-neutral-100 dark:shadow-neutral-950/[0.1]",
  "dark:placeholder:text-neutral-500 dark:hover:border-neutral-600 dark:hover:bg-neutral-800/50",
  "dark:focus-visible:border-blue-500 dark:focus-visible:bg-neutral-900 dark:disabled:bg-neutral-900",
);

const invalidClass = cn(
  "border-rose-500 hover:border-rose-500",
  "focus-visible:border-rose-500 focus-visible:ring-rose-500/20",
  "dark:border-rose-500",
);

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, invalid, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      className={cn(inputBaseClass, invalid && invalidClass, className)}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );
});
