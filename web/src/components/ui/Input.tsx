import { cn } from "@/lib/cn";
import { forwardRef, type InputHTMLAttributes } from "react";

export const inputBaseClass = cn(
  "h-9 w-full rounded-lg border border-neutral-200 bg-white px-3 text-[13px] leading-snug text-neutral-900 shadow-none outline-none transition-[color,box-shadow,border-color] placeholder:text-neutral-400",
  "focus-visible:border-blue-600 focus-visible:ring-2 focus-visible:ring-blue-500/15",
  "disabled:cursor-not-allowed disabled:opacity-50",
  "dark:border-neutral-700 dark:bg-neutral-950 dark:text-neutral-100 dark:placeholder:text-neutral-500 dark:focus-visible:border-blue-500",
);

const invalidClass =
  "border-rose-500 focus-visible:border-rose-500 focus-visible:ring-rose-500/20 dark:border-rose-500";

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
