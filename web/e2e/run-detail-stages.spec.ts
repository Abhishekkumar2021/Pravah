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
  });

  test("opens stages tab by default when a job failed and shows error log", async ({ page }) => {
    await page.goto(`/app/runs/${EXECUTION_ID}`);

    await expect(page.getByRole("tab", { name: /Stages & logs/i })).toHaveAttribute(
      "data-state",
      "active",
    );
    await expect(page.getByText(/\[error\]/)).toBeVisible();
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
