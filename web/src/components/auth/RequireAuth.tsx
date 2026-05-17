import { Navigate, Outlet, useLocation } from "react-router-dom";
import { hasValidSession } from "@/lib/api";

/** Redirects unauthenticated users to sign-in, preserving the intended destination. */
export function RequireAuth() {
  const location = useLocation();

  if (!hasValidSession()) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  return <Outlet />;
}
