import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { JobSummary } from "@/lib/api";
import * as api from "@/lib/api";
import { RunLogPanel } from "./RunLogPanel";

const job: JobSummary = {
  id: "j-1",
  stageId: "build",
  stageName: "Build",
  status: "succeeded",
  attempt: 1,
};

describe("RunLogPanel", () => {
  it("loads and displays log lines", async () => {
    vi.spyOn(api, "getJobLogs").mockResolvedValue({
      jobId: job.id,
      executionId: "exec-1",
      lines: [
        {
          id: "log-1",
          logTime: "2026-05-16T10:00:00.000Z",
          level: "INFO",
          message: "Stage Build completed successfully",
        },
      ],
    });

    render(<RunLogPanel executionId="exec-1" job={job} />);

    await waitFor(() => {
      expect(screen.getByText(/Stage Build completed successfully/)).toBeInTheDocument();
    });
    expect(screen.getByText(/\[INFO\]/)).toBeInTheDocument();
  });

  it("shows error when fetch fails", async () => {
    vi.spyOn(api, "getJobLogs").mockRejectedValue(new api.ApiError("Unauthorized", 401));

    render(<RunLogPanel executionId="exec-1" job={job} />);

    await waitFor(() => {
      expect(screen.getByText(/Unauthorized/)).toBeInTheDocument();
    });
  });
});
