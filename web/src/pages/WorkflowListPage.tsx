import { Search } from "lucide-react";
import { Link } from "react-router-dom";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

const rows = [
  {
    id: "wf-ingest",
    name: "Daily ingest",
    owner: "data@acme.test",
    status: "active",
    lastRun: "2m ago",
  },
  {
    id: "wf-dbt",
    name: "dbt models",
    owner: "analytics@acme.test",
    status: "active",
    lastRun: "1h ago",
  },
  {
    id: "wf-archive",
    name: "Archive to cold",
    owner: "platform@acme.test",
    status: "draft",
    lastRun: "—",
  },
];

export function WorkflowListPage() {
  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
            Workflows
          </h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Table shell for <span className="font-medium text-zinc-700 dark:text-zinc-300">US-12.04</span>—sorting,
            filters, and pagination come next.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <div className="relative min-w-[200px] flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="search"
              placeholder="Search by name…"
              aria-label="Search workflows"
              className="h-10 w-full rounded-xl border border-zinc-200 bg-white py-2 pl-9 pr-3 text-sm outline-none ring-teal-500/20 focus:border-teal-500 focus:ring-4 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100"
            />
          </div>
          <Button variant="secondary" disabled title="US-02.01">
            New run
          </Button>
        </div>
      </div>

      <div className="surface-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-zinc-200 bg-zinc-50/80 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/50 dark:text-zinc-400">
              <tr>
                <th className="px-6 py-3">Name</th>
                <th className="px-6 py-3">Owner</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Last run</th>
                <th className="px-6 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {rows.map((r) => (
                <tr key={r.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/40">
                  <td className="px-6 py-4 font-medium text-zinc-900 dark:text-zinc-100">
                    <Link to={`/app/workflows/${r.id}`} className="hover:text-teal-600 dark:hover:text-teal-400">
                      {r.name}
                    </Link>
                  </td>
                  <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">{r.owner}</td>
                  <td className="px-6 py-4">
                    <StatusBadge status={r.status} />
                  </td>
                  <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">{r.lastRun}</td>
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
      </div>
    </div>
  );
}
