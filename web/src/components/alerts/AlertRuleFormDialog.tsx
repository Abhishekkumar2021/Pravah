import { useState } from "react";
import { X, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import {
  type AlertRuleResponse,
  type AlertRuleChannelConfig,
  createAlertRule,
  updateAlertRule,
} from "@/lib/api";

type Props = {
  rule?: AlertRuleResponse;
  pipelineId?: string;
  onClose: () => void;
  onSuccess: () => void;
};

const EVENT_OPTIONS = [
  { value: "execution.failed", label: "Execution Failed" },
  { value: "execution.timeout", label: "Execution Timeout" },
  { value: "execution.cancelled", label: "Execution Cancelled" },
  { value: "execution.completed", label: "Execution Completed" },
  { value: "job.failed", label: "Stage Failed" },
];

type ChannelFormData = {
  type: "email" | "slack" | "webhook";
  recipients: string;
  webhookUrl: string;
};

export function AlertRuleFormDialog({ rule, pipelineId, onClose, onSuccess }: Props) {
  const isEdit = !!rule;
  const { addToast } = useToast();

  // Parse existing rule data if editing
  const existingConditions = rule ? JSON.parse(rule.conditions) : {};
  const existingChannels: AlertRuleChannelConfig[] = rule ? JSON.parse(rule.channels) : [];

  const [name, setName] = useState(rule?.name ?? "");
  const [description, setDescription] = useState(rule?.description ?? "");
  const [selectedEvents, setSelectedEvents] = useState<string[]>(
    existingConditions.events ?? [],
  );
  const [channels, setChannels] = useState<ChannelFormData[]>(
    existingChannels.map((c) => ({
      type: c.type,
      recipients: c.recipients?.join(", ") ?? "",
      webhookUrl: c.webhookUrl ?? c.url ?? "",
    })) || [{ type: "email", recipients: "", webhookUrl: "" }],
  );
  const [dedupWindowSeconds, setDedupWindowSeconds] = useState(
    rule?.dedupWindowSeconds ?? 300,
  );
  const [submitting, setSubmitting] = useState(false);

  function toggleEvent(event: string) {
    setSelectedEvents((prev) =>
      prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event],
    );
  }

  function addChannel() {
    setChannels((prev) => [...prev, { type: "email", recipients: "", webhookUrl: "" }]);
  }

  function removeChannel(index: number) {
    setChannels((prev) => prev.filter((_, i) => i !== index));
  }

  function updateChannel(index: number, updates: Partial<ChannelFormData>) {
    setChannels((prev) =>
      prev.map((c, i) => (i === index ? { ...c, ...updates } : c)),
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!name.trim()) {
      addToast({ type: "error", title: "Name is required" });
      return;
    }

    if (channels.length === 0) {
      addToast({ type: "error", title: "At least one channel is required" });
      return;
    }

    // Build channels config
    const channelsConfig: AlertRuleChannelConfig[] = [];
    for (const c of channels) {
      if (c.type === "email") {
        const recipients = c.recipients
          .split(",")
          .map((r) => r.trim())
          .filter(Boolean);
        if (recipients.length > 0) {
          channelsConfig.push({ type: "email", recipients });
        }
      } else if (c.type === "slack" && c.webhookUrl.trim()) {
        channelsConfig.push({ type: "slack", webhookUrl: c.webhookUrl.trim() });
      } else if (c.type === "webhook" && c.webhookUrl.trim()) {
        channelsConfig.push({ type: "webhook", url: c.webhookUrl.trim() });
      }
    }

    if (channelsConfig.length === 0) {
      addToast({ type: "error", title: "Configure at least one valid channel" });
      return;
    }

    const conditions = {
      events: selectedEvents.length > 0 ? selectedEvents : undefined,
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await updateAlertRule(rule.id, {
          name: name.trim(),
          description: description.trim() || undefined,
          conditions: JSON.stringify(conditions),
          channels: JSON.stringify(channelsConfig),
          dedupWindowSeconds,
        });
        addToast({ type: "success", title: "Alert rule updated" });
      } else {
        await createAlertRule({
          pipelineId: pipelineId,
          name: name.trim(),
          description: description.trim() || undefined,
          conditions: JSON.stringify(conditions),
          channels: JSON.stringify(channelsConfig),
          dedupWindowSeconds,
        });
        addToast({ type: "success", title: "Alert rule created" });
      }
      onSuccess();
    } catch (err) {
      addToast({
        type: "error",
        title: isEdit ? "Failed to update rule" : "Failed to create rule",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-background rounded-lg shadow-lg w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-4 border-b border-border">
          <h2 className="text-lg font-semibold">
            {isEdit ? "Edit Alert Rule" : "Create Alert Rule"}
          </h2>
          <button onClick={onClose} className="p-1 hover:bg-muted rounded">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-6">
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Name</label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., Critical Failures Alert"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <Input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Optional description"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">Trigger Events</label>
            <p className="text-sm text-muted-foreground mb-2">
              Select which events should trigger this alert. Leave empty to trigger on all events.
            </p>
            <div className="flex flex-wrap gap-2">
              {EVENT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => toggleEvent(opt.value)}
                  className={`px-3 py-1 rounded-full text-sm border transition-colors ${
                    selectedEvents.includes(opt.value)
                      ? "bg-primary text-primary-foreground border-primary"
                      : "bg-muted border-border hover:bg-muted/80"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm font-medium">Notification Channels</label>
              <Button type="button" variant="ghost" size="sm" onClick={addChannel}>
                <Plus className="w-4 h-4 mr-1" />
                Add Channel
              </Button>
            </div>
            <div className="space-y-3">
              {channels.map((channel, index) => (
                <div key={index} className="p-3 border border-border rounded-lg space-y-3">
                  <div className="flex items-center justify-between">
                    <select
                      value={channel.type}
                      onChange={(e) =>
                        updateChannel(index, {
                          type: e.target.value as "email" | "slack" | "webhook",
                        })
                      }
                      className="px-3 py-1.5 border border-border rounded bg-background text-sm"
                    >
                      <option value="email">Email</option>
                      <option value="slack">Slack</option>
                      <option value="webhook">Webhook</option>
                    </select>
                    {channels.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => removeChannel(index)}
                      >
                        <Trash2 className="w-4 h-4 text-red-500" />
                      </Button>
                    )}
                  </div>

                  {channel.type === "email" && (
                    <Input
                      value={channel.recipients}
                      onChange={(e) => updateChannel(index, { recipients: e.target.value })}
                      placeholder="email1@example.com, email2@example.com"
                    />
                  )}

                  {channel.type === "slack" && (
                    <Input
                      value={channel.webhookUrl}
                      onChange={(e) => updateChannel(index, { webhookUrl: e.target.value })}
                      placeholder="https://hooks.slack.com/services/..."
                    />
                  )}

                  {channel.type === "webhook" && (
                    <Input
                      value={channel.webhookUrl}
                      onChange={(e) => updateChannel(index, { webhookUrl: e.target.value })}
                      placeholder="https://your-endpoint.com/webhook"
                    />
                  )}
                </div>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">
              Deduplication Window (seconds)
            </label>
            <p className="text-sm text-muted-foreground mb-2">
              Suppress duplicate alerts for the same event within this time window.
            </p>
            <Input
              type="number"
              min={0}
              value={dedupWindowSeconds}
              onChange={(e) => setDedupWindowSeconds(parseInt(e.target.value) || 0)}
              className="w-32"
            />
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t border-border">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving..." : isEdit ? "Update Rule" : "Create Rule"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
