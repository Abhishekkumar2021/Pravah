import { expect, test } from "@playwright/test";
import {
  EXECUTION_ID,
  PIPELINE_ID,
  mockMultiStageExecutionBody,
  mockPipelineBody,
  seedDevBearerToken,
} from "./helpers";

/**
 * US-12.08 — run detail timeline and expandable stage logs.
 */
test.describe("Run detail stages (US-12.08)", () => {
  test.beforeEach(async ({ page }) => {
    await seedDevBearerToken(page);
    await page.route(`**/api/v1/executions/${EXECUTION_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockMultiStageExecutionBody("failed")),
      });
    });
    await page.route(`**/api/v1/pipelines/${PIPELINE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockPipelineBody()),
      });
    });
    await page.route(`**/api/v1/executions/${EXECUTION_ID}/jobs/*/logs`, async (route) => {
      const jobId = route.request().url().split("/jobs/")[1]?.split("/")[0] ?? "";
      const isFailedJob = jobId === "55555555-5555-4555-8555-555555555555";
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          jobId,
          executionId: EXECUTION_ID,
          lines: isFailedJob
            ? [
                {
                  id: "log-1",
                  logTime: "2026-05-16T10:01:00.000Z",
                  level: "ERROR",
                  message: "Stage Transform failed with exit code 1",
                },
              ]
            : [
                {
                  id: "log-2",
                  logTime: "2026-05-16T10:00:00.000Z",
                  level: "INFO",
                  message: "Stage Extract data completed successfully",
                },
              ],
        }),
      });
    });
  });

  test("opens stages tab by default when a job failed and shows error log", async ({ page }) => {
    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await expect(page.getByRole("tab", { name: /Stages & logs/i })).toHaveAttribute(
      "data-state",
      "active",
    );
    await expect(page.getByText(/\[ERROR\]/)).toBeVisible();
    await expect(page.getByText(/Stage Transform failed/)).toBeVisible();
    await expect(page.getByRole("button", { name: /Transform/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  test("switches to timeline tab and shows gantt stages", async ({ page }) => {
    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await page.getByRole("tab", { name: "Timeline" }).click();
    await expect(page.getByRole("tab", { name: "Timeline" })).toHaveAttribute("data-state", "active");
    const timeline = page.getByRole("tabpanel").filter({ has: page.getByRole("heading", { name: "Stage timeline" }) });
    await expect(timeline.getByText("Extract data", { exact: true })).toBeVisible();
    await expect(timeline.getByText("Transform", { exact: true })).toBeVisible();
  });
});
