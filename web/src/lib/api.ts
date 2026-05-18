/**
 * REST client for Pravah gateway (mutations + reads).
 * In dev, use Vite proxy: requests to `/api/...` forward to the gateway (see `vite.config.ts`).
 * Override base URL with `VITE_PRAVAH_API_BASE` when serving the UI from another origin.
 */

const apiBase = import.meta.env.VITE_PRAVAH_API_BASE?.replace(/\/$/, "") ?? "";

function apiUrl(path: string): string {
  if (!path.startsWith("/")) {
    throw new Error(`API path must start with /, got: ${path}`);
  }
  return apiBase ? `${apiBase}${path}` : path;
}

/**
 * Job summary with timing fields for Gantt chart visualization (US-02.09).
 *
 * Timing fields:
 * - queuedAt: when the job was queued (dependencies satisfied, waiting for worker)
 * - startedAt: when execution began (worker picked up the job)
 * - completedAt: when execution finished (success, failure, or cancellation)
 */
export type JobSummary = {
  id: string;
  stageId: string;
  stageName: string;
  status: string;
  attempt: number;
  maxAttempts: number;
  queuedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  output?: Record<string, unknown> | null;
};

export type ExecutionResponse = {
  id: string;
  status: string;
  pipelineId: string;
  pipelineVersion: number;
  triggerType: string;
  triggeredBy: string | null;
  retryOf: string | null;
  retryCount: number;
  createdAt: string;
  jobs: JobSummary[];
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly errorCode?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.ok) {
    return (await res.json()) as T;
  }
  let message = res.statusText;
  let errorCode: string | undefined;
  try {
    const body = await res.json();
    message = body.detail ?? body.message ?? body.error ?? res.statusText;
    errorCode = body.errorCode;
  } catch {
    message = await res.text().catch(() => res.statusText);
  }
  throw new ApiError(message, res.status, errorCode);
}

const ACCESS_TOKEN_STORAGE_KEY = "pravah.accessToken";
const AUTH_USER_STORAGE_KEY = "pravah.authUser";
const SESSION_CHANGED_EVENT = "pravah:session-changed";

export function getAccessToken(): string | undefined {
  return localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) ?? undefined;
}

/** True when a signed-in session exists and the access token is not expired. */
export function hasValidSession(): boolean {
  const token = getAccessToken()?.trim();
  if (!token) return false;
  try {
    const parts = token.split(".");
    if (parts.length < 2) return false;
    const payload = parts[1]!.replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), "=");
    const claims = JSON.parse(atob(padded)) as { exp?: unknown };
    if (typeof claims.exp === "number") {
      return claims.exp * 1000 > Date.now() + 10_000;
    }
    return true;
  } catch {
    return false;
  }
}

export function getStoredUser(): AuthUserResponse | undefined {
  const raw = localStorage.getItem(AUTH_USER_STORAGE_KEY);
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as AuthUserResponse;
  } catch {
    return undefined;
  }
}

function setStoredUser(user: AuthUserResponse | null) {
  if (user) {
    localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
  } else {
    localStorage.removeItem(AUTH_USER_STORAGE_KEY);
  }
}

/** Subscribe to session changes (sign-in, sign-out, token refresh; cross-tab via `storage`). */
export function subscribeSession(listener: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }
  const onStorage = (e: StorageEvent) => {
    if (
      e.key === ACCESS_TOKEN_STORAGE_KEY ||
      e.key === AUTH_USER_STORAGE_KEY ||
      e.key === null
    ) {
      listener();
    }
  };
  const onLocal = () => listener();
  window.addEventListener("storage", onStorage);
  window.addEventListener(SESSION_CHANGED_EVENT, onLocal);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(SESSION_CHANGED_EVENT, onLocal);
  };
}

export function setAccessToken(token: string | null) {
  if (token) {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  }
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
  }
}

/** Clear session and sign out. */
export function signOut() {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  localStorage.removeItem(AUTH_USER_STORAGE_KEY);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(SESSION_CHANGED_EVENT));
  }
}

function authHeaders(): HeadersInit {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export type AuthUserResponse = {
  id: string;
  tenantId: string;
  email: string;
  name: string;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
};

export type AuthTokenResponse = {
  accessToken: string;
  userId: string;
  tenantId: string;
  expiresAt: string;
  user: AuthUserResponse;
};

export type RegisterResponse = {
  email: string;
  message: string;
};

/** Email/password login (US-10.01). Stores JWT for subsequent API calls. */
export async function login(email: string, password: string): Promise<AuthTokenResponse> {
  const res = await fetch(apiUrl("/api/v1/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const body = await handleResponse<AuthTokenResponse>(res);
  setAccessToken(body.accessToken);
  setStoredUser(body.user);
  return body;
}

/** Self-service signup (US-10.01). Sends verification email; does not sign in. */
export async function register(
  email: string,
  password: string,
  name: string,
): Promise<RegisterResponse> {
  const res = await fetch(apiUrl("/api/v1/auth/register"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, name }),
  });
  return handleResponse<RegisterResponse>(res);
}

export async function verifyEmail(token: string): Promise<void> {
  const res = await fetch(apiUrl("/api/v1/auth/verify-email"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token: token.trim() }),
  });
  if (!res.ok) {
    await handleResponse<void>(res);
  }
}

export async function resendVerificationEmail(email: string): Promise<void> {
  const res = await fetch(apiUrl("/api/v1/auth/verify-email/resend"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  if (!res.ok) {
    await handleResponse<void>(res);
  }
}

export async function requestPasswordReset(email: string): Promise<void> {
  const res = await fetch(apiUrl("/api/v1/auth/password-reset/request"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  if (!res.ok) {
    await handleResponse<void>(res);
  }
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  const res = await fetch(apiUrl("/api/v1/auth/password-reset/confirm"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword }),
  });
  if (!res.ok) {
    await handleResponse<void>(res);
  }
}

export type ValidatePipelineResponse = { valid: boolean };

export async function validatePipelineDefinition(definitionYaml: string): Promise<ValidatePipelineResponse> {
  const res = await fetch(apiUrl("/api/v1/pipelines/validate"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ definitionYaml }),
  });
  return handleResponse<ValidatePipelineResponse>(res);
}

export async function publishPipeline(
  pipelineId: string,
  definitionYaml: string,
): Promise<PipelineResponse> {
  const res = await fetch(apiUrl(`/api/v1/pipelines/${pipelineId}/publish`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ definitionYaml }),
  });
  return handleResponse<PipelineResponse>(res);
}

const EXECUTIONS_WS_PATH = "/ws/v1/executions";

/**
 * WebSocket URL for execution-service realtime stream (tenant-scoped JWT on connect).
 *
 * Uses the `access_token` query parameter because browser `WebSocket` cannot set `Authorization`
 * reliably. Treat tokens as sensitive: they can appear in proxy access logs if the log format
 * includes the request URI query string—disable or redact at INFO in production.
 *
 * In local dev, same host as the SPA with Vite proxying `/ws` to the gateway (see `vite.config.ts`).
 */
export function executionsWebSocketUrl(accessToken: string): string {
  const path = `${EXECUTIONS_WS_PATH}?access_token=${encodeURIComponent(accessToken)}`;
  const base = import.meta.env.VITE_PRAVAH_API_BASE?.replace(/\/$/, "") ?? "";
  if (base) {
    const u = new URL(base);
    const wsProto = u.protocol === "https:" ? "wss:" : "ws:";
    return `${wsProto}//${u.host}${path}`;
  }
  if (typeof window === "undefined") {
    return `ws://127.0.0.1${path}`;
  }
  const wsProto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${wsProto}//${window.location.host}${path}`;
}

export async function getExecution(executionId: string): Promise<ExecutionResponse> {
  const res = await fetch(apiUrl(`/api/v1/executions/${executionId}`), {
    headers: authHeaders(),
  });
  return handleResponse<ExecutionResponse>(res);
}

export type JobLogLine = {
  id: string;
  logTime: string;
  level: string;
  message: string;
};

export type JobLogsResponse = {
  jobId: string;
  executionId: string;
  lines: JobLogLine[];
};

/** Per-job logs for run detail (US-02.03 / US-12.09). */
export async function getJobLogs(
  executionId: string,
  jobId: string,
  opts?: { level?: string },
): Promise<JobLogsResponse> {
  const path = withQuery(`/api/v1/executions/${executionId}/jobs/${jobId}/logs`, {
    level: opts?.level,
  });
  const res = await fetch(apiUrl(path), { headers: authHeaders() });
  return handleResponse<JobLogsResponse>(res);
}

export async function cancelExecution(executionId: string): Promise<ExecutionResponse> {
  const res = await fetch(apiUrl(`/api/v1/executions/${executionId}/cancel`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
  });
  return handleResponse<ExecutionResponse>(res);
}

/** Retry from a failed stage (US-02.05). Creates a new execution linked via retryOf. */
export async function retryExecution(
  executionId: string,
  fromStageId: string,
): Promise<CreateExecutionResponse> {
  const res = await fetch(apiUrl(`/api/v1/executions/${executionId}/retry`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({ fromStageId }),
  });
  return handleResponse<CreateExecutionResponse>(res);
}

export type PipelineResponse = {
  id: string;
  projectId: string;
  name: string;
  description: string | null;
  currentVersion: number;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type PipelineListResponse = {
  content: PipelineResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

export type PipelineDetailResponse = {
  id: string;
  projectId: string;
  name: string;
  description: string | null;
  currentVersion: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  versions: { version: number; publishedAt: string | null; publishedBy: string | null }[];
};

export type ExecutionListItem = {
  id: string;
  pipelineId: string;
  pipelineVersion: number;
  status: string;
  triggerType: string;
  triggeredBy: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

export type ListExecutionsResponse = {
  content: ExecutionListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

const PIPELINE_ID_UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function withQuery(path: string, params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === "") continue;
    search.set(k, String(v));
  }
  const q = search.toString();
  return q ? `${path}?${q}` : path;
}

export async function listPipelines(
  projectId: string,
  opts?: { status?: string; page?: number; size?: number },
): Promise<PipelineListResponse> {
  const path = withQuery("/api/v1/pipelines", {
    projectId,
    status: opts?.status,
    page: opts?.page ?? 0,
    size: opts?.size ?? 50,
  });
  const res = await fetch(apiUrl(path), { headers: authHeaders() });
  return handleResponse<PipelineListResponse>(res);
}

export async function getPipeline(pipelineId: string): Promise<PipelineDetailResponse> {
  const res = await fetch(apiUrl(`/api/v1/pipelines/${pipelineId}`), {
    headers: authHeaders(),
  });
  return handleResponse<PipelineDetailResponse>(res);
}

export type PipelineVersionDefinitionResponse = {
  pipelineId: string;
  version: number;
  definition: PipelineDefinition;
  publishedAt: string | null;
  publishedBy: string | null;
};

export type PipelineDefinition = {
  name?: string;
  description?: string;
  stages?: StageDefinition[];
  variables?: Record<string, unknown>;
  environments?: Record<string, unknown>;
  retry?: Record<string, unknown>;
  timeout?: Record<string, unknown>;
};

export type StageDefinition = {
  id: string;
  name?: string;
  type?: string;
  dependsOn?: string[];
  depends_on?: string[];
  config?: Record<string, unknown>;
  retry?: Record<string, unknown>;
  timeout?: Record<string, unknown>;
};

export async function getPipelineVersionDefinition(
  pipelineId: string,
  version: number,
): Promise<PipelineVersionDefinitionResponse> {
  const res = await fetch(apiUrl(`/api/v1/pipelines/${pipelineId}/versions/${version}`), {
    headers: authHeaders(),
  });
  return handleResponse<PipelineVersionDefinitionResponse>(res);
}

export type CreateExecutionRequest = {
  pipelineId: string;
  pipelineVersion?: number | null;
  parameters?: Record<string, unknown> | null;
};

export type TriggerPipelineRunRequest = {
  pipelineVersion?: number | null;
  parameters?: Record<string, unknown> | null;
  async?: boolean | null;
};

export type TriggerPipelineRunResponse = {
  id: string;
};

export type CreateExecutionJobResponse = {
  id: string;
  stageId: string;
  stageName: string;
  status: string;
};

export type CreateExecutionResponse = {
  id: string;
  pipelineId: string;
  pipelineVersion: number;
  status: string;
  jobs: CreateExecutionJobResponse[];
};

/** Start a manual execution (POST /api/v1/executions). */
export async function createExecution(
  pipelineId: string,
  pipelineVersion?: number | null,
): Promise<CreateExecutionResponse> {
  const id = pipelineId.trim();
  if (!PIPELINE_ID_UUID_RE.test(id)) {
    throw new ApiError("Pipeline id must be a UUID", 400, "INVALID_INPUT");
  }
  const body: CreateExecutionRequest = {
    pipelineId: id,
    pipelineVersion: pipelineVersion ?? null,
    parameters: null,
  };
  const res = await fetch(apiUrl("/api/v1/executions"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  });
  return handleResponse<CreateExecutionResponse>(res);
}

/** Trigger a pipeline run via API (POST /api/v1/pipelines/{id}/runs, US-03.08). */
export async function triggerPipelineRun(
  pipelineId: string,
  options?: {
    pipelineVersion?: number | null;
    parameters?: Record<string, unknown> | null;
    async?: boolean;
  },
): Promise<CreateExecutionResponse | TriggerPipelineRunResponse> {
  const id = pipelineId.trim();
  if (!PIPELINE_ID_UUID_RE.test(id)) {
    throw new ApiError("Pipeline id must be a UUID", 400, "INVALID_INPUT");
  }
  const body: TriggerPipelineRunRequest = {
    pipelineVersion: options?.pipelineVersion ?? null,
    parameters: options?.parameters ?? null,
    async: options?.async ?? false,
  };
  const res = await fetch(apiUrl(`/api/v1/pipelines/${encodeURIComponent(id)}/runs`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  });
  if (options?.async) {
    return handleResponse<TriggerPipelineRunResponse>(res);
  }
  return handleResponse<CreateExecutionResponse>(res);
}

export type ScheduleResponse = {
  id: string;
  pipelineId: string;
  name: string;
  cronExpression: string;
  timezone: string;
  active: boolean;
  nextRunAt: string | null;
  lastRunAt: string | null;
  createdAt: string;
};

export type CreateScheduleRequest = {
  pipelineId: string;
  name: string;
  cronExpression: string;
  timezone: string;
};

export type CronPreviewRequest = {
  cronExpression: string;
  timezone: string;
  after?: string | null;
  count?: number;
};

export type CronPreviewResponse = {
  description: string;
  nextRuns: string[];
};

export async function listSchedules(pipelineId: string): Promise<ScheduleResponse[]> {
  const path = withQuery("/api/v1/schedules", { pipelineId });
  const res = await fetch(apiUrl(path), { headers: authHeaders() });
  return handleResponse<ScheduleResponse[]>(res);
}

export async function createSchedule(body: CreateScheduleRequest): Promise<ScheduleResponse> {
  const res = await fetch(apiUrl("/api/v1/schedules"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  });
  return handleResponse<ScheduleResponse>(res);
}

export async function previewCron(body: CronPreviewRequest): Promise<CronPreviewResponse> {
  const res = await fetch(apiUrl("/api/v1/schedules/preview"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
    body: JSON.stringify({
      cronExpression: body.cronExpression,
      timezone: body.timezone,
      after: body.after ?? null,
      count: body.count ?? 10,
    }),
  });
  return handleResponse<CronPreviewResponse>(res);
}

export async function pauseSchedule(scheduleId: string): Promise<ScheduleResponse> {
  const res = await fetch(apiUrl(`/api/v1/schedules/${scheduleId}/pause`), {
    method: "POST",
    headers: authHeaders(),
  });
  return handleResponse<ScheduleResponse>(res);
}

export async function resumeSchedule(scheduleId: string): Promise<ScheduleResponse> {
  const res = await fetch(apiUrl(`/api/v1/schedules/${scheduleId}/resume`), {
    method: "POST",
    headers: authHeaders(),
  });
  return handleResponse<ScheduleResponse>(res);
}

export async function deleteSchedule(scheduleId: string): Promise<void> {
  const res = await fetch(apiUrl(`/api/v1/schedules/${scheduleId}`), {
    method: "DELETE",
    headers: authHeaders(),
  });
  if (!res.ok) {
    await handleResponse<unknown>(res);
  }
}

export async function listExecutions(opts?: {
  status?: string;
  pipelineId?: string;
  page?: number;
  size?: number;
}): Promise<ListExecutionsResponse> {
  const path = withQuery("/api/v1/executions", {
    status: opts?.status,
    pipelineId: opts?.pipelineId,
    page: opts?.page ?? 0,
    size: opts?.size ?? 50,
  });
  const res = await fetch(apiUrl(path), { headers: authHeaders() });
  return handleResponse<ListExecutionsResponse>(res);
}
