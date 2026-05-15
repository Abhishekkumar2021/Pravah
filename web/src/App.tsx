import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./components/layout/AppShell";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { RunDetailPage } from "./pages/RunDetailPage";
import { RunListPage } from "./pages/RunListPage";
import { WorkflowDetailPage } from "./pages/WorkflowDetailPage";
import { WorkflowListPage } from "./pages/WorkflowListPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/app" element={<AppShell />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="workflows" element={<WorkflowListPage />} />
        <Route path="workflows/:workflowId" element={<WorkflowDetailPage />} />
        <Route path="runs" element={<RunListPage />} />
        <Route path="runs/:executionId" element={<RunDetailPage />} />
      </Route>
      <Route path="/" element={<Navigate to="/app/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
    </Routes>
  );
}
