const LS_PROJECT = "pravah.defaultProjectId";

/** Project scope for pipeline list/detail (`GET /api/v1/pipelines?projectId=…`). */
export function getResolvedProjectId(): string | undefined {
  const env = (import.meta.env.VITE_PRAVAH_PROJECT_ID as string | undefined)?.trim();
  if (env) return env;
  const ls = localStorage.getItem(LS_PROJECT)?.trim();
  return ls || undefined;
}

export function setDefaultProjectId(id: string | null) {
  if (id?.trim()) {
    localStorage.setItem(LS_PROJECT, id.trim());
  } else {
    localStorage.removeItem(LS_PROJECT);
  }
}
