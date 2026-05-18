import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import { RunStageGantt } from "./RunStageGantt";

const jobsWithoutTiming: JobSummary[] = [
  {
    id: "j1",
    stageId: "build",
    stageName: "Build",
    status: "running",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: null,
    startedAt: null,
    completedAt: null,
  },
  {
    id: "j2",
    stageId: "test",
    stageName: "Test",
    status: "pending",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: null,
    startedAt: null,
    completedAt: null,
  },
];

const jobsWithTiming: JobSummary[] = [
  {
    id: "j1",
    stageId: "build",
    stageName: "Build",
    status: "succeeded",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:00:00Z",
    startedAt: "2026-05-16T10:00:05Z",
    completedAt: "2026-05-16T10:01:00Z",
  },
  {
    id: "j2",
    stageId: "test",
    stageName: "Test",
    status: "succeeded",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:01:00Z",
    startedAt: "2026-05-16T10:01:02Z",
    completedAt: "2026-05-16T10:02:30Z",
  },
];

describe("RunStageGantt", () => {
  it("renders gantt overview and stage labels", () => {
    render(<RunStageGantt jobs={jobsWithoutTiming} />);
    expect(screen.getByText("Stage progress by status")).toBeInTheDocument();
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Test")).toBeInTheDocument();
  });

  it("shows empty state when there are no jobs", () => {
    render(<RunStageGantt jobs={[]} />);
    expect(screen.getByText(/no stages/i)).toBeInTheDocument();
  });

  it("renders timing data when available", () => {
    render(<RunStageGantt jobs={jobsWithTiming} />);
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Test")).toBeInTheDocument();
    expect(screen.getByText("55s")).toBeInTheDocument();
    expect(screen.getByText("1m 28s")).toBeInTheDocument();
  });
});
