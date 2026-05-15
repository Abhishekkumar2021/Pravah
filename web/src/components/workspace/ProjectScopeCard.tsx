import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { getResolvedProjectId, setDefaultProjectId } from "@/lib/workspace";

const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

type ProjectScopeCardProps = {
  /** Called after the user saves a new project id (trimmed). */
  onSaved?: () => void;
};

export function ProjectScopeCard({ onSaved }: ProjectScopeCardProps) {
  const [draft, setDraft] = useState(getResolvedProjectId() ?? "");
  const [validationError, setValidationError] = useState<string | null>(null);

  const trimmed = draft.trim();
  const isValid = trimmed === "" || UUID_RE.test(trimmed);

  function save() {
    if (trimmed && !UUID_RE.test(trimmed)) {
      setValidationError("Project ID must be a valid UUID");
      return;
    }
    setValidationError(null);
    setDefaultProjectId(trimmed || null);
    onSaved?.();
  }

  return (
    <Card className="border-amber-200/80 dark:border-amber-900/40">
      <CardHeader>
        <CardTitle className="text-base">Project scope</CardTitle>
        <CardDescription>
          Pipeline APIs require a <span className="font-medium">project UUID</span>. Set{" "}
          <code className="rounded bg-zinc-100 px-1 dark:bg-zinc-800">VITE_PRAVAH_PROJECT_ID</code>{" "}
          for CI, or save one here for local dev (stored as{" "}
          <code className="rounded bg-zinc-100 px-1 dark:bg-zinc-800">localStorage</code>).
        </CardDescription>
      </CardHeader>
      <div className="flex flex-col gap-3 sm:flex-row">
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            setValidationError(null);
          }}
          placeholder="00000000-0000-4000-8000-000000000000"
          aria-label="Default project id"
          aria-invalid={!isValid || !!validationError}
          className={`min-h-10 flex-1 rounded-xl border bg-white px-3 py-2 font-mono text-sm dark:bg-zinc-900 dark:text-zinc-100 ${
            !isValid || validationError
              ? "border-rose-400 dark:border-rose-600"
              : "border-zinc-200 dark:border-zinc-700"
          }`}
          autoComplete="off"
        />
        <Button type="button" onClick={save} disabled={!isValid}>
          Save
        </Button>
      </div>
      {validationError && (
        <p className="text-sm text-rose-600 dark:text-rose-400">{validationError}</p>
      )}
    </Card>
  );
}
