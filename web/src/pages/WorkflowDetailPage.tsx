import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { StatusBadge } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

const tabs = ["Overview", "Runs", "Schedule", "Settings"] as const;

export function WorkflowDetailPage() {
  const { workflowId } = useParams();
  const [tab, setTab] = useState<(typeof tabs)[number]>("Overview");

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <nav className="text-sm text-zinc-500 dark:text-zinc-400" aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-1">
            <li>
              <Link to="/app/workflows" className="hover:text-teal-600 dark:hover:text-teal-400">
                Workflows
              </Link>
            </li>
            <li aria-hidden>/</li>
            <li className="font-medium text-zinc-800 dark:text-zinc-200">{workflowId}</li>
          </ol>
        </nav>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
              {workflowId}
            </h2>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
              Header, DAG, and tabs scaffold for <span className="font-medium">US-12.05</span>.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <StatusBadge status="active" />
            <span className="rounded-full bg-zinc-100 px-3 py-1 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
              Owner: you@tenant
            </span>
          </div>
        </div>
      </div>

      <div className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="flex gap-1 overflow-x-auto" role="tablist" aria-label="Workflow sections">
          {tabs.map((t) => (
            <button
              key={t}
              type="button"
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={cn(
                "relative whitespace-nowrap px-4 py-3 text-sm font-medium transition-colors",
                tab === t
                  ? "text-teal-700 dark:text-teal-300"
                  : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200",
              )}
            >
              {t}
              {tab === t && (
                <span className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-teal-500 dark:bg-teal-400" />
              )}
            </button>
          ))}
        </div>
      </div>

      {tab === "Overview" && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Definition snapshot</CardTitle>
              <CardDescription>
                Visual DAG editor is <span className="font-medium">US-12.06</span>. Placeholder canvas below.
              </CardDescription>
            </CardHeader>
            <div className="flex aspect-[16/9] max-h-72 items-center justify-center rounded-xl border border-dashed border-zinc-300 bg-zinc-50 text-sm text-zinc-500 dark:border-zinc-700 dark:bg-zinc-900/50 dark:text-zinc-400">
              DAG canvas
            </div>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Recent runs</CardTitle>
              <CardDescription>Deep link to run detail (US-12.08).</CardDescription>
            </CardHeader>
            <ul className="space-y-2 text-sm">
              <li>
                <Link
                  className="font-medium text-teal-700 hover:underline dark:text-teal-300"
                  to="/app/runs/00000000-0000-4000-8000-000000000001"
                >
                  Run #1842
                </Link>
                <p className="text-xs text-zinc-500">Running · validate</p>
              </li>
              <li>
                <Link
                  className="font-medium text-teal-700 hover:underline dark:text-teal-300"
                  to="/app/runs/00000000-0000-4000-8000-000000000099"
                >
                  Run #1840
                </Link>
                <p className="text-xs text-zinc-500">Failed · archive</p>
              </li>
            </ul>
          </Card>
        </div>
      )}

      {tab !== "Overview" && (
        <Card>
          <CardHeader>
            <CardTitle>{tab}</CardTitle>
            <CardDescription>Content for this tab is not implemented yet.</CardDescription>
          </CardHeader>
        </Card>
      )}
    </div>
  );
}
