import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Bell, ChevronLeft, Loader2, Mail } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Switch } from "@/components/ui/Switch";
import { useToast } from "@/components/ui/Toast";
import {
  getNotificationPreferences,
  updateNotificationPreferences,
  type NotificationPreferenceResponse,
} from "@/lib/api";

export default function NotificationPreferencesPage() {
  const { addToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [emailEnabled, setEmailEnabled] = useState(true);
  const [inAppEnabled, setInAppEnabled] = useState(true);
  const [quietStart, setQuietStart] = useState("");
  const [quietEnd, setQuietEnd] = useState("");
  const [quietTz, setQuietTz] = useState("");
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      try {
        const prefs = await getNotificationPreferences();
        applyPrefs(prefs);
      } catch (err) {
        addToast({
          type: "error",
          title: "Failed to load preferences",
          description: err instanceof Error ? err.message : "Unknown error",
        });
      } finally {
        setLoading(false);
      }
    })();
  }, [addToast]);

  function applyPrefs(prefs: NotificationPreferenceResponse) {
    setEmailEnabled(prefs.emailEnabled);
    setInAppEnabled(prefs.inAppEnabled);
    setQuietStart(prefs.quietHoursStart ?? "");
    setQuietEnd(prefs.quietHoursEnd ?? "");
    setQuietTz(prefs.quietHoursTz ?? "");
    setUpdatedAt(prefs.updatedAt);
  }

  async function handleSave() {
    setSaving(true);
    try {
      const prefs = await updateNotificationPreferences({
        emailEnabled,
        inAppEnabled,
        quietHoursStart: quietStart.trim() || null,
        quietHoursEnd: quietEnd.trim() || null,
        quietHoursTz: quietTz.trim() || null,
      });
      applyPrefs(prefs);
      addToast({ type: "success", title: "Preferences saved" });
    } catch (err) {
      addToast({
        type: "error",
        title: "Failed to save preferences",
        description: err instanceof Error ? err.message : "Unknown error",
      });
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="p-6 flex items-center justify-center min-h-[40vh]">
        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to="/app/notifications">
            <ChevronLeft className="w-4 h-4 mr-1" />
            Notifications
          </Link>
        </Button>
      </div>

      <div>
        <h1 className="text-2xl font-semibold">Notification preferences</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Control how you receive alerts from Pravah
        </p>
        {updatedAt && (
          <p className="text-xs text-muted-foreground mt-2">
            Last updated {new Date(updatedAt).toLocaleString()}
          </p>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Mail className="w-5 h-5" />
            Email
          </CardTitle>
          <CardDescription>Receive alert emails when rules include your address</CardDescription>
        </CardHeader>
        <div className="px-6 pb-6 flex items-center justify-between">
          <Label htmlFor="email-enabled">Enable email notifications</Label>
          <Switch
            id="email-enabled"
            checked={emailEnabled}
            onCheckedChange={setEmailEnabled}
            aria-label="Enable email notifications"
          />
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Bell className="w-5 h-5" />
            In-app
          </CardTitle>
          <CardDescription>Show alerts in the notification bell and center</CardDescription>
        </CardHeader>
        <div className="px-6 pb-6 flex items-center justify-between">
          <Label htmlFor="in-app-enabled">Enable in-app notifications</Label>
          <Switch
            id="in-app-enabled"
            checked={inAppEnabled}
            onCheckedChange={setInAppEnabled}
            aria-label="Enable in-app notifications"
          />
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Quiet hours</CardTitle>
          <CardDescription>
            Optional window to suppress non-critical notifications (24h format, e.g. 22:00)
          </CardDescription>
        </CardHeader>
        <div className="px-6 pb-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="quiet-start">Start</Label>
              <Input
                id="quiet-start"
                placeholder="22:00"
                value={quietStart}
                onChange={(e) => setQuietStart(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="quiet-end">End</Label>
              <Input
                id="quiet-end"
                placeholder="07:00"
                value={quietEnd}
                onChange={(e) => setQuietEnd(e.target.value)}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="quiet-tz">Timezone</Label>
            <Input
              id="quiet-tz"
              placeholder="America/New_York"
              value={quietTz}
              onChange={(e) => setQuietTz(e.target.value)}
            />
          </div>
        </div>
      </Card>

      <Button onClick={() => void handleSave()} disabled={saving}>
        {saving ? (
          <>
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            Saving…
          </>
        ) : (
          "Save preferences"
        )}
      </Button>
    </div>
  );
}
