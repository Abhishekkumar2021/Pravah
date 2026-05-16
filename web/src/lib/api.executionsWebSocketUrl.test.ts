import { afterEach, describe, expect, it, vi } from "vitest";
import { executionsWebSocketUrl } from "@/lib/api";

describe("executionsWebSocketUrl", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("builds ws URL from VITE_PRAVAH_API_BASE (http → ws)", () => {
    vi.stubEnv("VITE_PRAVAH_API_BASE", "http://gateway.example:8080");
    expect(executionsWebSocketUrl("abc")).toBe(
      "ws://gateway.example:8080/ws/v1/executions?access_token=abc",
    );
  });

  it("builds wss URL from https API base", () => {
    vi.stubEnv("VITE_PRAVAH_API_BASE", "https://api.example/");
    expect(executionsWebSocketUrl("tok")).toBe(
      "wss://api.example/ws/v1/executions?access_token=tok",
    );
  });

  it("encodes access token for query string", () => {
    vi.stubEnv("VITE_PRAVAH_API_BASE", "http://h:1");
    expect(executionsWebSocketUrl("a b")).toContain("access_token=a%20b");
  });
});
