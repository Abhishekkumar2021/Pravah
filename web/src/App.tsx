import { Navigate, Route, Routes } from "react-router-dom";
import { HomeRedirect } from "./components/auth/HomeRedirect";
import { PublicOnly } from "./components/auth/PublicOnly";
import { RequireAuth } from "./components/auth/RequireAuth";
import { AppShell } from "./components/layout/AppShell";
import { DashboardPage } from "./pages/DashboardPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { SignupPage } from "./pages/SignupPage";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import { LoginPage } from "./pages/LoginPage";
import { RunDetailPage } from "./pages/RunDetailPage";
import { RunListPage } from "./pages/RunListPage";
import { WorkflowDetailPage } from "./pages/WorkflowDetailPage";
import { ConnectionListPage } from "./pages/ConnectionListPage";
import { WorkflowListPage } from "./pages/WorkflowListPage";

export default function App() {
  return (
    <Routes>
      <Route element={<PublicOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Route>
      <Route path="/app" element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="workflows" element={<WorkflowListPage />} />
          <Route path="workflows/:workflowId" element={<WorkflowDetailPage />} />
          <Route path="connections" element={<ConnectionListPage />} />
          <Route path="runs" element={<RunListPage />} />
          <Route path="runs/:executionId" element={<RunDetailPage />} />
        </Route>
      </Route>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
