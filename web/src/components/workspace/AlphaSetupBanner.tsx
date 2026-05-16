import { Link } from "react-router-dom";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { DevTokenCard } from "@/components/workspace/DevTokenCard";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import { getDevBearerToken } from "@/lib/api";
import { getResolvedProjectId } from "@/lib/workspace";

type AlphaSetupBannerProps = {
  onProjectSaved?: () => void;
  onTokenSaved?: () => void;
};

export function AlphaSetupBanner({ onProjectSaved, onTokenSaved }: AlphaSetupBannerProps) {
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
          Paste your dev JWT below (from <code className="text-[12px]">make local-dev-token</code>).
        </li>
        <li>Start a run from a workflow row or detail page; open run detail to watch live job status.</li>
      </ol>
      <div className="mt-4 flex flex-col gap-4">
        {!projectId && <ProjectScopeCard onSaved={onProjectSaved} />}
        {!hasToken && <DevTokenCard onSaved={onTokenSaved} />}
      </div>
    </Card>
  );
}
