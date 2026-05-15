import { ArrowUpRight, PlayCircle, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Pill } from "@/components/ui/Badge";

const recentWorkflows = [
  { id: "wf-ingest", name: "Daily ingest", status: "active", runs: 12 },
  { id: "wf-dbt", name: "dbt models", status: "active", runs: 4 },
  { id: "wf-archive", name: "Archive to cold", status: "draft", runs: 0 },
];

const activeRuns = [
  { id: "00000000-0000-4000-8000-000000000001", name: "Daily ingest", stage: "validate", progress: 62 },
  { id: "00000000-0000-4000-8000-000000000002", name: "dbt models", stage: "run", progress: 38 },
];

const failures = [
  { id: "00000000-0000-4000-8000-000000000099", name: "Archive to cold", error: "Stage timeout after 900s" },
];

export function DashboardPage() {
  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
          Dashboard
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          Overview shell for <span className="font-medium text-zinc-700 dark:text-zinc-300">US-12.03</span>—replace
          mock rows with GraphQL once the BFF is live (
          <span className="font-medium">ADR-033</span>).
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <Card className="md:col-span-2">
          <CardHeader>
            <CardTitle>Recent workflows</CardTitle>
            <CardDescription>Sortable table and filters ship with US-12.04.</CardDescription>
          </CardHeader>
          <ul className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {recentWorkflows.map((w) => (
              <li key={w.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0">
                  <Link
                    to={`/app/workflows/${w.id}`}
                    className="group flex items-center gap-1 font-medium text-zinc-900 dark:text-zinc-100"
                  >
                    <span className="truncate">{w.name}</span>
                    <ArrowUpRight className="h-4 w-4 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
                  </Link>
                  <p className="text-xs text-zinc-500">{w.runs} runs this week</p>
                </div>
                <Pill>{w.status}</Pill>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Quick actions</CardTitle>
            <CardDescription>US-12.03 entry points.</CardDescription>
          </CardHeader>
          <div className="flex flex-col gap-2">
            <Link
              to="/app/workflows"
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-3 text-sm font-medium text-white shadow-sm transition-colors hover:bg-teal-500 dark:bg-teal-500 dark:hover:bg-teal-400"
            >
              <PlayCircle className="h-4 w-4" aria-hidden />
              Browse workflows
            </Link>
            <Link
              to="/app/runs"
              className="inline-flex items-center justify-center rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm font-medium text-zinc-800 transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:hover:bg-zinc-800"
            >
              View all runs
            </Link>
          </div>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Active runs</CardTitle>
            <CardDescription>Real-time updates via WebSocket land in US-12.10.</CardDescription>
          </CardHeader>
          <ul className="space-y-4">
            {activeRuns.map((r) => (
              <li key={r.id}>
                <Link
                  to={`/app/runs/${r.id}`}
                  className="block rounded-xl border border-zinc-100 p-4 transition-colors hover:border-teal-200 hover:bg-teal-50/40 dark:border-zinc-800 dark:hover:border-teal-900/60 dark:hover:bg-teal-950/20"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="font-medium text-zinc-900 dark:text-zinc-100">{r.name}</p>
                    <Pill>
                      {r.stage} · {r.progress}%
                    </Pill>
                  </div>
                  <div className="mt-3 h-2 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-teal-500 to-cyan-400"
                      style={{ width: `${r.progress}%` }}
                    />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TriangleAlert className="h-5 w-5 text-amber-500" aria-hidden />
              Recent failures
            </CardTitle>
            <CardDescription>Drill into logs with US-12.09.</CardDescription>
          </CardHeader>
          <ul className="space-y-3">
            {failures.map((f) => (
              <li
                key={f.id}
                className="rounded-xl border border-rose-100 bg-rose-50/50 p-4 dark:border-rose-900/40 dark:bg-rose-950/30"
              >
                <Link to={`/app/runs/${f.id}`} className="font-medium text-rose-900 dark:text-rose-200">
                  {f.name}
                </Link>
                <p className="mt-1 text-sm text-rose-800/90 dark:text-rose-300/90">{f.error}</p>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  );
}
