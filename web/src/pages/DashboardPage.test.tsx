import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
import { DashboardPage } from "./DashboardPage";

vi.mock("@/lib/useExecutionRealtime", () => ({
  useExecutionRealtime: () => ({ liveConnected: false }),
}));

const PROJECT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

function renderPage() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <DashboardPage />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("DashboardPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.accessToken", "jwt");
    localStorage.setItem("pravah.defaultProjectId", PROJECT_ID);
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
    vi.spyOn(api, "listExecutions").mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      size: 20,
      page: 0,
      last: true,
    });
  });

  it("renders dashboard heading and loads data", async () => {
    renderPage();

    expect(screen.getByRole("heading", { name: /dashboard/i })).toBeVisible();
    await waitFor(() => expect(api.listPipelines).toHaveBeenCalled());
    expect(await screen.findByText("ETL")).toBeVisible();
  });

  it("shows workspace setup when project is missing", () => {
    localStorage.removeItem("pravah.defaultProjectId");
    renderPage();
    expect(screen.getByText("Workspace setup")).toBeVisible();
  });
});
