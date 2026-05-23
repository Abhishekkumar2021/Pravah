import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { hasValidSession, refreshAccessToken } from "@/lib/api";

/** Redirects unauthenticated users to sign-in, preserving the intended destination. */
export function RequireAuth() {
  const location = useLocation();
  const [checking, setChecking] = useState(!hasValidSession());

  useEffect(() => {
    if (hasValidSession()) {
      setChecking(false);
      return;
    }
    let cancelled = false;
    void refreshAccessToken()
      .catch(() => null)
      .then(() => {
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
