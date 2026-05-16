import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
import { RunListPage } from "./RunListPage";

vi.mock("@/lib/useExecutionRealtime", () => ({
  useExecutionRealtime: () => ({ liveConnected: false }),
}));

const PROJECT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

function renderPage() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <RunListPage />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("RunListPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.devBearerToken", "jwt");
    localStorage.setItem("pravah.defaultProjectId", PROJECT_ID);
    vi.spyOn(api, "listExecutions").mockResolvedValue({
      content: [
        {
          id: "run-1",
          pipelineId: "pipe-1",
          pipelineVersion: 1,
          status: "succeeded",
          triggerType: "manual",
          triggeredBy: "user-1",
          createdAt: "2026-01-01T00:00:00Z",
          startedAt: "2026-01-01T00:01:00Z",
          completedAt: "2026-01-01T00:05:00Z",
        },
      ],
      totalPages: 1,
      totalElements: 1,
      size: 20,
      page: 0,
      last: true,
    });
    vi.spyOn(api, "listPipelines").mockResolvedValue({
      content: [
        {
          id: "pipe-1",
          projectId: PROJECT_ID,
          name: "ETL",
          description: null,
          currentVersion: 1,
          status: "active",
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        },
      ],
      totalPages: 1,
      totalElements: 1,
      size: 20,
      page: 0,
      last: true,
    });
  });

  it("renders runs table when token is set", async () => {
    renderPage();
    expect(screen.getByRole("heading", { name: /runs/i })).toBeVisible();
    await waitFor(() => expect(api.listExecutions).toHaveBeenCalled());
  });
});
