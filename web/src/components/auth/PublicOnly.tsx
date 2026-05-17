import { Navigate, Outlet } from "react-router-dom";
import { hasValidSession } from "@/lib/api";

/** Auth pages: send signed-in users to the app shell. */
export function PublicOnly() {
  if (hasValidSession()) {
    return <Navigate to="/app/dashboard" replace />;
  }
  return <Outlet />;
}
