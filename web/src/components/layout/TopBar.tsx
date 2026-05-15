import { LogOut, Monitor, Moon, Sun } from "lucide-react";
import { Link } from "react-router-dom";
import { useTheme } from "@/lib/theme";

export function TopBar() {
  const { preference, cyclePreference } = useTheme();

  const ThemeIcon = preference === "dark" ? Moon : preference === "light" ? Sun : Monitor;

  return (
    <header className="sticky top-0 z-40 border-b border-zinc-200/80 bg-white/80 px-6 py-3 backdrop-blur-md dark:border-zinc-800/80 dark:bg-zinc-950/80">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase tracking-wider text-teal-600 dark:text-teal-400">
            Pravah
          </p>
          <h1 className="truncate text-lg font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Workspace
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={cyclePreference}
            className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-zinc-200 bg-white text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800"
            title={`Theme: ${preference} (click to cycle)`}
            aria-label={`Theme preference is ${preference}. Click to cycle.`}
          >
            <ThemeIcon className="h-5 w-5" aria-hidden />
          </button>
          <Link
            to="/login"
            className="inline-flex h-10 items-center gap-2 rounded-xl border border-zinc-200 bg-white px-3 text-sm font-medium text-zinc-700 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800"
          >
            <LogOut className="h-4 w-4" aria-hidden />
            <span className="hidden sm:inline">Sign out</span>
          </Link>
        </div>
      </div>
    </header>
  );
}
