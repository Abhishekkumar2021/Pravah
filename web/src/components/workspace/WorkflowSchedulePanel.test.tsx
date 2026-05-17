import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { ToastProvider } from "@/components/ui/Toast";
import * as api from "@/lib/api";
import { WorkflowSchedulePanel } from "./WorkflowSchedulePanel";

function renderPanel() {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <WorkflowSchedulePanel pipelineId={PIPELINE_ID} />
      </ToastProvider>
    </ThemeProvider>,
  );
}

const PIPELINE_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

describe("WorkflowSchedulePanel", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.accessToken", "jwt");
    vi.spyOn(api, "listSchedules").mockResolvedValue([]);
  });

  it("loads schedules and shows form", async () => {
    renderPanel();

    await waitFor(() => expect(api.listSchedules).toHaveBeenCalledWith(PIPELINE_ID));
    expect(screen.getByText("Schedules")).toBeVisible();
  });

});
