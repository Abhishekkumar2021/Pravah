import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
import { WorkflowDetailPage } from "./WorkflowDetailPage";

const WORKFLOW_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

vi.mock("@/components/workspace/WorkflowSchedulePanel", () => ({
  WorkflowSchedulePanel: () => <div data-testid="schedule-panel" />,
}));

vi.mock("@/components/alerts/AlertRulesPanel", () => ({
  AlertRulesPanel: () => <div data-testid="alert-rules-panel" />,
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/app/workflows/${WORKFLOW_ID}`]}>
      <ThemeProvider>
        <Routes>
          <Route path="/app/workflows/:workflowId" element={<WorkflowDetailPage />} />
        </Routes>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("WorkflowDetailPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.accessToken", "jwt");
    vi.spyOn(api, "getPipeline").mockResolvedValue({
      id: WORKFLOW_ID,
      projectId: "proj-1",
      name: "ETL Pipeline",
      description: "Loads data",
      status: "active",
      currentVersion: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      createdBy: "user-1",
      versions: [{ version: 1, publishedAt: null, publishedBy: null }],
    });
    vi.spyOn(api, "listExecutions").mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      size: 8,
      page: 0,
      last: true,
    });
  });

  it("loads pipeline detail", async () => {
    renderPage();
    await waitFor(() => expect(api.getPipeline).toHaveBeenCalledWith(WORKFLOW_ID));
    expect(await screen.findByRole("heading", { name: "ETL Pipeline" })).toBeVisible();
  });

  it("shows alert rules panel on Alerts tab", async () => {
    const user = userEvent.setup();
    renderPage();
    await waitFor(() => expect(api.getPipeline).toHaveBeenCalledWith(WORKFLOW_ID));
    await user.click(await screen.findByRole("tab", { name: "Alerts" }));
    expect(await screen.findByTestId("alert-rules-panel")).toBeVisible();
  });

});
