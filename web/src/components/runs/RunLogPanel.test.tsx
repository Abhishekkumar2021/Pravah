import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import { RunLogPanel } from "./RunLogPanel";

const failedJob: JobSummary = {
  id: "j-fail",
  stageId: "deploy",
  stageName: "Deploy",
  status: "failed",
  attempt: 1,
};

const okJob: JobSummary = {
  id: "j-ok",
  stageId: "lint",
  stageName: "Lint",
  status: "succeeded",
  attempt: 1,
};

describe("RunLogPanel", () => {
  it("shows error line for failed jobs", () => {
    render(<RunLogPanel job={failedJob} />);
    expect(screen.getByText(/\[error\]/)).toBeInTheDocument();
    expect(screen.getByText(/Log streaming is not connected/i)).toBeInTheDocument();
  });

  it("shows placeholder only for non-failed jobs", () => {
    render(<RunLogPanel job={okJob} />);
    expect(screen.queryByText(/\[error\]/)).not.toBeInTheDocument();
    expect(screen.getByText(/Log streaming is not connected/i)).toBeInTheDocument();
  });
});
