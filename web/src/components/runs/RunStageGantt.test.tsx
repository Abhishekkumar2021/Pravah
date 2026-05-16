import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import { RunStageGantt } from "./RunStageGantt";

const jobs: JobSummary[] = [
  { id: "j1", stageId: "build", stageName: "Build", status: "running", attempt: 1 },
  { id: "j2", stageId: "test", stageName: "Test", status: "pending", attempt: 1 },
];

describe("RunStageGantt", () => {
  it("renders gantt overview and stage labels", () => {
    render(<RunStageGantt jobs={jobs} />);
    expect(screen.getByText("Stage progress by status")).toBeInTheDocument();
    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Test")).toBeInTheDocument();
  });

  it("shows empty state when there are no jobs", () => {
    render(<RunStageGantt jobs={[]} />);
    expect(screen.getByText(/no stages/i)).toBeInTheDocument();
  });
});
