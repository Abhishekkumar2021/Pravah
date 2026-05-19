import {
  Bell,
  Cable,
  FileText,
  LayoutDashboard,
  PanelLeft,
  PanelLeftClose,
  PlayCircle,
  Settings,
  Workflow,
  X,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { hasValidSession, subscribeSession } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/Tooltip";
import { cn } from "@/lib/cn";
import { TopBar } from "./TopBar";

const SIDEBAR_KEY = "pravah.sidebar.collapsed";

const NAV_ITEMS = [
  { to: "/app/dashboard", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/app/workflows", label: "Workflows", icon: Workflow, end: false },
  { to: "/app/connections", label: "Connections", icon: Cable, end: true },
  { to: "/app/runs", label: "Runs", icon: PlayCircle, end: false },
  { to: "/app/alert-rules", label: "Alerts", icon: Bell, end: true },
  { to: "/app/audit-log", label: "Audit Log", icon: FileText, end: true },
  { to: "/app/settings", label: "Settings", icon: Settings, end: true },
] as const;

function isNavItemActive(pathname: string, to: string, end: boolean): boolean {
  if (end) {
    return pathname === to || pathname === "/app";
  }
  return pathname === to || pathname.startsWith(`${to}/`);
}

function navItemClassName(active: boolean, iconOnly: boolean) {
  return cn(
    "group relative flex items-center rounded-lg text-[13px] font-medium outline-none transition-colors duration-150",
    iconOnly ? "mx-auto h-10 w-10 justify-center" : "gap-3 px-3 py-2.5",
    "focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-neutral-950",
    active
      ? iconOnly
        ? "bg-blue-600 text-white shadow-sm shadow-blue-600/20 dark:bg-blue-600 dark:text-white"
        : "bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300"
      : "text-neutral-600 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-400 dark:hover:bg-neutral-800/80 dark:hover:text-neutral-100",
    active &&
      !iconOnly &&
      "before:absolute before:left-0 before:top-1/2 before:h-5 before:w-0.5 before:-translate-y-1/2 before:rounded-full before:bg-blue-600 dark:before:bg-blue-400",
  );
}

type SidebarNavProps = {
  iconOnly: boolean;
  showTooltips: boolean;
  showCollapseControl: boolean;
  collapsed: boolean;
  onToggleCollapse: () => void;
  onNavigate?: () => void;
  hideBrand?: boolean;
  onClose?: () => void;
};

function SidebarNav({
  iconOnly,
  showTooltips,
  showCollapseControl,
  collapsed,
  onToggleCollapse,
  onNavigate,
  hideBrand = false,
  onClose,
}: SidebarNavProps) {
  const { pathname } = useLocation();

  return (
    <>
      {onClose && (
        <div className="flex h-12 shrink-0 items-center justify-end border-b border-neutral-200 px-2 dark:border-neutral-800">
          <button
            type="button"
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-lg text-neutral-500 transition-colors hover:bg-neutral-100 hover:text-neutral-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 dark:hover:bg-neutral-800 dark:hover:text-neutral-200"
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
      )}

      {!hideBrand && (
      <div
        className={cn(
          "flex h-14 shrink-0 items-center border-b border-neutral-200/80 dark:border-neutral-800",
          iconOnly ? "justify-center px-2" : "gap-3 px-4",
        )}
      >
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-600 to-blue-700 text-sm font-bold text-white shadow-lg shadow-blue-600/20 ring-1 ring-blue-500/20">
          P
        </div>
        {!iconOnly && (
          <div className="min-w-0 flex-1">
            <p className="truncate text-[14px] font-semibold tracking-tight text-neutral-900 dark:text-neutral-50">
              Pravah
            </p>
            <p className="truncate text-[11px] font-medium text-neutral-500 dark:text-neutral-400">
              Control plane
            </p>
          </div>
        )}
      </div>
      )}

      <nav
        id="app-sidebar-nav"
        className={cn("flex flex-1 flex-col gap-1 p-3", iconOnly && "items-stretch px-2")}
        aria-label="Primary"
      >
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = isNavItemActive(pathname, item.to, item.end);

          const link = (
            <NavLink
              to={item.to}
              end={item.end}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={navItemClassName(active, iconOnly)}
            >
              <Icon
                className={cn("h-5 w-5 shrink-0", active && iconOnly ? "opacity-100" : "opacity-80")}
                aria-hidden
              />
              {iconOnly ? (
                <span className="sr-only">{item.label}</span>
              ) : (
                <span className="truncate">{item.label}</span>
              )}
            </NavLink>
          );

          if (!showTooltips) {
            return <div key={item.to}>{link}</div>;
          }

          return (
            <Tooltip key={item.to}>
              <TooltipTrigger asChild>{link}</TooltipTrigger>
              <TooltipContent side="right" sideOffset={10}>
                {item.label}
              </TooltipContent>
            </Tooltip>
          );
        })}
      </nav>

      {showCollapseControl && (
        <div className="border-t border-neutral-200 p-3 dark:border-neutral-800">
          <Button
            type="button"
            variant="ghost"
            onClick={onToggleCollapse}
            className={cn(
              "h-auto w-full gap-3 px-3 py-2.5 text-[13px] font-medium text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700 dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-neutral-200",
              iconOnly ? "justify-center px-2" : "justify-start",
            )}
            aria-expanded={!collapsed}
            aria-controls="app-sidebar-nav"
          >
            {collapsed ? (
              <PanelLeft className="h-5 w-5 shrink-0" aria-hidden />
            ) : (
              <PanelLeftClose className="h-5 w-5 shrink-0" aria-hidden />
            )}
            {!iconOnly && "Collapse"}
          </Button>
        </div>
      )}
    </>
  );
}

export function AppShell() {
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_KEY) === "1");
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    return subscribeSession(() => {
      if (!hasValidSession()) {
        const redirect = encodeURIComponent(location.pathname + location.search);
        navigate(`/login?redirect=${redirect}`, { replace: true });
      }
    });
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_KEY, collapsed ? "1" : "0");
  }, [collapsed]);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!mobileOpen) return;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMobileOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [mobileOpen]);

  const toggleCollapsed = useCallback(() => setCollapsed((value) => !value), []);
  const openMobileNav = useCallback(() => setMobileOpen(true), []);
  const closeMobileNav = useCallback(() => setMobileOpen(false), []);

  return (
    <TooltipProvider delayDuration={300}>
      <div className="flex min-h-dvh">
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-blue-600 focus:px-4 focus:py-2 focus:text-white focus:shadow-lg"
        >
          Skip to content
        </a>

        <aside
          className={cn(
            "sticky top-0 z-20 hidden h-dvh shrink-0 flex-col border-r border-neutral-200/80 bg-white/95 backdrop-blur-sm transition-[width] duration-200 ease-out dark:border-neutral-800 dark:bg-neutral-950/95 md:flex",
            collapsed ? "w-[4.5rem]" : "w-60",
          )}
          aria-label="Main navigation"
        >
          <SidebarNav
            iconOnly={collapsed}
            showTooltips={collapsed}
            showCollapseControl
            collapsed={collapsed}
            onToggleCollapse={toggleCollapsed}
          />
        </aside>

        <div
          className={cn(
            "fixed inset-0 z-40 md:hidden",
            mobileOpen ? "pointer-events-auto" : "pointer-events-none",
          )}
          aria-hidden={!mobileOpen}
        >
          <div
            className={cn(
              "absolute inset-0 bg-black/40 transition-opacity duration-200",
              mobileOpen ? "opacity-100" : "opacity-0",
            )}
            onClick={closeMobileNav}
            aria-hidden
          />
          <aside
            className={cn(
              "absolute inset-y-0 left-0 flex w-[min(18rem,calc(100vw-3rem))] flex-col border-r border-neutral-200 bg-white shadow-xl transition-transform duration-200 ease-out dark:border-neutral-800 dark:bg-neutral-950",
              mobileOpen ? "translate-x-0" : "-translate-x-full",
            )}
            aria-label="Mobile navigation"
            inert={mobileOpen ? undefined : true}
          >
            <SidebarNav
              iconOnly={false}
              showTooltips={false}
              showCollapseControl={false}
              collapsed={false}
              hideBrand
              onClose={closeMobileNav}
              onToggleCollapse={toggleCollapsed}
              onNavigate={closeMobileNav}
            />
          </aside>
        </div>

        <div className="flex min-w-0 flex-1 flex-col">
          <TopBar onMenuClick={openMobileNav} />
          <main
            id="main-content"
            className="flex-1 bg-gradient-to-b from-neutral-50 to-neutral-100/50 p-4 dark:from-neutral-950 dark:to-neutral-900/50 sm:p-6"
          >
            <div className="mx-auto max-w-6xl">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </TooltipProvider>
  );
}
