import { describe, expect, it, vi, beforeEach } from "vitest";
import { ApiError, triggerPipelineRun } from "@/lib/api";

describe("triggerPipelineRun", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("rejects invalid pipeline id", async () => {
    await expect(triggerPipelineRun("not-a-uuid")).rejects.toBeInstanceOf(ApiError);
  });

  it("POSTs to pipelines runs endpoint", async () => {
    const pipelineId = "00000000-0000-4000-8000-000000000001";
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: "00000000-0000-4000-8000-000000000002",
          pipelineId,
          pipelineVersion: 1,
          status: "pending",
          jobs: [],
        }),
        { status: 201 },
      ),
    );

    await triggerPipelineRun(pipelineId, { parameters: { env: "prod" } });

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/pipelines/${pipelineId}/runs`),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          pipelineVersion: null,
          parameters: { env: "prod" },
          async: false,
        }),
      }),
    );
  });
});
