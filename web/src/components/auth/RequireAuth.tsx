import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { ensureAccessToken, hasValidSession } from "@/lib/api";

/** Redirects unauthenticated users to sign-in, preserving the intended destination. */
export function RequireAuth() {
  const location = useLocation();
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let cancelled = false;
    void ensureAccessToken()
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setChecking(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (checking) {
    return null;
  }

  if (!hasValidSession()) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }

  return <Outlet />;
}
