import type { Page } from "@playwright/test";
import { EXECUTION_ID, PIPELINE_ID } from "./helpers";

export const WS_CONNECTED_TITLE = "Connected to execution updates (WebSocket)";

export type ExecutionUpdatedFrame = {
  type: "execution.updated";
  executionId: string;
  status: string;
  occurredAt?: string;
  pipelineId?: string;
};

export async function mockExecutionWebSocket(
  page: Page,
  options?: {
    onRoute?: (url: string) => void;
    pushAfterMs?: number;
    frame?: ExecutionUpdatedFrame;
  },
) {
  const frame: ExecutionUpdatedFrame = options?.frame ?? {
    type: "execution.updated",
    executionId: EXECUTION_ID,
    status: "running",
    occurredAt: "2026-05-16T10:00:01.000Z",
    pipelineId: PIPELINE_ID,
  };
  const delay = options?.pushAfterMs ?? 50;

  await page.routeWebSocket(/\/ws\/v1\/executions/, (ws) => {
    options?.onRoute?.(ws.url());
    setTimeout(() => ws.send(JSON.stringify(frame)), delay);
  });
}
