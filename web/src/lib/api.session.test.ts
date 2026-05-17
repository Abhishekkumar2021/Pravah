import { beforeEach, describe, expect, it } from "vitest";
import { hasValidSession, setAccessToken } from "@/lib/api";

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
