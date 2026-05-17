import { Navigate } from "react-router-dom";
import { hasValidSession } from "@/lib/api";

export function HomeRedirect() {
  return <Navigate to={hasValidSession() ? "/app/dashboard" : "/login"} replace />;
}
