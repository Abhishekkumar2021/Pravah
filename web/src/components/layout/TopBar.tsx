import { LogOut, Menu, Monitor, Moon, Sun } from "lucide-react";
import { useMemo, useSyncExternalStore } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/Tooltip";
import { useTheme } from "@/lib/theme";
import { signOut, subscribeSession, type AuthUserResponse } from "@/lib/api";

const AUTH_USER_JSON_KEY = "pravah.authUser";

type TopBarProps = {
  onMenuClick?: () => void;
};

function readStoredUserJson(): string | null {
  return localStorage.getItem(AUTH_USER_JSON_KEY);
}

function parseUserJson(json: string | null): AuthUserResponse | undefined {
  if (!json) return undefined;
  try {
    return JSON.parse(json) as AuthUserResponse;
  } catch {
    return undefined;
  }
}

export function TopBar({ onMenuClick }: TopBarProps) {
  const { preference, cyclePreference } = useTheme();
  const navigate = useNavigate();
  const userJson = useSyncExternalStore(
    subscribeSession,
    readStoredUserJson,
    () => null,
  );
  const user = useMemo(() => parseUserJson(userJson), [userJson]);

  const ThemeIcon = preference === "dark" ? Moon : preference === "light" ? Sun : Monitor;

  const handleSignOut = () => {
    signOut();
    navigate("/login");
  };

  const displayName = user?.name?.trim() || user?.email || "Workspace";

  return (
    <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center border-b border-neutral-200/80 bg-white/95 px-4 backdrop-blur-sm dark:border-neutral-800 dark:bg-neutral-950/95 sm:px-6">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          {onMenuClick && (
            <IconButton
              type="button"
              className="md:hidden"
              onClick={onMenuClick}
              aria-label="Open navigation menu"
            >
              <Menu className="h-5 w-5" aria-hidden />
            </IconButton>
          )}
          <div className="min-w-0 leading-tight">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-blue-600 dark:text-blue-400">
              Pravah
            </p>
            <p className="truncate text-sm font-semibold tracking-tight text-neutral-900 dark:text-neutral-50">
              {displayName}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <NotificationBell />
          <Tooltip>
            <TooltipTrigger asChild>
              <IconButton
                type="button"
                onClick={cyclePreference}
                aria-label={`Theme preference is ${preference}. Click to cycle.`}
                className="relative"
              >
                <ThemeIcon className="h-4 w-4" aria-hidden />
                <span className="absolute -bottom-0.5 -right-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-neutral-200 text-[8px] font-semibold uppercase leading-none text-neutral-600 dark:bg-neutral-700 dark:text-neutral-300">
                  {preference === "system" ? "A" : preference === "light" ? "L" : "D"}
                </span>
              </IconButton>
            </TooltipTrigger>
            <TooltipContent>
              {preference === "system"
                ? "Theme: Auto (follows system)"
                : preference === "light"
                ? "Theme: Light"
                : "Theme: Dark"}
              <span className="ml-1 text-neutral-400">· Click to cycle</span>
            </TooltipContent>
          </Tooltip>
          <Button variant="secondary" className="h-9 gap-1.5 px-3" onClick={handleSignOut} aria-label="Sign out">
            <LogOut className="h-4 w-4" aria-hidden />
            <span className="hidden sm:inline">Sign out</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
