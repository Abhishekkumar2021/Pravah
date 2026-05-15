import { useCallback, useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { ApiError, getDevBearerToken, listPipelines, type PipelineResponse } from "@/lib/api";
import { formatShortDateTime } from "@/lib/format";
import { getResolvedProjectId } from "@/lib/workspace";

export function WorkflowListPage() {
  const [projectNonce, setProjectNonce] = useState(0);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"all" | "active" | "draft">("all");
  const [rows, setRows] = useState<PipelineResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const projectId = getResolvedProjectId();
  const hasToken = Boolean(getDevBearerToken());

  const load = useCallback(async () => {
    const pid = getResolvedProjectId();
    const token = getDevBearerToken();
    if (!pid || !token) {
      setRows([]);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await listPipelines(pid, { page: 0, size: 100 });
      setRows(res.content);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((p) => {
      if (statusFilter !== "all" && p.status.toLowerCase() !== statusFilter) {
        return false;
      }
      if (!q) return true;
      const desc = p.description?.toLowerCase() ?? "";
      return p.name.toLowerCase().includes(q) || desc.includes(q);
    });
  }, [rows, query, statusFilter]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Workflows
          </h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Live data from <span className="font-medium text-zinc-700 dark:text-zinc-300">GET /api/v1/pipelines</span>{" "}
            when a project id and dev JWT are configured (
            <span className="font-medium">US-12.04</span>).
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <div className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="search"
              placeholder="Search by name…"
              aria-label="Search workflows"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="h-10 w-full rounded-xl border border-zinc-200 bg-white py-2 pl-9 pr-3 text-sm outline-none ring-teal-500/20 focus:border-teal-500 focus:ring-4 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
            />
          </div>
          <select
            aria-label="Filter by status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as typeof statusFilter)}
            className="h-10 rounded-xl border border-zinc-200 bg-white px-3 text-sm dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
          >
            <option value="all">All statuses</option>
            <option value="active">Active</option>
            <option value="draft">Draft</option>
          </select>
          <Button variant="secondary" disabled title="US-02.01">
            New run
          </Button>
        </div>
      </div>

      {!projectId && <ProjectScopeCard onSaved={() => setProjectNonce((n) => n + 1)} />}

      {projectId && !hasToken && (
        <Card className="border-teal-200/80 dark:border-teal-900/50">
          <CardTitle className="text-base">JWT required</CardTitle>
          <CardDescription>
            Paste a gateway JWT using the <span className="font-medium">Dev token</span> panel on any run detail page,
            then return here to load pipelines.
          </CardDescription>
        </Card>
      )}

      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load pipelines</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {loading && <p className="text-sm text-zinc-500">Loading workflows…</p>}

      {projectId && hasToken && (
        <div className="surface-card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50/80 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/50 dark:text-zinc-400">
                <tr>
                  <th className="px-6 py-3">Name</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3">Version</th>
                  <th className="px-6 py-3">Updated</th>
                  <th className="px-6 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {filtered.map((p) => (
                  <tr key={p.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/40">
                    <td className="px-6 py-4 font-medium text-zinc-900 dark:text-zinc-100">
                      <Link to={`/app/workflows/${p.id}`} className="hover:text-teal-600 dark:hover:text-teal-400">
                        {p.name}
                      </Link>
                      <p className="mt-0.5 font-mono text-xs text-zinc-400">{p.id}</p>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">v{p.currentVersion}</td>
                    <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">{formatShortDateTime(p.updatedAt)}</td>
                    <td className="px-6 py-4 text-right">
                      <Button variant="ghost" className="h-9 px-3 text-xs" disabled>
                        Run
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!loading && filtered.length === 0 && (
            <p className="border-t border-zinc-100 px-6 py-4 text-sm text-zinc-500 dark:border-zinc-800">
              No workflows match your filters.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
