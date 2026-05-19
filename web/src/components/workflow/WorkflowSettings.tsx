import { useState } from "react";
import { AlertTriangle, Archive, Copy, Info, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Switch } from "@/components/ui/Switch";
import type { PipelineDetailResponse } from "@/lib/api";

type WorkflowSettingsProps = {
  pipeline: PipelineDetailResponse;
  onUpdate: (patch: { name?: string; description?: string }) => Promise<void>;
  onArchive?: () => Promise<void>;
  onDelete?: () => Promise<void>;
};

export function WorkflowSettings({
  pipeline,
  onUpdate,
  onArchive,
  onDelete,
}: WorkflowSettingsProps) {
  const [name, setName] = useState(pipeline.name);
  const [description, setDescription] = useState(pipeline.description ?? "");
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [notifyOnFailure, setNotifyOnFailure] = useState(true);
  const [autoRetry, setAutoRetry] = useState(false);
  const [retryCount, setRetryCount] = useState(3);

  const [archiving, setArchiving] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState("");

  const handleSave = async () => {
    setSaving(true);
    setSaveMessage(null);
    setSaveError(null);
    try {
      await onUpdate({ name, description: description || undefined });
      setSaveMessage("Settings saved");
      setTimeout(() => setSaveMessage(null), 3000);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Failed to save");
    } finally {
      setSaving(false);
    }
  };

  const handleArchive = async () => {
    if (!onArchive) return;
    setArchiving(true);
    try {
      await onArchive();
    } catch {
      // handled externally
    } finally {
      setArchiving(false);
    }
  };

  const copyId = () => {
    void navigator.clipboard.writeText(pipeline.id);
  };

  const isActive = pipeline.status.toLowerCase() === "active";

  return (
    <div className="space-y-6">
      {/* Basic info */}
      <Card>
        <CardHeader>
          <CardTitle>General</CardTitle>
          <CardDescription>Basic workflow information</CardDescription>
        </CardHeader>

        <div className="space-y-4 p-4 pt-0">
          <div>
            <Label htmlFor="settings-name">Name</Label>
            <Input
              id="settings-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1.5"
            />
          </div>

          <div>
            <Label htmlFor="settings-description">Description</Label>
            <textarea
              id="settings-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional description for this workflow"
              rows={3}
              className="mt-1.5 w-full rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-400 hover:border-neutral-300 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100 dark:hover:border-neutral-600"
            />
          </div>

          <div className="flex items-center justify-between border-t border-neutral-200 pt-4 dark:border-neutral-800">
            <div>
              <Label className="text-sm">Workflow ID</Label>
              <p className="mt-0.5 flex items-center gap-2 font-mono text-xs text-neutral-500">
                {pipeline.id}
                <button
                  type="button"
                  onClick={copyId}
                  className="text-neutral-400 transition-colors hover:text-neutral-600 dark:hover:text-neutral-300"
                >
                  <Copy className="h-3.5 w-3.5" />
                </button>
              </p>
            </div>
            <div className="text-right">
              <Label className="text-sm">Current version</Label>
              <p className="mt-0.5 font-mono text-xs text-neutral-500">v{pipeline.currentVersion}</p>
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 border-t border-neutral-200 pt-4 dark:border-neutral-800">
            {saveMessage && (
              <p className="text-sm text-emerald-600 dark:text-emerald-400">{saveMessage}</p>
            )}
            {saveError && (
              <p className="text-sm text-rose-600 dark:text-rose-400">{saveError}</p>
            )}
            <Button
              type="button"
              variant="primary"
              onClick={() => void handleSave()}
              disabled={saving || !name.trim()}
            >
              {saving ? "Saving…" : "Save changes"}
            </Button>
          </div>
        </div>
      </Card>

      {/* Execution settings */}
      <Card>
        <CardHeader>
          <CardTitle>Execution</CardTitle>
          <CardDescription>Configure how this workflow runs</CardDescription>
        </CardHeader>

        <div className="space-y-4 p-4 pt-0">
          <div className="flex items-center justify-between">
            <div>
              <Label htmlFor="notify-failure">Notify on failure</Label>
              <p className="text-xs text-neutral-500">Send alerts when the workflow fails</p>
            </div>
            <Switch
              id="notify-failure"
              checked={notifyOnFailure}
              onCheckedChange={setNotifyOnFailure}
            />
          </div>

          <div className="border-t border-neutral-200 pt-4 dark:border-neutral-800">
            <div className="flex items-center justify-between">
              <div>
                <Label htmlFor="auto-retry">Auto retry on failure</Label>
                <p className="text-xs text-neutral-500">Automatically retry failed runs</p>
              </div>
              <Switch
                id="auto-retry"
                checked={autoRetry}
                onCheckedChange={setAutoRetry}
              />
            </div>
            {autoRetry && (
              <div className="mt-3">
                <Label htmlFor="retry-count">Retry count</Label>
                <Input
                  id="retry-count"
                  type="number"
                  min={1}
                  max={10}
                  value={retryCount}
                  onChange={(e) => setRetryCount(parseInt(e.target.value, 10) || 1)}
                  className="mt-1.5 w-24"
                />
              </div>
            )}
          </div>

          <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-200">
            <Info className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Execution settings will take effect on the next run. These features require backend support (US-02.xx).
            </p>
          </div>
        </div>
      </Card>

      {/* Danger zone */}
      <Card className="border-rose-200/80 dark:border-rose-900/50">
        <CardHeader>
          <CardTitle className="text-rose-700 dark:text-rose-300">Danger zone</CardTitle>
          <CardDescription>Irreversible or destructive actions</CardDescription>
        </CardHeader>

        <div className="space-y-4 p-4 pt-0">
          {isActive && onArchive && (
            <div className="flex items-center justify-between rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
              <div>
                <p className="font-medium text-neutral-900 dark:text-neutral-100">Archive workflow</p>
                <p className="text-sm text-neutral-500">Disable and hide this workflow. Can be restored later.</p>
              </div>
              <Button
                type="button"
                variant="secondary"
                className="gap-2"
                onClick={() => void handleArchive()}
                disabled={archiving}
              >
                <Archive className="h-4 w-4" />
                {archiving ? "Archiving…" : "Archive"}
              </Button>
            </div>
          )}

          {onDelete && (
            <div className="flex items-center justify-between rounded-lg border border-rose-200 bg-rose-50/50 p-4 dark:border-rose-900/50 dark:bg-rose-950/20">
              <div>
                <p className="font-medium text-rose-800 dark:text-rose-200">Delete workflow</p>
                <p className="text-sm text-rose-700/80 dark:text-rose-300/80">Permanently delete this workflow and all its data.</p>
              </div>
              <Dialog>
                <DialogTrigger asChild>
                  <Button
                    type="button"
                    variant="secondary"
                    className="gap-2 text-rose-600 hover:bg-rose-100 hover:text-rose-700 dark:text-rose-400 dark:hover:bg-rose-950/50 dark:hover:text-rose-300"
                  >
                    <Trash2 className="h-4 w-4" />
                    Delete
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 dark:bg-rose-900/50">
                      <AlertTriangle className="h-6 w-6 text-rose-600 dark:text-rose-400" />
                    </div>
                    <DialogTitle className="text-center">Delete workflow?</DialogTitle>
                    <DialogDescription className="text-center">
                      This action cannot be undone. All runs, schedules, and alerts associated with this workflow will be permanently deleted.
                    </DialogDescription>
                  </DialogHeader>
                  <div className="py-4">
                    <Label htmlFor="delete-confirm">
                      Type <span className="font-mono font-semibold">{pipeline.name}</span> to confirm
                    </Label>
                    <Input
                      id="delete-confirm"
                      value={deleteConfirm}
                      onChange={(e) => setDeleteConfirm(e.target.value)}
                      placeholder={pipeline.name}
                      className="mt-1.5"
                    />
                  </div>
                  <DialogFooter>
                    <DialogClose asChild>
                      <Button type="button" variant="secondary">
                        Cancel
                      </Button>
                    </DialogClose>
                    <Button
                      type="button"
                      variant="primary"
                      className="bg-rose-600 hover:bg-rose-700 dark:bg-rose-600 dark:hover:bg-rose-700"
                      disabled={deleteConfirm !== pipeline.name}
                      onClick={() => void onDelete?.()}
                    >
                      Delete permanently
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}
