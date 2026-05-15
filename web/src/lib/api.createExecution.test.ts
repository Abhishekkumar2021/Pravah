import { describe, expect, it } from "vitest";
import { ApiError, createExecution } from "@/lib/api";

describe("createExecution", () => {
  it("rejects non-UUID pipeline id without calling the network", async () => {
    await expect(createExecution("  not-a-uuid  ")).rejects.toMatchObject({
      name: "ApiError",
      message: "Pipeline id must be a UUID",
      errorCode: "INVALID_INPUT",
    });
    await expect(createExecution("not-a-uuid")).rejects.toBeInstanceOf(ApiError);
  });
});
