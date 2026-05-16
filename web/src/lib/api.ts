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

export type JobSummary = {
  id: string;
  stageId: string;
  stageName: string;
  status: string;
  attempt: number;
};

export type ExecutionResponse = {
  id: string;
  status: string;
  pipelineId: string;
  pipelineVersion: number;
  triggerType: string;
  triggeredBy: string | null;
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
    message = body.message ?? body.error ?? res.statusText;
    errorCode = body.errorCode;
  } catch {
    message = await res.text().catch(() => res.statusText);
  }
  throw new ApiError(message, res.status, errorCode);
}

const DEV_BEARER_STORAGE_KEY = "pravah.devBearerToken";
const DEV_BEARER_CHANGED_EVENT = "pravah:dev-bearer-token-changed";

export function getDevBearerToken(): string | undefined {
  return localStorage.getItem(DEV_BEARER_STORAGE_KEY) ?? undefined;
}

/** Subscribe to dev JWT changes (same-tab saves + other tabs via `storage`). */
export function subscribeDevBearerToken(listener: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }
  const onStorage = (e: StorageEvent) => {
    if (e.key === DEV_BEARER_STORAGE_KEY || e.key === null) {
      listener();
    }
  };
  const onLocal = () => listener();
  window.addEventListener("storage", onStorage);
  window.addEventListener(DEV_BEARER_CHANGED_EVENT, onLocal);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(DEV_BEARER_CHANGED_EVENT, onLocal);
  };
}

export function setDevBearerToken(token: string | null) {
  if (token) {
    localStorage.setItem(DEV_BEARER_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(DEV_BEARER_STORAGE_KEY);
  }
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(DEV_BEARER_CHANGED_EVENT));
  }
}

/** Clear dev JWT and sign out. Use on explicit sign-out actions. */
export function signOut() {
  localStorage.removeItem(DEV_BEARER_STORAGE_KEY);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(DEV_BEARER_CHANGED_EVENT));
  }
}

function authHeaders(): HeadersInit {
  const token = getDevBearerToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
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

export type CreateExecutionRequest = {
  pipelineId: string;
  pipelineVersion?: number | null;
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
