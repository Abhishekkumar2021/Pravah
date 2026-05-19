import { cn } from "@/lib/cn";

type SkipLinkProps = {
  href: string;
  className?: string;
  children?: React.ReactNode;
};

export function SkipLink({ href, className, children = "Skip to main content" }: SkipLinkProps) {
  return (
    <a
      href={href}
      className={cn(
        "absolute -top-12 left-4 z-[100] rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-lg",
        "transition-all duration-150",
        "focus:top-4 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2",
        "dark:bg-blue-500 dark:focus:ring-blue-400",
        className
      )}
    >
      {children}
    </a>
  );
}
