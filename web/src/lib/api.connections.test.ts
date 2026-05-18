import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createConnection,
  deleteConnection,
  listConnections,
  testConnection,
  updateConnection,
} from "@/lib/api";

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  localStorage.setItem("pravah.accessToken", "test-token");
});

afterEach(() => {
  vi.unstubAllGlobals();
  localStorage.clear();
});

describe("connection API", () => {
  it("listConnections calls GET /api/v1/connections", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        {
          id: "conn-1",
          name: "warehouse",
          type: "postgres",
          config: { host: "localhost", credentials: { password: "env:DB_PASS" } },
          createdBy: "user-1",
          createdAt: "2026-05-16T09:00:00Z",
        },
      ],
    });

    const connections = await listConnections();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/connections",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer test-token" }),
      }),
    );
    expect(connections[0]?.name).toBe("warehouse");
  });

  it("createConnection posts JSON body", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        id: "conn-1",
        name: "warehouse",
        type: "postgres",
        config: {},
        createdBy: "user-1",
        createdAt: "2026-05-16T09:00:00Z",
      }),
    });

    await createConnection({
      name: "warehouse",
      type: "postgres",
      config: { host: "localhost" },
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/connections",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          name: "warehouse",
          type: "postgres",
          config: { host: "localhost" },
        }),
      }),
    );
  });

  it("updateConnection puts config", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        id: "conn-1",
        name: "warehouse",
        type: "postgres",
        config: { host: "db" },
        createdBy: "user-1",
        createdAt: "2026-05-16T09:00:00Z",
      }),
    });

    await updateConnection("conn-1", { config: { host: "db" } });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/connections/conn-1",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("deleteConnection sends DELETE", async () => {
    fetchMock.mockResolvedValueOnce({ ok: true });

    await deleteConnection("conn-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/connections/conn-1",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("testConnection posts to test endpoint", async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: true, message: "Connection successful" }),
    });

    const result = await testConnection("conn-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/connections/conn-1/test",
      expect.objectContaining({ method: "POST" }),
    );
    expect(result.success).toBe(true);
  });
});
