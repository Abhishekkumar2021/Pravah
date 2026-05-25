import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, login } from "@/lib/api";

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api errors", () => {
  it("login throws ApiError on failure", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ message: "Invalid credentials", errorCode: "AUTH_FAILED" }),
    });

    await expect(login("dev@localhost.pravah", "wrong")).rejects.toMatchObject({
      name: "ApiError",
      status: 401,
      message: "Invalid credentials",
    });
  });

  it("ApiError exposes status and code", () => {
    const err = new ApiError("Quota exceeded", 429, "QUOTA");
    expect(err.status).toBe(429);
    expect(err.errorCode).toBe("QUOTA");
  });
});
