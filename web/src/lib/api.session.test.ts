import { beforeEach, describe, expect, it, vi } from "vitest";
import { ensureAccessToken, getAccessToken, hasValidSession, setAccessToken } from "@/lib/api";

function jwtWithExp(expSeconds: number): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  const payload = btoa(JSON.stringify({ exp: expSeconds }));
  return `${header}.${payload}.sig`;
}

describe("hasValidSession", () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
  });

  it("returns false when no token is stored", () => {
    setAccessToken(null);
    expect(hasValidSession()).toBe(false);
  });

  it("returns false when token is expired", () => {
    setAccessToken(jwtWithExp(Math.floor(Date.now() / 1000) - 60));
    expect(hasValidSession()).toBe(false);
  });

  it("returns true when token expires in the future", () => {
    setAccessToken(jwtWithExp(Math.floor(Date.now() / 1000) + 3600));
    expect(hasValidSession()).toBe(true);
  });
});

describe("ensureAccessToken", () => {
  beforeEach(() => {
    setAccessToken(null);
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("returns the in-memory token without calling refresh", async () => {
    setAccessToken(jwtWithExp(Math.floor(Date.now() / 1000) + 3600));
    const fetchSpy = vi.spyOn(globalThis, "fetch");

    await expect(ensureAccessToken()).resolves.toBe(getAccessToken());
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("restores the token via refresh when memory is empty", async () => {
    const token = jwtWithExp(Math.floor(Date.now() / 1000) + 3600);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          accessToken: token,
          userId: "user-1",
          tenantId: "tenant-1",
          expiresAt: new Date(Date.now() + 3600_000).toISOString(),
          user: {
            id: "user-1",
            tenantId: "tenant-1",
            email: "dev@localhost.pravah",
            name: "Dev",
            status: "active",
            lastLoginAt: null,
            createdAt: new Date().toISOString(),
          },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(ensureAccessToken()).resolves.toBe(token);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      "/api/v1/auth/refresh",
      expect.objectContaining({ method: "POST", credentials: "include" }),
    );
  });
});
