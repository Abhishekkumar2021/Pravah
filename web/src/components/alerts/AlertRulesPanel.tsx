import { useState, useEffect, useCallback } from "react";
import { Bell, Plus, Trash2, ToggleLeft, ToggleRight, Settings } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { DataTable } from "@/components/ui/DataTable";
import { TableSkeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/ui/Toast";
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
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRuleResponse | null>(null);
  const { addToast } = useToast();

  const fetchRules = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listAlertRules(pipelineId);
      setRules(data);
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to load alert rules",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setLoading(false);
    }
  }, [pipelineId, addToast]);

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

  async function handleDelete(rule: AlertRuleResponse) {
    if (!window.confirm(`Delete alert rule "${rule.name}"?`)) return;
    try {
      await deleteAlertRule(rule.id);
      addToast({ type: "success", title: `Rule "${rule.name}" deleted` });
      void fetchRules();
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to delete rule",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    }
  }

  if (loading) {
    return (
      <div className={embedded ? "space-y-4" : "p-6 space-y-6"}>
        {!embedded && <h1 className="text-2xl font-semibold">Alert Rules</h1>}
        <TableSkeleton headers={["Name", "Triggers", "Channels", "Status", "Actions"]} />
      </div>
    );
  }

  return (
    <div className={embedded ? "space-y-4" : "p-6 space-y-6"}>
      <div className="flex items-center justify-between gap-4">
        {!embedded ? (
          <div>
            <h1 className="text-2xl font-semibold">Alert Rules</h1>
            <p className="text-sm text-muted-foreground mt-1">
              Configure notifications for workflow failures, completions, and other events
            </p>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            Email, Slack, or webhook notifications when this workflow fails or completes.
          </p>
        )}
        <Button onClick={() => setShowCreateDialog(true)} className="shrink-0">
          <Plus className="w-4 h-4 mr-2" />
          Create Rule
        </Button>
      </div>

      {rules.length === 0 ? (
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
      ) : (
        <DataTable>
          <table className="w-full">
            <thead>
              <tr className="border-b border-border">
                <th className="text-left py-3 px-4 font-medium text-muted-foreground">Name</th>
                <th className="text-left py-3 px-4 font-medium text-muted-foreground">Triggers</th>
                <th className="text-left py-3 px-4 font-medium text-muted-foreground">Channels</th>
                <th className="text-left py-3 px-4 font-medium text-muted-foreground">Status</th>
                <th className="text-right py-3 px-4 font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id} className="border-b border-border hover:bg-muted/50">
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
                        onClick={() => void handleDelete(rule)}
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
      )}

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
    </div>
  );
}
