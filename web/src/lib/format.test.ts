import { describe, expect, it, vi, afterEach } from "vitest";
import {
  formatDurationMs,
  formatExecutionWallDuration,
  formatShortDateTime,
  TERMINAL_STATUSES,
} from "@/lib/format";

describe("format", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("formatShortDateTime handles empty and invalid input", () => {
    expect(formatShortDateTime(null)).toBe("—");
    expect(formatShortDateTime("not-a-date")).toBe("—");
    expect(formatShortDateTime("2026-05-16T09:00:00Z")).not.toBe("—");
  });

  it("formatDurationMs formats hours minutes and seconds", () => {
    expect(formatDurationMs(null)).toBe("—");
    expect(formatDurationMs(-1)).toBe("—");
    expect(formatDurationMs(45_000)).toBe("45s");
    expect(formatDurationMs(125_000)).toBe("2m 5s");
    expect(formatDurationMs(3_725_000)).toBe("1h 2m");
  });

  it("formatExecutionWallDuration uses terminal and in-progress rules", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-16T12:00:00Z"));

    expect(
      formatExecutionWallDuration({
        status: "succeeded",
        createdAt: "2026-05-16T11:00:00Z",
        startedAt: "2026-05-16T11:00:00Z",
        completedAt: "2026-05-16T11:05:00Z",
      }),
    ).toBe("5m 0s");

    expect(
      formatExecutionWallDuration({
        status: "running",
        createdAt: "2026-05-16T11:00:00Z",
        startedAt: "2026-05-16T11:55:00Z",
        completedAt: null,
      }),
    ).toBe("5m 0s");

    expect(
      formatExecutionWallDuration({
        status: "pending",
        createdAt: "2026-05-16T11:00:00Z",
        startedAt: null,
        completedAt: null,
      }),
    ).toBe("—");

    expect(TERMINAL_STATUSES).toContain("failed");
  });
});
