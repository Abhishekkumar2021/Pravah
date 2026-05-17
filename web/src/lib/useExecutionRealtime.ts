import { useEffect, useRef, useState } from "react";
import { executionsWebSocketUrl, getAccessToken, subscribeSession } from "@/lib/api";

const EXECUTION_UPDATED = "execution.updated";

type ExecutionUpdatedPayload = {
  type?: string;
  executionId?: string;
};

/**
 * Subscribes to gateway → execution-service WebSocket for `execution.updated` frames (US-12.10).
 *
 * When `executionId` is set, only that run triggers `onExecutionUpdated`. When omitted, any tenant
 * execution update triggers the callback (for list/dashboard views).
 */
export function useExecutionRealtime(options: {
  executionId?: string | undefined;
  enabled: boolean;
  onExecutionUpdated: (executionId: string) => void;
}): { liveConnected: boolean } {
  const { executionId, enabled, onExecutionUpdated } = options;
  const onUpdateRef = useRef(onExecutionUpdated);
  onUpdateRef.current = onExecutionUpdated;

  const [liveConnected, setLiveConnected] = useState(false);

  useEffect(() => {
    if (!enabled || typeof window === "undefined") {
      setLiveConnected(false);
      return;
    }

    let cancelled = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let attempt = 0;

    const clearReconnect = () => {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
    };

    const scheduleReconnect = () => {
      if (cancelled) return;
      const token = getAccessToken();
      if (!token) return;
      const delayMs = Math.min(30_000, 1000 * 2 ** Math.min(attempt, 5));
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        open();
      }, delayMs);
    };

    const open = () => {
      if (cancelled) return;
      const token = getAccessToken();
      if (!token) {
        setLiveConnected(false);
        return;
      }

      let url: string;
      try {
        url = executionsWebSocketUrl(token);
      } catch {
        setLiveConnected(false);
        return;
      }

      clearReconnect();
      attempt += 1;
      socket = new WebSocket(url);

      socket.onopen = () => {
        if (cancelled) return;
        attempt = 0;
        setLiveConnected(true);
      };

      socket.onmessage = (event) => {
        if (cancelled) return;
        try {
          const msg = JSON.parse(String(event.data)) as ExecutionUpdatedPayload;
          if (msg.type !== EXECUTION_UPDATED || !msg.executionId) {
            return;
          }
          const id = String(msg.executionId);
          if (executionId !== undefined && id !== executionId) {
            return;
          }
          onUpdateRef.current(id);
        } catch (e) {
          if (import.meta.env.DEV) {
            console.debug("[useExecutionRealtime] ignored non-JSON frame", e);
          }
        }
      };

      socket.onerror = () => {
        // Browser then emits `close`
      };

      socket.onclose = () => {
        socket = null;
        if (cancelled) return;
        setLiveConnected(false);
        scheduleReconnect();
      };
    };

    open();

    const unsubToken = subscribeSession(() => {
      clearReconnect();
      if (socket) {
        const s = socket;
        socket = null;
        s.onclose = null;
        s.close();
      }
      attempt = 0;
      open();
    });

    return () => {
      cancelled = true;
      clearReconnect();
      unsubToken();
      if (socket) {
        const s = socket;
        socket = null;
        s.onclose = null;
        s.close();
      }
      setLiveConnected(false);
    };
  }, [executionId, enabled]);

  return { liveConnected };
}
