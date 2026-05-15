import { LayoutDashboard, PanelLeftClose, PanelLeft, PlayCircle, Workflow } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
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
      "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
      isActive
        ? "bg-teal-600/15 text-teal-800 dark:bg-teal-500/10 dark:text-teal-300"
        : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800/80",
      collapsed && "justify-center px-2",
    );

  return (
    <div className="flex min-h-dvh">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-teal-600 focus:px-4 focus:py-2 focus:text-white"
      >
        Skip to content
      </a>
      <aside
        className={cn(
          "sticky top-0 flex h-dvh flex-col border-r border-zinc-200/80 bg-white/90 backdrop-blur-md dark:border-zinc-800/80 dark:bg-zinc-950/90",
          collapsed ? "w-[4.25rem]" : "w-60",
        )}
        aria-label="Main navigation"
      >
        <div className="flex h-16 items-center gap-2 border-b border-zinc-200/80 px-4 dark:border-zinc-800/80">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-teal-500 to-cyan-500 text-sm font-bold text-white shadow-md">
            P
          </div>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold tracking-tight">Pravah</p>
              <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">Control plane</p>
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
        <div className="border-t border-zinc-200/80 p-3 dark:border-zinc-800/80">
          <button
            type="button"
            onClick={toggle}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-zinc-600 transition-colors hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800/80"
            aria-expanded={!collapsed}
            aria-controls="app-sidebar-nav"
          >
            {collapsed ? (
              <PanelLeft className="h-5 w-5 shrink-0" aria-hidden />
            ) : (
              <PanelLeftClose className="h-5 w-5 shrink-0" aria-hidden />
            )}
            {!collapsed && "Collapse"}
          </button>
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main id="main-content" className="flex-1 bg-zinc-50/80 p-6 dark:bg-zinc-950/50">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
