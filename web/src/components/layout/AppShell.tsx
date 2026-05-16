import { LayoutDashboard, PanelLeftClose, PanelLeft, PlayCircle, Workflow } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { TooltipProvider } from "@/components/ui/Tooltip";
import { cn } from "@/lib/cn";
import { TopBar } from "./TopBar";

const SIDEBAR_KEY = "pravah.sidebar.collapsed";

export function AppShell() {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === "1");

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, collapsed ? "1" : "0");
  }, [collapsed]);

  const toggle = useCallback(() => setCollapsed((c) => !c), []);

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
      isActive
        ? "bg-blue-600/10 text-blue-800 dark:bg-blue-500/10 dark:text-blue-300"
        : "text-neutral-600 hover:bg-neutral-100 dark:text-neutral-400 dark:hover:bg-neutral-800/60",
      collapsed && "justify-center px-2",
    );

  return (
    <TooltipProvider>
    <div className="flex min-h-dvh">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-blue-600 focus:px-4 focus:py-2 focus:text-white"
      >
        Skip to content
      </a>
      <aside
        className={cn(
          "sticky top-0 flex h-dvh flex-col border-r border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-950",
          collapsed ? "w-[4.25rem]" : "w-60",
        )}
        aria-label="Main navigation"
      >
        <div className="flex h-14 shrink-0 items-center gap-2 border-b border-neutral-200 px-4 dark:border-neutral-800">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-xs font-semibold text-white">
            P
          </div>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium tracking-tight text-neutral-900 dark:text-neutral-50">
                Pravah
              </p>
              <p className="truncate text-xs text-neutral-500 dark:text-neutral-400">Control plane</p>
            </div>
          )}
        </div>
        <nav id="app-sidebar-nav" className="flex flex-1 flex-col gap-1 p-3">
          <NavLink to="/app/dashboard" className={linkClass} title="Dashboard">
            <LayoutDashboard className="h-5 w-5 shrink-0 opacity-80" aria-hidden />
            {!collapsed && "Dashboard"}
          </NavLink>
          <NavLink to="/app/workflows" className={linkClass} title="Workflows">
            <Workflow className="h-5 w-5 shrink-0 opacity-80" aria-hidden />
            {!collapsed && "Workflows"}
          </NavLink>
          <NavLink to="/app/runs" className={linkClass} title="Runs">
            <PlayCircle className="h-5 w-5 shrink-0 opacity-80" aria-hidden />
            {!collapsed && "Runs"}
          </NavLink>
        </nav>
        <div className="border-t border-neutral-200 p-3 dark:border-neutral-800">
          <Button
            type="button"
            variant="ghost"
            onClick={toggle}
            className="h-auto w-full justify-start gap-3 px-3 py-2 text-sm font-medium text-neutral-600 dark:text-neutral-400"
            aria-expanded={!collapsed}
            aria-controls="app-sidebar-nav"
          >
            {collapsed ? (
              <PanelLeft className="h-5 w-5 shrink-0" aria-hidden />
            ) : (
              <PanelLeftClose className="h-5 w-5 shrink-0" aria-hidden />
            )}
            {!collapsed && "Collapse"}
          </Button>
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main id="main-content" className="flex-1 bg-neutral-50 p-6 dark:bg-neutral-950">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
    </TooltipProvider>
  );
}
