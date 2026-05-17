import { expect, test } from "@playwright/test";
import {
  EXECUTION_ID,
  PIPELINE_ID,
  mockExecutionBody,
  seedSession,
} from "./helpers";
import { mockExecutionWebSocket, WS_CONNECTED_TITLE } from "./ws-mock";

/**
 * US-12.10 — runs list refreshes when WebSocket pushes tenant execution updates.
 */
test.describe("Runs list realtime (US-12.10)", () => {
  test.beforeEach(async ({ page }) => {
    await seedSession(page);
  });

  test("shows Live badge and refreshes list on execution.updated", async ({ page }) => {
    let listCalls = 0;

    await page.route("**/api/v1/executions**", async (route) => {
      if (route.request().method() !== "GET") {
        await route.continue();
        return;
      }
      listCalls += 1;
      const status = listCalls >= 2 ? "running" : "pending";
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [
            {
              id: EXECUTION_ID,
              pipelineId: PIPELINE_ID,
              pipelineVersion: 1,
              status,
              triggerType: "manual",
              triggeredBy: null,
              createdAt: "2026-05-16T10:00:00.000Z",
              startedAt: null,
              completedAt: null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          last: true,
        }),
      });
    });

    await page.route("**/api/v1/pipelines**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [
            {
              id: PIPELINE_ID,
              name: "E2E Pipeline",
              projectId: "55555555-5555-4555-8555-555555555555",
              description: null,
              currentVersion: 1,
              status: "active",
              createdAt: "2026-05-16T09:00:00.000Z",
              updatedAt: "2026-05-16T09:00:00.000Z",
            },
          ],
          page: 0,
          size: 200,
          totalElements: 1,
          totalPages: 1,
          last: true,
        }),
      });
    });

    await mockExecutionWebSocket(page);

    await page.goto("/app/runs");

    await expect(page.getByTitle(WS_CONNECTED_TITLE)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("running", { exact: true }).first()).toBeVisible({ timeout: 10_000 });
  });
});
