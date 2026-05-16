import { LogOut, Monitor, Moon, Sun } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { useTheme } from "@/lib/theme";

export function TopBar() {
  const { preference, cyclePreference } = useTheme();

  const ThemeIcon = preference === "dark" ? Moon : preference === "light" ? Sun : Monitor;

  return (
    <header className="sticky top-0 z-40 flex h-14 shrink-0 items-center border-b border-neutral-200 bg-white px-6 dark:border-neutral-800 dark:bg-neutral-950">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4">
        <div className="min-w-0 leading-tight">
          <p className="text-[10px] font-medium uppercase tracking-wider text-blue-600 dark:text-blue-400">
            Pravah
          </p>
          <h1 className="truncate text-sm font-semibold tracking-tight text-neutral-900 dark:text-neutral-50">
            Workspace
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <IconButton
            type="button"
            onClick={cyclePreference}
            title={`Theme: ${preference} (click to cycle)`}
            aria-label={`Theme preference is ${preference}. Click to cycle.`}
          >
            <ThemeIcon className="h-4 w-4" aria-hidden />
          </IconButton>
          <Button variant="secondary" asChild className="h-9 px-3">
            <Link to="/login" aria-label="Sign out">
              <LogOut className="h-4 w-4" aria-hidden />
              <span className="hidden sm:inline">Sign out</span>
            </Link>
          </Button>
        </div>
      </div>
    </header>
  );
}
