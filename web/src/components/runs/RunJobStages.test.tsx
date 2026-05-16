import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { JobSummary } from "@/lib/api";
import * as api from "@/lib/api";
import { RunJobStages } from "./RunJobStages";

const jobs: JobSummary[] = [
  { id: "j-ok", stageId: "lint", stageName: "Lint", status: "succeeded", attempt: 1 },
  { id: "j-bad", stageId: "deploy", stageName: "Deploy", status: "failed", attempt: 2 },
];

describe("RunJobStages", () => {
  beforeEach(() => {
    vi.spyOn(api, "getJobLogs").mockImplementation(async (_execId, jobId) => ({
      jobId,
      executionId: "exec-1",
      lines: [
        {
          id: `log-${jobId}`,
          logTime: "2026-05-16T10:00:00.000Z",
          level: jobId === "j-bad" ? "ERROR" : "INFO",
          message: jobId === "j-bad" ? "Stage failed" : "Stage ok",
        },
      ],
    }));
  });

  it("auto-expands failed stages and loads logs", async () => {
    render(<RunJobStages executionId="exec-1" jobs={jobs} />);
    await waitFor(() => {
      expect(screen.getByText(/Stage failed/)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /Deploy/i })).toHaveAttribute("aria-expanded", "true");
  });

  it("expands newly failed stage when jobs update", async () => {
    const { rerender } = render(
      <RunJobStages
        executionId="exec-1"
        jobs={[{ id: "j-ok", stageId: "lint", stageName: "Lint", status: "succeeded", attempt: 1 }]}
      />,
    );
    expect(screen.queryByText(/Stage failed/)).not.toBeInTheDocument();
    rerender(<RunJobStages executionId="exec-1" jobs={jobs} />);
    await waitFor(() => {
      expect(screen.getByText(/Stage failed/)).toBeInTheDocument();
    });
  });

  it("toggles log panel for a stage", async () => {
    const user = userEvent.setup();
    render(<RunJobStages executionId="exec-1" jobs={jobs} />);
    const lintToggle = screen.getByRole("button", { name: /Lint/i });
    await user.click(lintToggle);
    await waitFor(() => {
      expect(screen.getByText(/Stage ok/)).toBeInTheDocument();
    });
    await user.click(lintToggle);
    expect(screen.queryByText(/Stage ok/)).not.toBeInTheDocument();
  });
});
