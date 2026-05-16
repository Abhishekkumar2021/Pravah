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
          Run detail, runs list, and dashboard use WebSocket for live status when a JWT is set (
          <span className="font-medium">US-12.10</span>).
        </CardDescription>
      </CardHeader>
      <ol className="list-decimal space-y-1 pl-5 text-[13px] text-neutral-600 dark:text-neutral-300">
        <li className={projectId ? "text-neutral-400 line-through" : undefined}>
          Set your project UUID (tenant scope for pipeline list).
        </li>
        <li className={hasToken ? "text-neutral-400 line-through" : undefined}>
          Open <Link to="/app/runs" className="font-medium text-blue-600 hover:underline dark:text-blue-400">Runs</Link>
          , choose any execution, and paste a gateway JWT in the Dev token panel.
        </li>
        <li>Start a run from a workflow row or detail page; open run detail to watch live job status.</li>
      </ol>
      {!projectId && (
        <div className="mt-4">
          <ProjectScopeCard onSaved={onProjectSaved} />
        </div>
      )}
    </Card>
  );
}
