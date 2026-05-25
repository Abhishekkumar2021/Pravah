import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { ProjectScopeCard } from "@/components/workspace/ProjectScopeCard";
import { getResolvedProjectId } from "@/lib/workspace";

type AlphaSetupBannerProps = {
  onProjectSaved?: () => void;
};

export function AlphaSetupBanner({ onProjectSaved }: AlphaSetupBannerProps) {
  const projectId = getResolvedProjectId();

  if (projectId) {
    return null;
  }

  return (
    <Card className="border-blue-200/80 dark:border-blue-900/50">
      <CardHeader>
        <CardTitle className="text-base">Workspace setup</CardTitle>
        <CardDescription>
          Set your default project UUID so pipeline and run lists are scoped to the right tenant.
          Live run status uses WebSocket when you are signed in (<span className="font-medium">US-12.10</span>).
        </CardDescription>
      </CardHeader>
      <ol className="list-decimal space-y-1 pl-5 text-[13px] text-neutral-600 dark:text-neutral-300">
        <li>Set your project UUID (from seed output or your operator docs).</li>
        <li>Start a run from a workflow row or detail page; open run detail to watch live job status.</li>
      </ol>
      <div className="mt-4">
        <ProjectScopeCard onSaved={onProjectSaved} />
      </div>
    </Card>
  );
}
