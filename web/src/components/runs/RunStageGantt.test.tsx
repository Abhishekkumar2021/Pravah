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

const parallelJobs: JobSummary[] = [
  {
    id: "j1",
    stageId: "extract",
    stageName: "Extract",
    status: "succeeded",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:00:00Z",
    startedAt: "2026-05-16T10:00:05Z",
    completedAt: "2026-05-16T10:01:00Z",
  },
  {
    id: "j2",
    stageId: "validate",
    stageName: "Validate",
    status: "succeeded",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: "2026-05-16T10:00:10Z",
    startedAt: "2026-05-16T10:00:12Z",
    completedAt: "2026-05-16T10:01:20Z",
  },
];

describe("RunStageGantt", () => {
  it("renders one row per stage", () => {
    render(<RunStageGantt jobs={jobsWithoutTiming} />);
    expect(screen.getByText("Stage timeline with one row per stage")).toBeInTheDocument();
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Test")).toBeInTheDocument();
    expect(screen.getAllByText("Awaiting timestamps")).toHaveLength(2);
  });

  it("shows empty state when there are no jobs", () => {
    render(<RunStageGantt jobs={[]} />);
    expect(screen.getByText(/no stages/i)).toBeInTheDocument();
  });

  it("renders timing data and durations when available", () => {
    render(<RunStageGantt jobs={jobsWithTiming} />);
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Test")).toBeInTheDocument();
    expect(screen.getByText("55s")).toBeInTheDocument();
    expect(screen.getByText("1m 28s")).toBeInTheDocument();
    expect(screen.getByText("Queued / waiting")).toBeInTheDocument();
  });

  it("shows parallel stages on separate rows", () => {
    render(<RunStageGantt jobs={parallelJobs} />);
    expect(screen.getByText("Extract")).toBeInTheDocument();
    expect(screen.getByText("Validate")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });
});
