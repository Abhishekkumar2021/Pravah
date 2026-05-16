import { beforeEach, describe, expect, it, vi } from "vitest";
import { getResolvedProjectId, setDefaultProjectId } from "@/lib/workspace";

describe("workspace", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
  });

  it("getResolvedProjectId prefers env over localStorage", () => {
    localStorage.setItem("pravah.defaultProjectId", "from-ls");
    vi.stubEnv("VITE_PRAVAH_PROJECT_ID", "from-env");

    expect(getResolvedProjectId()).toBe("from-env");
  });

  it("getResolvedProjectId falls back to localStorage", () => {
    localStorage.setItem("pravah.defaultProjectId", "project-1");

    expect(getResolvedProjectId()).toBe("project-1");
  });

  it("setDefaultProjectId updates and clears storage", () => {
    setDefaultProjectId("  abc  ");
    expect(localStorage.getItem("pravah.defaultProjectId")).toBe("abc");

    setDefaultProjectId(null);
    expect(localStorage.getItem("pravah.defaultProjectId")).toBeNull();
  });
});
