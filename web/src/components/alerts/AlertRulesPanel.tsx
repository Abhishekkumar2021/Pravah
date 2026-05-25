import { useState, useEffect, useCallback } from "react";
import { Bell, Plus, Trash2, ToggleLeft, ToggleRight, Settings } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageError } from "@/components/ui/PageError";
import { useToast } from "@/components/ui/Toast";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  type AlertRuleResponse,
  listAlertRules,
  deleteAlertRule,
  enableAlertRule,
  disableAlertRule,
} from "@/lib/api";
import { AlertRuleFormDialog } from "@/components/alerts/AlertRuleFormDialog";

type Props = {
  /** When set, only rules scoped to this pipeline are shown and created. */
  pipelineId?: string;
  /** Hide page-level heading (e.g. when embedded in workflow tabs). */
  embedded?: boolean;
};

function parseConditions(conditionsJson: string): string {
  try {
    const cond = JSON.parse(conditionsJson);
    const events = (cond.events as string[]) || [];
    return events.length > 0 ? events.join(", ") : "All events";
  } catch {
    return "All events";
  }
}

function parseChannels(channelsJson: string): string {
  try {
    const channels = JSON.parse(channelsJson) as { type: string }[];
    return channels.map((c) => c.type).join(", ") || "None";
  } catch {
    return "None";
  }
}

export function AlertRulesPanel({ pipelineId, embedded = false }: Props) {
  const [rules, setRules] = useState<AlertRuleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRuleResponse | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AlertRuleResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const { addToast } = useToast();

  const fetchRules = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listAlertRules(pipelineId);
      setRules(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unknown error");
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, [pipelineId]);

  useEffect(() => {
    void fetchRules();
  }, [fetchRules]);

  async function handleToggle(rule: AlertRuleResponse) {
    try {
      if (rule.enabled) {
        await disableAlertRule(rule.id);
        addToast({ type: "success", title: `Rule "${rule.name}" disabled` });
      } else {
        await enableAlertRule(rule.id);
        addToast({ type: "success", title: `Rule "${rule.name}" enabled` });
      }
      void fetchRules();
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to update rule",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await deleteAlertRule(deleteTarget.id);
      addToast({ type: "success", title: `Rule "${deleteTarget.name}" deleted` });
      setDeleteTarget(null);
      void fetchRules();
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to delete rule",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setDeleting(false);
    }
  }

  if (loading) {
    return (
      <div className={embedded ? "space-y-4" : "space-y-6"}>
        {!embedded ? (
          <PageHeader
            icon={Bell}
            iconAccent="from-rose-500 to-pink-600 shadow-rose-500/20 ring-rose-400/20"
            title="Alert Rules"
            description="Configure notifications for workflow failures, completions, and other events."
          />
        ) : null}
        <TableSkeleton headers={["Name", "Triggers", "Channels", "Status", "Actions"]} />
      </div>
    );
  }

  return (
    <div className={embedded ? "space-y-4" : "space-y-6"}>
      {!embedded ? (
        <PageHeader
          icon={Bell}
          iconAccent="from-rose-500 to-pink-600 shadow-rose-500/20 ring-rose-400/20"
          title="Alert Rules"
          description="Configure notifications for workflow failures, completions, and other events."
          actions={
            <Button onClick={() => setShowCreateDialog(true)} className="gap-2 shrink-0">
              <Plus className="h-4 w-4" aria-hidden />
              Create rule
            </Button>
          }
        />
      ) : (
        <div className="flex items-center justify-between gap-4">
          <p className="text-sm text-neutral-500">
            Email, Slack, or webhook notifications when this workflow fails or completes.
          </p>
          <Button onClick={() => setShowCreateDialog(true)} className="gap-2 shrink-0">
            <Plus className="h-4 w-4" aria-hidden />
            Create rule
          </Button>
        </div>
      )}

      {error ? (
        <PageError title="Could not load alert rules" message={error} onRetry={() => void fetchRules()} />
      ) : null}

      {!error && rules.length === 0 ? (
        <EmptyState
          icon={<Bell className="w-12 h-12 text-muted-foreground" />}
          title={pipelineId ? "No alerts for this workflow" : "No alert rules"}
          description={
            pipelineId
              ? "Add a rule to notify your team when this workflow fails or completes."
              : "Create an alert rule to get notified via email, Slack, or webhook."
          }
          action={
            <Button onClick={() => setShowCreateDialog(true)}>
              <Plus className="w-4 h-4 mr-2" />
              Create Alert Rule
            </Button>
          }
        />
      ) : !error ? (
        <DataTable>
          <table className="table-data">
            <thead>
              <tr>
                <th>Name</th>
                <th>Triggers</th>
                <th>Channels</th>
                <th>Status</th>
                <th className="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td className="py-3 px-4">
                    <div className="font-medium">{rule.name}</div>
                    {rule.description && (
                      <div className="text-sm text-muted-foreground truncate max-w-xs">
                        {rule.description}
                      </div>
                    )}
                    {!pipelineId && rule.pipelineId && (
                      <div className="text-xs text-muted-foreground mt-1">Pipeline-specific</div>
                    )}
                  </td>
                  <td className="py-3 px-4">
                    <span className="text-sm">{parseConditions(rule.conditions)}</span>
                  </td>
                  <td className="py-3 px-4">
                    <span className="text-sm capitalize">{parseChannels(rule.channels)}</span>
                  </td>
                  <td className="py-3 px-4">
                    <button
                      type="button"
                      onClick={() => void handleToggle(rule)}
                      className="flex items-center gap-2 text-sm"
                    >
                      {rule.enabled ? (
                        <>
                          <ToggleRight className="w-5 h-5 text-green-500" />
                          <span className="text-green-600">Enabled</span>
                        </>
                      ) : (
                        <>
                          <ToggleLeft className="w-5 h-5 text-muted-foreground" />
                          <span className="text-muted-foreground">Disabled</span>
                        </>
                      )}
                    </button>
                  </td>
                  <td className="py-3 px-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setEditingRule(rule)}
                        title="Edit rule"
                      >
                        <Settings className="w-4 h-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setDeleteTarget(rule)}
                        title="Delete rule"
                      >
                        <Trash2 className="w-4 h-4 text-red-500" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </DataTable>
      ) : null}

      {showCreateDialog && (
        <AlertRuleFormDialog
          pipelineId={pipelineId}
          onClose={() => setShowCreateDialog(false)}
          onSuccess={() => {
            setShowCreateDialog(false);
            void fetchRules();
          }}
        />
      )}

      {editingRule && (
        <AlertRuleFormDialog
          rule={editingRule}
          pipelineId={pipelineId}
          onClose={() => setEditingRule(null)}
          onSuccess={() => {
            setEditingRule(null);
            void fetchRules();
          }}
        />
      )}

      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete alert rule?</DialogTitle>
            <DialogDescription>
              <strong>{deleteTarget?.name}</strong> will stop sending notifications immediately. This
              cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="secondary" disabled={deleting}>
                Cancel
              </Button>
            </DialogClose>
            <Button type="button" variant="danger" loading={deleting} onClick={() => void confirmDelete()}>
              Delete rule
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
