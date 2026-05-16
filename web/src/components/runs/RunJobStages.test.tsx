import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import { RunJobStages } from "./RunJobStages";

const jobs: JobSummary[] = [
  { id: "j-ok", stageId: "lint", stageName: "Lint", status: "succeeded", attempt: 1 },
  { id: "j-bad", stageId: "deploy", stageName: "Deploy", status: "failed", attempt: 2 },
];

describe("RunJobStages", () => {
  it("auto-expands failed stages with error log placeholder", () => {
    render(<RunJobStages jobs={jobs} />);
    expect(screen.getByText(/\[error\]/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Deploy/i })).toHaveAttribute("aria-expanded", "true");
  });

  it("expands newly failed stage when jobs update", () => {
    const { rerender } = render(
      <RunJobStages
        jobs={[{ id: "j-ok", stageId: "lint", stageName: "Lint", status: "succeeded", attempt: 1 }]}
      />,
    );
    expect(screen.queryByText(/\[error\]/)).not.toBeInTheDocument();
    rerender(<RunJobStages jobs={jobs} />);
    expect(screen.getByText(/\[error\]/)).toBeInTheDocument();
  });

  it("toggles log panel for a stage", async () => {
    const user = userEvent.setup();
    render(<RunJobStages jobs={jobs} />);
    const lintToggle = screen.getByRole("button", { name: /Lint/i });
    expect(screen.getAllByText(/Log streaming is not connected/i)).toHaveLength(1);
    await user.click(lintToggle);
    expect(screen.getAllByText(/Log streaming is not connected/i)).toHaveLength(2);
    await user.click(lintToggle);
    expect(screen.getAllByText(/Log streaming is not connected/i)).toHaveLength(1);
  });
});
