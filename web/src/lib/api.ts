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

export function getDevBearerToken(): string | undefined {
  return localStorage.getItem("pravah.devBearerToken") ?? undefined;
}

export function setDevBearerToken(token: string | null) {
  if (token) {
    localStorage.setItem("pravah.devBearerToken", token);
  } else {
    localStorage.removeItem("pravah.devBearerToken");
  }
}

function authHeaders(): HeadersInit {
  const token = getDevBearerToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function getExecution(executionId: string): Promise<ExecutionResponse> {
  const res = await fetch(apiUrl(`/api/v1/executions/${executionId}`), {
    headers: authHeaders(),
  });
  return handleResponse<ExecutionResponse>(res);
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
  const body: CreateExecutionRequest = {
    pipelineId,
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
