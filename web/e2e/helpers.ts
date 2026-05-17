import type { Page } from "@playwright/test";

export const ACCESS_TOKEN_KEY = "pravah.accessToken";
export const PROJECT_SCOPE_KEY = "pravah.defaultProjectId";
function buildTestJwt(expSeconds: number): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  const payload = btoa(JSON.stringify({ exp: expSeconds }));
  return `${header}.${payload}.e2e`;
}

/** Non-expired JWT so `hasValidSession()` passes in Playwright. */
export const TEST_JWT = buildTestJwt(Math.floor(Date.now() / 1000) + 86400 * 365);
export const TEST_PROJECT_ID = "55555555-5555-4555-8555-555555555555";

export const EXECUTION_ID = "11111111-1111-4111-8111-111111111111";
export const PIPELINE_ID = "22222222-2222-4222-8222-222222222222";

export async function seedSession(page: Page, token = TEST_JWT) {
  await page.addInitScript(
    ([tokenKey, accessToken, projectKey, projectId]) => {
      localStorage.setItem(tokenKey, accessToken);
      localStorage.setItem(projectKey, projectId);
    },
    [ACCESS_TOKEN_KEY, token, PROJECT_SCOPE_KEY, TEST_PROJECT_ID] as const,
  );
}

export type MockJob = {
  id: string;
  stageId: string;
  stageName: string;
  status: string;
  attempt: number;
  maxAttempts: number;
};

export function mockExecutionBody(status: string, jobs: MockJob[] = defaultJobs(status)) {
  return {
    id: EXECUTION_ID,
    status,
    pipelineId: PIPELINE_ID,
    pipelineVersion: 1,
    triggerType: "manual",
    triggeredBy: "33333333-3333-4333-8333-333333333333",
    createdAt: "2026-05-16T10:00:00.000Z",
    jobs,
  };
}

export function mockMultiStageExecutionBody(status = "failed") {
  return mockExecutionBody(status, [
    {
      id: "44444444-4444-4444-8444-444444444444",
      stageId: "extract",
      stageName: "Extract data",
      status: "succeeded",
      attempt: 1,
      maxAttempts: 3,
    },
    {
      id: "55555555-5555-4555-8555-555555555555",
      stageId: "transform",
      stageName: "Transform",
      status: "failed",
      attempt: 2,
      maxAttempts: 2,
    },
  ]);
}

function defaultJobs(executionStatus: string): MockJob[] {
  const jobStatus =
    executionStatus === "running"
      ? "running"
      : executionStatus === "succeeded"
        ? "succeeded"
        : "pending";
  return [
    {
      id: "44444444-4444-4444-8444-444444444444",
      stageId: "extract",
      stageName: "Extract data",
      status: jobStatus,
      attempt: 1,
      maxAttempts: 3,
    },
  ];
}

export function mockPipelineBody() {
  return {
    id: PIPELINE_ID,
    projectId: TEST_PROJECT_ID,
    name: "E2E Pipeline",
    description: null,
    currentVersion: 1,
    status: "active",
    createdAt: "2026-05-16T09:00:00.000Z",
    updatedAt: "2026-05-16T09:00:00.000Z",
    createdBy: "33333333-3333-4333-8333-333333333333",
    versions: [{ version: 1, publishedAt: "2026-05-16T09:00:00.000Z", publishedBy: null }],
  };
}
