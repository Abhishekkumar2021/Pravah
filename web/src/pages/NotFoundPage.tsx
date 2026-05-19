import { Link, useLocation } from "react-router-dom";
import { ArrowLeft, Home, Search, FileQuestion } from "lucide-react";
import { Button } from "@/components/ui/Button";

export function NotFoundPage() {
  const location = useLocation();

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-4 text-center">
      <div className="relative mb-8">
        <div className="absolute -inset-4 rounded-full bg-gradient-to-br from-neutral-100 to-neutral-50 blur-2xl dark:from-neutral-900 dark:to-neutral-950" />
        <div className="relative flex h-24 w-24 items-center justify-center rounded-3xl bg-gradient-to-br from-neutral-100 to-white shadow-lg ring-1 ring-neutral-200/50 dark:from-neutral-800 dark:to-neutral-900 dark:ring-neutral-700/50">
          <FileQuestion className="h-12 w-12 text-neutral-400 dark:text-neutral-500" />
        </div>
      </div>

      <h1 className="bg-gradient-to-r from-neutral-900 to-neutral-600 bg-clip-text text-4xl font-bold tracking-tight text-transparent dark:from-neutral-100 dark:to-neutral-400">
        Page not found
      </h1>

      <p className="mt-4 max-w-md text-neutral-600 dark:text-neutral-400">
        The page you're looking for doesn't exist or may have been moved.
      </p>

      <div className="mt-2 rounded-lg bg-neutral-100 px-3 py-1.5 font-mono text-sm text-neutral-500 dark:bg-neutral-800 dark:text-neutral-400">
        {location.pathname}
      </div>

      <div className="mt-8 flex flex-col gap-3 sm:flex-row">
        <Button asChild variant="primary" className="gap-2">
          <Link to="/app">
            <Home className="h-4 w-4" />
            Go to Dashboard
          </Link>
        </Button>
        <Button asChild variant="secondary" className="gap-2">
          <Link to="/app/workflows">
            <Search className="h-4 w-4" />
            Browse Workflows
          </Link>
        </Button>
      </div>

      <button
        type="button"
        onClick={() => window.history.back()}
        className="mt-6 flex items-center gap-1.5 text-sm text-neutral-500 transition-colors hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
      >
        <ArrowLeft className="h-4 w-4" />
        Go back
      </button>
    </div>
  );
}
