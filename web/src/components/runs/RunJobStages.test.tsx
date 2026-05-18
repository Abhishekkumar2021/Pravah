import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { JobSummary } from "@/lib/api";
import * as api from "@/lib/api";
import { RunJobStages } from "./RunJobStages";

const jobs: JobSummary[] = [
  {
    id: "j-ok",
    stageId: "lint",
    stageName: "Lint",
    status: "succeeded",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:00:00Z",
    startedAt: "2026-05-16T10:00:01Z",
    completedAt: "2026-05-16T10:00:30Z",
    output: { row_count: 10 },
  },
  {
    id: "j-bad",
    stageId: "deploy",
    stageName: "Deploy",
    status: "failed",
    attempt: 2,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:00:30Z",
    startedAt: "2026-05-16T10:00:31Z",
    completedAt: "2026-05-16T10:01:00Z",
  },
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

  it("shows attempt of maxAttempts", () => {
    render(<RunJobStages executionId="exec-1" jobs={jobs} />);
    expect(screen.getByText("attempt 2 of 3")).toBeInTheDocument();
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
        jobs={[
          {
            id: "j-ok",
            stageId: "lint",
            stageName: "Lint",
            status: "succeeded",
            attempt: 1,
            maxAttempts: 3,
            queuedAt: "2026-05-16T10:00:00Z",
            startedAt: "2026-05-16T10:00:01Z",
            completedAt: "2026-05-16T10:00:30Z",
            output: { row_count: 1 },
          },
        ]}
      />,
    );
    expect(screen.queryByText(/Stage failed/)).not.toBeInTheDocument();
    rerender(<RunJobStages executionId="exec-1" jobs={jobs} />);
    await waitFor(() => {
      expect(screen.getByText(/Stage failed/)).toBeInTheDocument();
    });
  });

  it("shows stage output when expanded", async () => {
    const user = userEvent.setup();
    render(<RunJobStages executionId="exec-1" jobs={jobs} />);
    await user.click(screen.getByRole("button", { name: /Lint/i }));
    expect(screen.getByText(/Stage output/)).toBeInTheDocument();
    expect(screen.getByText(/"row_count": 10/)).toBeInTheDocument();
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
