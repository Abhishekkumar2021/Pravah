import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setDevBearerToken } from "@/lib/api";
import { useExecutionRealtime } from "@/lib/useExecutionRealtime";

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  close() {
    this.onclose?.();
  }
}

describe("useExecutionRealtime", () => {
  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal("WebSocket", MockWebSocket);
    setDevBearerToken("test-jwt");
  });

  afterEach(() => {
    setDevBearerToken(null);
    vi.unstubAllGlobals();
  });

  it("connects with access_token query and filters by execution id", async () => {
    const onExecutionUpdated = vi.fn();
    const { result } = renderHook(() =>
      useExecutionRealtime({
        executionId: "exec-a",
        enabled: true,
        onExecutionUpdated,
      }),
    );

    await waitFor(() => expect(MockWebSocket.instances.length).toBe(1));
    const socket = MockWebSocket.instances[0]!;
    expect(socket.url).toContain("access_token=test-jwt");
    act(() => socket.onopen?.());
    await waitFor(() => expect(result.current.liveConnected).toBe(true));
    act(() => {
      socket.onmessage?.({
        data: JSON.stringify({ type: "execution.updated", executionId: "exec-b" }),
      });
    });
    expect(onExecutionUpdated).not.toHaveBeenCalled();

    act(() => {
      socket.onmessage?.({
        data: JSON.stringify({ type: "execution.updated", executionId: "exec-a" }),
      });
    });
    expect(onExecutionUpdated).toHaveBeenCalledWith("exec-a");
  });

  it("notifies for any execution when executionId is omitted", async () => {
    const onExecutionUpdated = vi.fn();
    renderHook(() =>
      useExecutionRealtime({
        enabled: true,
        onExecutionUpdated,
      }),
    );

    await waitFor(() => expect(MockWebSocket.instances.length).toBe(1));
    const socket = MockWebSocket.instances[0]!;
    act(() => socket.onopen?.());

    act(() => {
      socket.onmessage?.({
        data: JSON.stringify({ type: "execution.updated", executionId: "any-id" }),
      });
    });
    expect(onExecutionUpdated).toHaveBeenCalledWith("any-id");
  });
});
