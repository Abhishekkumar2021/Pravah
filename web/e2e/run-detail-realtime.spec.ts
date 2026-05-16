import { expect, test } from "@playwright/test";
import {
  EXECUTION_ID,
  PIPELINE_ID,
  mockExecutionBody,
  mockPipelineBody,
  seedDevBearerToken,
  TEST_JWT,
} from "./helpers";
import { mockExecutionWebSocket, WS_CONNECTED_TITLE } from "./ws-mock";

/**
 * US-12.10 — browser automation for run detail WebSocket refresh (mocked API + WS).
 */
test.describe("Run detail realtime (US-12.10)", () => {
  test.beforeEach(async ({ page }) => {
    await seedDevBearerToken(page);
  });

  test("shows Live badge when WebSocket connects for a pending run", async ({ page }) => {
    let executionFetches = 0;

    await page.route(`**/api/v1/executions/${EXECUTION_ID}`, async (route) => {
      executionFetches += 1;
      const status = executionFetches >= 2 ? "running" : "pending";
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockExecutionBody(status)),
      });
    });

    await page.route(`**/api/v1/pipelines/${PIPELINE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockPipelineBody()),
      });
    });

    await mockExecutionWebSocket(page);

    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await expect(page.getByTitle(WS_CONNECTED_TITLE)).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("running", { exact: true }).first()).toBeVisible({ timeout: 10_000 });
  });

  test("WebSocket URL includes access_token query param", async ({ page }) => {
    await page.route(`**/api/v1/executions/${EXECUTION_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockExecutionBody("pending")),
      });
    });

    await page.route(`**/api/v1/pipelines/${PIPELINE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockPipelineBody()),
      });
    });

    const wsUrls: string[] = [];
    await mockExecutionWebSocket(page, {
      onRoute: (url) => wsUrls.push(url),
      pushAfterMs: 500,
    });

    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await expect.poll(() => wsUrls.length).toBeGreaterThan(0);
    expect(wsUrls[0]).toContain(`access_token=${encodeURIComponent(TEST_JWT)}`);
  });
});

test.describe("Run detail realtime without dev token", () => {
  test("does not open WebSocket without dev bearer token", async ({ page }) => {
    await page.route(`**/api/v1/executions/${EXECUTION_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockExecutionBody("pending")),
      });
    });

    await page.route(`**/api/v1/pipelines/${PIPELINE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockPipelineBody()),
      });
    });

    let wsConnections = 0;
    await page.routeWebSocket(/\/ws\/v1\/executions/, () => {
      wsConnections += 1;
    });

    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await expect(page.getByTitle(WS_CONNECTED_TITLE)).toHaveCount(0);
    expect(wsConnections).toBe(0);
  });
});
