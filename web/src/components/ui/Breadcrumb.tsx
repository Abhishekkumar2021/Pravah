import { ChevronRight, Home } from "lucide-react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";

export type BreadcrumbItem = {
  label: string;
  href?: string;
  icon?: React.ReactNode;
};

type BreadcrumbProps = {
  items: BreadcrumbItem[];
  className?: string;
  showHome?: boolean;
};

export function Breadcrumb({ items, className, showHome = false }: BreadcrumbProps) {
  const allItems = showHome
    ? [{ label: "Home", href: "/app/dashboard", icon: <Home className="h-3.5 w-3.5" /> }, ...items]
    : items;

  return (
    <nav
      className={cn("text-sm text-neutral-500 dark:text-neutral-400", className)}
      aria-label="Breadcrumb"
    >
      <ol className="flex flex-wrap items-center gap-1.5">
        {allItems.map((item, index) => {
          const isLast = index === allItems.length - 1;

          return (
            <li key={`${item.label}-${index}`} className="flex items-center gap-1.5">
              {index > 0 && (
                <ChevronRight
                  className="h-3.5 w-3.5 text-neutral-300 dark:text-neutral-600"
                  aria-hidden
                />
              )}
              {isLast ? (
                <span className="flex items-center gap-1.5 font-medium text-neutral-800 dark:text-neutral-200">
                  {item.icon}
                  {item.label}
                </span>
              ) : item.href ? (
                <Link
                  to={item.href}
                  className="flex items-center gap-1.5 transition-colors hover:text-blue-600 dark:hover:text-blue-400"
                >
                  {item.icon}
                  {item.label}
                </Link>
              ) : (
                <span className="flex items-center gap-1.5">
                  {item.icon}
                  {item.label}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
