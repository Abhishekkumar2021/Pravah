import { describe, expect, it } from "vitest";
import type { JobSummary } from "@/lib/api";
import { computeGanttTimeline, sortJobsForGantt } from "./ganttTimeline";

function job(partial: Partial<JobSummary> & Pick<JobSummary, "id" | "stageId" | "stageName">): JobSummary {
  return {
    status: "pending",
    attempt: 1,
    maxAttempts: 3,
    queuedAt: null,
    startedAt: null,
    completedAt: null,
    ...partial,
  };
}

describe("ganttTimeline", () => {
  it("sorts stages by earliest queue/start time", () => {
    const ordered = sortJobsForGantt([
      job({ id: "2", stageId: "load", stageName: "Load", startedAt: "2026-05-16T10:02:00Z" }),
      job({ id: "1", stageId: "extract", stageName: "Extract", startedAt: "2026-05-16T10:00:00Z" }),
    ]);
    expect(ordered.map((j) => j.stageId)).toEqual(["extract", "load"]);
  });

  it("places parallel stages on separate rows with overlapping windows", () => {
    const timeline = computeGanttTimeline(
      [
        job({
          id: "1",
          stageId: "a",
          stageName: "Stage A",
          status: "succeeded",
          queuedAt: "2026-05-16T10:00:00Z",
          startedAt: "2026-05-16T10:00:05Z",
          completedAt: "2026-05-16T10:01:00Z",
        }),
        job({
          id: "2",
          stageId: "b",
          stageName: "Stage B",
          status: "succeeded",
          queuedAt: "2026-05-16T10:00:10Z",
          startedAt: "2026-05-16T10:00:15Z",
          completedAt: "2026-05-16T10:01:30Z",
        }),
      ],
      new Date("2026-05-16T10:02:00Z").getTime(),
    );

    expect(timeline.rows).toHaveLength(2);
    expect(timeline.hasTimingData).toBe(true);

    const stageA = timeline.rows.find((row) => row.job.stageId === "a")!;
    const stageB = timeline.rows.find((row) => row.job.stageId === "b")!;

    expect(stageA.segments.some((segment) => segment.kind === "running")).toBe(true);
    expect(stageB.segments.some((segment) => segment.kind === "running")).toBe(true);

    const aRunning = stageA.segments.find((segment) => segment.kind === "running")!;
    const bRunning = stageB.segments.find((segment) => segment.kind === "running")!;

    expect(aRunning.startPct).toBeLessThan(bRunning.startPct + bRunning.widthPct);
    expect(bRunning.startPct).toBeLessThan(aRunning.startPct + aRunning.widthPct);
  });

  it("renders queued wait segment before running segment", () => {
    const timeline = computeGanttTimeline([
      job({
        id: "1",
        stageId: "extract",
        stageName: "Extract",
        status: "succeeded",
        queuedAt: "2026-05-16T10:00:00Z",
        startedAt: "2026-05-16T10:00:10Z",
        completedAt: "2026-05-16T10:01:00Z",
      }),
    ]);

    const row = timeline.rows[0];
    expect(row.waitMs).toBe(10_000);
    expect(row.durationMs).toBe(50_000);
    expect(row.segments.map((segment) => segment.kind)).toEqual(["queued", "running"]);
  });

  it("falls back when timestamps are missing", () => {
    const timeline = computeGanttTimeline([
      job({ id: "1", stageId: "a", stageName: "A", status: "pending" }),
      job({ id: "2", stageId: "b", stageName: "B", status: "pending" }),
    ]);

    expect(timeline.hasTimingData).toBe(false);
    expect(timeline.rows.every((row) => row.segments[0]?.kind === "pending")).toBe(true);
  });
});
