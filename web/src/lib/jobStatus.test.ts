import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import {
  isFailedJob,
  jobStatusBarClass,
  jobStatusBorderClass,
  normalizeJobStatus,
  sortJobsByStage,
  statusBadgeClass,
} from "./jobStatus";

describe("jobStatus", () => {
  it("normalizes canceled to cancelled", () => {
    expect(normalizeJobStatus("CANCELED")).toBe("cancelled");
  });

  it("detects failed jobs", () => {
    expect(isFailedJob("FAILED")).toBe(true);
    expect(isFailedJob("running")).toBe(false);
  });

  it("maps status to bar classes", () => {
    expect(jobStatusBarClass("succeeded")).toContain("emerald");
    expect(jobStatusBarClass("failed")).toContain("rose");
  });

  it("highlights failed job borders", () => {
    expect(jobStatusBorderClass("failed")).toContain("rose");
    expect(jobStatusBorderClass("succeeded")).toBeUndefined();
  });

  it("includes skipped in badge styles", () => {
    expect(statusBadgeClass.skipped).toContain("dashed");
  });

  it("sorts jobs by stage id", () => {
    const jobs: JobSummary[] = [
      {
        id: "2",
        stageId: "z-stage",
        stageName: "Z",
        status: "pending",
        attempt: 1,
        maxAttempts: 3,
        queuedAt: null,
        startedAt: null,
        completedAt: null,
      },
      {
        id: "1",
        stageId: "a-stage",
        stageName: "A",
        status: "pending",
        attempt: 1,
        maxAttempts: 3,
        queuedAt: null,
        startedAt: null,
        completedAt: null,
      },
    ];
    expect(sortJobsByStage(jobs).map((j) => j.stageId)).toEqual(["a-stage", "z-stage"]);
  });
});
