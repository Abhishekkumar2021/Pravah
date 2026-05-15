import { Link } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import { getDevBearerToken } from "@/lib/api";
import { getResolvedProjectId } from "@/lib/workspace";

type AlphaSetupBannerProps = {
  onProjectSaved?: () => void;
};

export function AlphaSetupBanner({ onProjectSaved }: AlphaSetupBannerProps) {
  const projectId = getResolvedProjectId();
  const hasToken = Boolean(getDevBearerToken());

  if (projectId && hasToken) {
    return null;
  }

  return (
    <Card className="border-blue-200/80 dark:border-blue-900/50">
      <CardHeader>
        <CardTitle className="text-base">Alpha setup</CardTitle>
        <CardDescription>
          Complete these steps to browse workflows, start runs via REST, and view execution detail.
          Live status updates arrive with <span className="font-medium">US-12.10</span> (WebSocket).
        </CardDescription>
      </CardHeader>
      <ol className="list-decimal space-y-1 pl-5 text-[13px] text-neutral-600 dark:text-neutral-300">
        <li className={projectId ? "text-neutral-400 line-through" : undefined}>
          Set your project UUID (tenant scope for pipeline list).
        </li>
        <li className={hasToken ? "text-neutral-400 line-through" : undefined}>
          Paste a gateway JWT on any{" "}
          <Link to="/app/runs" className="font-medium text-blue-600 hover:underline dark:text-blue-400">
            run detail
          </Link>{" "}
          page (Dev token panel).
        </li>
        <li>Start a run from a workflow row or detail page, then refresh to see status changes until WebSocket ships.</li>
      </ol>
      {!projectId && (
        <div className="mt-4">
          <ProjectScopeCard onSaved={onProjectSaved} />
        </div>
      )}
    </Card>
  );
}
