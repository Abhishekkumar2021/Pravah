import { cn } from "@/lib/cn";
import { forwardRef, type InputHTMLAttributes } from "react";

export const inputBaseClass = cn(
  "h-10 w-full rounded-lg border border-neutral-200 bg-white px-3 text-[13px] text-neutral-900",
  "shadow-sm outline-none transition-all duration-150",
  "placeholder:text-neutral-400",
  "hover:border-neutral-300",
  "focus-visible:border-blue-500 focus-visible:ring-2 focus-visible:ring-blue-500/20",
  "disabled:cursor-not-allowed disabled:bg-neutral-50 disabled:opacity-60",
  "dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100",
  "dark:placeholder:text-neutral-500 dark:hover:border-neutral-600",
  "dark:focus-visible:border-blue-500 dark:disabled:bg-neutral-900",
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
