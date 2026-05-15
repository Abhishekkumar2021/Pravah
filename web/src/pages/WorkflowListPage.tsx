import { useCallback, useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { AlphaSetupBanner } from "@/components/workspace/AlphaSetupBanner";
import { TriggerRunButton } from "@/components/workspace/TriggerRunButton";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardTitle } from "@/components/ui/Card";
import { DataTable } from "@/components/ui/DataTable";
import { Pagination } from "@/components/ui/Pagination";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { TableSkeleton } from "@/components/ui/Skeleton";
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
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 20;

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
      const res = await listPipelines(pid, {
        page,
        size: pageSize,
        status: statusFilter === "all" ? undefined : statusFilter,
      });
      setRows(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter]);

  useEffect(() => {
    setPage(0);
  }, [statusFilter]);

  useEffect(() => {
    void load();
  }, [load, projectNonce]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((p) => {
      const desc = p.description?.toLowerCase() ?? "";
      return p.name.toLowerCase().includes(q) || desc.includes(q);
    });
  }, [rows, query]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="page-title">Workflows</h2>
          <p className="page-desc">
            Live data from <span className="font-medium text-neutral-700 dark:text-neutral-300">GET /api/v1/pipelines</span>{" "}
            when a project id and dev JWT are configured (
            <span className="font-medium">US-12.04</span>). Start runs via{" "}
            <span className="font-medium">POST /api/v1/executions</span>.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <div className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" />
            <Input
              type="search"
              placeholder="Search by name…"
              aria-label="Search workflows"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="py-2 pl-9"
            />
          </div>
          <Select
            aria-label="Filter by status"
            value={statusFilter}
            onValueChange={(v) => setStatusFilter(v as typeof statusFilter)}
            options={[
              { value: "all", label: "All statuses" },
              { value: "active", label: "Active" },
              { value: "draft", label: "Draft" },
            ]}
            className="w-full sm:w-[10.5rem]"
          />
          <Button
            type="button"
            variant="secondary"
            className="h-9"
            disabled={!projectId || !hasToken || loading}
            onClick={() => void load()}
          >
            Refresh
          </Button>
        </div>
      </div>

      <AlphaSetupBanner onProjectSaved={() => setProjectNonce((n) => n + 1)} />

      {error && (
        <Card className="border-rose-200 dark:border-rose-900/50">
          <CardTitle className="text-base text-rose-800 dark:text-rose-200">Could not load pipelines</CardTitle>
          <CardDescription className="text-rose-700/90 dark:text-rose-300/90">{error}</CardDescription>
        </Card>
      )}

      {projectId && hasToken && (
        <DataTable
          aria-label="Workflows"
          footer={
            <>
              {!loading && filtered.length === 0 ? (
                <p className="px-4 py-3 text-[13px] text-neutral-500">No workflows match your filters.</p>
              ) : null}
              {!loading && filtered.length > 0 ? (
                <Pagination
                  page={page}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  onPageChange={setPage}
                />
              ) : null}
            </>
          }
        >
          {loading ? (
            <TableSkeleton headers={["Name", "Status", "Version", "Updated", "Actions"]} rows={8} />
          ) : (
            <table className="table-data">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Status</th>
                  <th>Version</th>
                  <th>Updated</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((p) => (
                  <tr key={p.id}>
                    <td className="font-medium text-neutral-900 dark:text-neutral-100">
                      <Link to={`/app/workflows/${p.id}`} className="hover:text-blue-600 dark:hover:text-blue-400">
                        {p.name}
                      </Link>
                      <p className="mt-0.5 font-mono text-[11px] text-neutral-400">{p.id}</p>
                    </td>
                    <td>
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="text-neutral-600 dark:text-neutral-400">v{p.currentVersion}</td>
                    <td className="text-neutral-600 dark:text-neutral-400">{formatShortDateTime(p.updatedAt)}</td>
                    <td className="text-right">
                      <TriggerRunButton
                        pipelineId={p.id}
                        pipelineVersion={p.currentVersion}
                        disabled={p.status.toLowerCase() !== "active"}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </DataTable>
      )}
    </div>
  );
}
