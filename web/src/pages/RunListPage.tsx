import { Link } from "react-router-dom";
import { StatusBadge } from "@/components/ui/Badge";

const runs = [
  {
    id: "00000000-0000-4000-8000-000000000001",
    workflow: "Daily ingest",
    status: "running",
    started: "09:41",
    duration: "3m 12s",
  },
  {
    id: "00000000-0000-4000-8000-000000000002",
    workflow: "dbt models",
    status: "pending",
    started: "09:38",
    duration: "—",
  },
  {
    id: "00000000-0000-4000-8000-000000000080",
    workflow: "Daily ingest",
    status: "succeeded",
    started: "Yesterday",
    duration: "18m 04s",
  },
  {
    id: "00000000-0000-4000-8000-000000000099",
    workflow: "Archive to cold",
    status: "failed",
    started: "Mon",
    duration: "42m 51s",
  },
];

export function RunListPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Runs</h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          List shell for <span className="font-medium text-zinc-700 dark:text-zinc-300">US-12.07</span>. Filters and
          pagination follow the same table patterns as workflows.
        </p>
      </div>

      <div className="surface-card overflow-hidden">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-zinc-200 bg-zinc-50/80 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/50 dark:text-zinc-400">
            <tr>
              <th className="px-6 py-3">Workflow</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Started</th>
              <th className="px-6 py-3">Duration</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {runs.map((r) => (
              <tr key={r.id} className="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/40">
                <td className="px-6 py-4">
                  <Link
                    to={`/app/runs/${r.id}`}
                    className="font-medium text-zinc-900 hover:text-teal-600 dark:text-zinc-100 dark:hover:text-teal-400"
                  >
                    {r.workflow}
                  </Link>
                  <p className="mt-0.5 font-mono text-xs text-zinc-400">{r.id}</p>
                </td>
                <td className="px-6 py-4">
                  <StatusBadge status={r.status} />
                </td>
                <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">{r.started}</td>
                <td className="px-6 py-4 text-zinc-600 dark:text-zinc-400">{r.duration}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
