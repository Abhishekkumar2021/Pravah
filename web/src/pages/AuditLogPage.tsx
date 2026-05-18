import { useState, useEffect } from "react";
import { FileText, ChevronLeft, ChevronRight, Filter, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { DataTable } from "@/components/ui/DataTable";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
import { type AuditLogEntry, type AuditLogPage, listAuditLogs } from "@/lib/api";

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const { addToast } = useToast();

  // Filters
  const [actionFilter, setActionFilter] = useState("");
  const [resourceTypeFilter, setResourceTypeFilter] = useState("");

  async function fetchLogs() {
    setLoading(true);
    try {
      const data: AuditLogPage = await listAuditLogs({
        action: actionFilter || undefined,
        resourceType: resourceTypeFilter || undefined,
        page,
        size: 50,
      });
      setLogs(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to load audit logs",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchLogs();
  }, [page, actionFilter, resourceTypeFilter]);

  function formatTimestamp(ts: string): string {
    const date = new Date(ts);
    return date.toLocaleString();
  }

  function getActionBadgeColor(action: string): string {
    if (action.includes("created")) return "bg-green-100 text-green-800";
    if (action.includes("deleted")) return "bg-red-100 text-red-800";
    if (action.includes("updated") || action.includes("modified"))
      return "bg-blue-100 text-blue-800";
    if (action.includes("failed")) return "bg-red-100 text-red-800";
    return "bg-gray-100 text-gray-800";
  }

  function clearFilters() {
    setActionFilter("");
    setResourceTypeFilter("");
    setPage(0);
  }

  if (loading && page === 0) {
    return (
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Audit Log</h1>
        </div>
        <TableSkeleton
          headers={["Timestamp", "Actor", "Action", "Resource", "Details"]}
        />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Audit Log</h1>
          <p className="text-sm text-muted-foreground mt-1">
            Track all system activity and changes
          </p>
        </div>
        <Button variant="secondary" onClick={() => setShowFilters(!showFilters)}>
          <Filter className="w-4 h-4 mr-2" />
          Filters
        </Button>
      </div>

      {showFilters && (
        <div className="p-4 border border-border rounded-lg bg-muted/20 space-y-3">
          <div className="flex items-center justify-between">
            <span className="font-medium">Filters</span>
            {(actionFilter || resourceTypeFilter) && (
              <Button variant="ghost" size="sm" onClick={clearFilters}>
                <X className="w-4 h-4 mr-1" />
                Clear
              </Button>
            )}
          </div>
          <div className="flex gap-4 flex-wrap">
            <div>
              <label className="block text-sm mb-1">Action</label>
              <Input
                value={actionFilter}
                onChange={(e) => {
                  setActionFilter(e.target.value);
                  setPage(0);
                }}
                placeholder="e.g., execution.failed"
                className="w-48"
              />
            </div>
            <div>
              <label className="block text-sm mb-1">Resource Type</label>
              <Input
                value={resourceTypeFilter}
                onChange={(e) => {
                  setResourceTypeFilter(e.target.value);
                  setPage(0);
                }}
                placeholder="e.g., pipeline, execution"
                className="w-48"
              />
            </div>
          </div>
        </div>
      )}

      {logs.length === 0 && !loading ? (
        <EmptyState
          icon={<FileText className="w-12 h-12 text-muted-foreground" />}
          title="No audit entries"
          description={
            actionFilter || resourceTypeFilter
              ? "No entries match your filters. Try adjusting them."
              : "Activity will appear here as you use the system."
          }
          action={
            (actionFilter || resourceTypeFilter) && (
              <Button variant="secondary" onClick={clearFilters}>
                Clear Filters
              </Button>
            )
          }
        />
      ) : (
        <>
          <DataTable>
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-44">
                    Timestamp
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-36">
                    Actor
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-40">
                    Action
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground">
                    Resource
                  </th>
                  <th className="text-left py-3 px-4 font-medium text-muted-foreground w-32">
                    IP
                  </th>
                </tr>
              </thead>
              <tbody>
                {logs.map((entry) => (
                  <tr
                    key={entry.id}
                    className="border-b border-border hover:bg-muted/50"
                  >
                    <td className="py-3 px-4 text-sm text-muted-foreground">
                      {formatTimestamp(entry.createdAt)}
                    </td>
                    <td className="py-3 px-4">
                      <div className="text-sm">
                        {entry.actorName || entry.actorType}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {entry.actorType}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${getActionBadgeColor(entry.action)}`}
                      >
                        {entry.action}
                      </span>
                    </td>
                    <td className="py-3 px-4">
                      <div className="text-sm">{entry.resourceName || entry.resourceId}</div>
                      <div className="text-xs text-muted-foreground">
                        {entry.resourceType}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-sm text-muted-foreground font-mono">
                      {entry.ipAddress || "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DataTable>

          <div className="flex items-center justify-between">
            <div className="text-sm text-muted-foreground">
              Showing {logs.length} of {totalElements} entries
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <span className="text-sm">
                Page {page + 1} of {totalPages || 1}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
