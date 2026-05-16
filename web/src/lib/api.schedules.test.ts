import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  createSchedule,
  deleteSchedule,
  listSchedules,
  pauseSchedule,
  previewCron,
  resumeSchedule,
} from "@/lib/api";

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  localStorage.setItem("pravah.devBearerToken", "test-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("schedule API", () => {
  it("listSchedules calls pipeline query and parses array", async () => {
    const pipelineId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        {
          id: "sched-1",
          pipelineId,
          name: "Daily",
          cronExpression: "0 9 * * *",
          timezone: "UTC",
          active: true,
          nextRunAt: "2026-05-17T09:00:00Z",
          lastRunAt: null,
          createdAt: "2026-05-16T09:00:00Z",
        },
      ],
    });

    const schedules = await listSchedules(pipelineId);

    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/schedules?pipelineId=${pipelineId}`,
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer test-token" }),
      }),
    );
    expect(schedules).toHaveLength(1);
    expect(schedules[0]?.name).toBe("Daily");
  });

  it("createSchedule posts JSON body", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        id: "sched-1",
        pipelineId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
        name: "Morning",
        cronExpression: "0 9 * * *",
        timezone: "UTC",
        active: true,
        nextRunAt: null,
        lastRunAt: null,
        createdAt: "2026-05-16T09:00:00Z",
      }),
    });

    const created = await createSchedule({
      pipelineId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
      name: "Morning",
      cronExpression: "0 9 * * *",
      timezone: "UTC",
    });

    expect(created.name).toBe("Morning");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/schedules",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("previewCron caps count in request body", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ description: "Every day", nextRuns: [] }),
    });

    await previewCron({ cronExpression: "0 9 * * *", timezone: "UTC", count: 10 });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(String(init.body));
    expect(body.count).toBe(10);
  });

  it("pauseSchedule and resumeSchedule use POST endpoints", async () => {
    const schedule = {
      id: "sched-1",
      pipelineId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
      name: "Daily",
      cronExpression: "0 9 * * *",
      timezone: "UTC",
      active: false,
      nextRunAt: null,
      lastRunAt: null,
      createdAt: "2026-05-16T09:00:00Z",
    };
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => schedule })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...schedule, active: true }),
      });

    await pauseSchedule("sched-1");
    await resumeSchedule("sched-1");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/schedules/sched-1/pause");
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/schedules/sched-1/resume");
  });

  it("deleteSchedule throws ApiError on failure", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 404,
      statusText: "Not Found",
      json: async () => ({ message: "Schedule not found" }),
    });

    await expect(deleteSchedule("missing")).rejects.toBeInstanceOf(ApiError);
  });
});
