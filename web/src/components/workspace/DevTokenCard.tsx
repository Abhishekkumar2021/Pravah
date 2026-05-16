import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { inputBaseClass } from "@/components/ui/Input";
import { cn } from "@/lib/cn";
import { getDevBearerToken, setDevBearerToken } from "@/lib/api";

type DevTokenCardProps = {
  onSaved?: () => void;
  className?: string;
};

export function DevTokenCard({ onSaved, className }: DevTokenCardProps) {
  const [draft, setDraft] = useState(getDevBearerToken() ?? "");
  const [saved, setSaved] = useState(false);

  function save() {
    const trimmed = draft.trim();
    setDevBearerToken(trimmed || null);
    setSaved(true);
    onSaved?.();
    window.setTimeout(() => setSaved(false), 2000);
  }

  function clear() {
    setDraft("");
    setDevBearerToken(null);
    onSaved?.();
  }

  return (
    <Card className={cn("border-blue-200/80 dark:border-blue-900/50", className)}>
      <CardHeader>
        <CardTitle className="text-base">Development JWT</CardTitle>
        <CardDescription>
          Paste the token from{" "}
          <code className="rounded bg-neutral-100 px-1 dark:bg-neutral-800">make local-dev-token</code>{" "}
          (backend). Saved in this browser only — no DevTools console needed.
        </CardDescription>
      </CardHeader>
      <div className="flex flex-col gap-3">
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Paste output of: make local-dev-token"
          rows={4}
          spellCheck={false}
          autoComplete="off"
          aria-label="Development JWT"
          className={cn(
            inputBaseClass,
            "min-h-[5.5rem] resize-y py-2 font-mono text-[11px] leading-relaxed",
          )}
        />
        <div className="flex flex-wrap gap-2">
          <Button type="button" onClick={save}>
            {saved ? "Saved" : "Save token"}
          </Button>
          <Button type="button" variant="secondary" onClick={clear}>
            Clear
          </Button>
        </div>
      </div>
    </Card>
  );
}
