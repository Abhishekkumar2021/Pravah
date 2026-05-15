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
    headers: { ...authHeaders() },
  });
  if (!res.ok) {
    throw new Error(await res.text().catch(() => res.statusText));
  }
  return (await res.json()) as ExecutionResponse;
}

export async function cancelExecution(executionId: string): Promise<ExecutionResponse> {
  const res = await fetch(apiUrl(`/api/v1/executions/${executionId}/cancel`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
    },
  });
  if (!res.ok) {
    throw new Error(await res.text().catch(() => res.statusText));
  }
  return (await res.json()) as ExecutionResponse;
}
