import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { TooltipProvider } from "@/components/ui/Tooltip";
import * as api from "@/lib/api";
import { RunDetailPage } from "./RunDetailPage";

const EXECUTION_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

vi.mock("@/lib/useExecutionRealtime", () => ({
  useExecutionRealtime: () => ({ liveConnected: false }),
}));

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/app/runs/${EXECUTION_ID}`]}>
      <ThemeProvider>
        <TooltipProvider>
          <Routes>
            <Route path="/app/runs/:executionId" element={<RunDetailPage />} />
          </Routes>
        </TooltipProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("RunDetailPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.devBearerToken", "jwt");
    vi.spyOn(api, "getExecution").mockResolvedValue({
      id: EXECUTION_ID,
      pipelineId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
      pipelineVersion: 1,
      status: "running",
      triggerType: "manual",
      triggeredBy: "user-1",
      retryOf: null,
      retryCount: 0,
      createdAt: "2026-01-01T00:00:00Z",
      jobs: [],
    });
    vi.spyOn(api, "getPipeline").mockResolvedValue({
      id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
      projectId: "proj-1",
      name: "ETL",
      description: null,
      status: "active",
      currentVersion: 1,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      createdBy: "user-1",
      versions: [{ version: 1, publishedAt: null, publishedBy: null }],
    });
  });

  it("loads execution detail", async () => {
    renderPage();
    await waitFor(() => expect(api.getExecution).toHaveBeenCalledWith(EXECUTION_ID));
    expect(await screen.findByRole("heading", { name: /Run · ETL/i })).toBeVisible();
  });

  it("toggles dev token panel", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("button", { name: /Dev token/i }));
    expect(await screen.findByLabelText("Development JWT")).toBeVisible();
  });
});
