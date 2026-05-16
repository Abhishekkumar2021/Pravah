import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
import { WorkflowListPage } from "./WorkflowListPage";

const PROJECT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

function renderPage() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <WorkflowListPage />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("WorkflowListPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.devBearerToken", "jwt");
    localStorage.setItem("pravah.defaultProjectId", PROJECT_ID);
    vi.spyOn(api, "listPipelines").mockResolvedValue({
      content: [
        {
          id: "pipe-1",
          projectId: PROJECT_ID,
          name: "Payments",
          description: "Nightly",
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

  it("lists workflows when configured", async () => {
    renderPage();
    expect(screen.getByRole("heading", { name: /workflows/i })).toBeVisible();
    expect(await screen.findByText("Payments")).toBeVisible();
    await waitFor(() => expect(api.listPipelines).toHaveBeenCalled());
  });
});
