import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Bell, ChevronLeft, Loader2, Mail, Settings2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Switch } from "@/components/ui/Switch";
import { Skeleton } from "@/components/ui/Skeleton";
import { PageError } from "@/components/ui/PageError";
import { useToast } from "@/components/ui/Toast";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  getNotificationPreferences,
  updateNotificationPreferences,
  type NotificationPreferenceResponse,
} from "@/lib/api";

function PreferencesSkeleton() {
  return (
    <div className="max-w-2xl space-y-6" aria-busy="true" aria-label="Loading preferences">
      {Array.from({ length: 3 }, (_, i) => (
        <Card key={i}>
          <CardHeader>
            <Skeleton className="h-5 w-32" />
            <Skeleton className="mt-2 h-4 w-full max-w-md" />
          </CardHeader>
          <div className="px-6 pb-6">
            <Skeleton className="h-10 w-full" />
          </div>
        </Card>
      ))}
      <Skeleton className="h-10 w-36" />
    </div>
  );
}

export default function NotificationPreferencesPage() {
  const { addToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [emailEnabled, setEmailEnabled] = useState(true);
  const [inAppEnabled, setInAppEnabled] = useState(true);
  const [quietStart, setQuietStart] = useState("");
  const [quietEnd, setQuietEnd] = useState("");
  const [quietTz, setQuietTz] = useState("");
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);

  async function loadPreferences() {
    setLoading(true);
    setError(null);
    try {
      const prefs = await getNotificationPreferences();
      applyPrefs(prefs);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unknown error");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadPreferences();
  }, []);

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

  return (
    <div className="space-y-6">
      <PageHeader
        icon={Settings2}
        iconAccent="from-violet-500 to-purple-600 shadow-violet-500/20 ring-violet-400/20"
        title="Notification preferences"
        description={
          updatedAt && !loading
            ? `Control how you receive alerts from Pravah · Last updated ${new Date(updatedAt).toLocaleString()}`
            : "Control how you receive alerts from Pravah"
        }
        actions={
          <Button variant="ghost" size="sm" asChild>
            <Link to="/app/notifications" className="gap-1">
              <ChevronLeft className="h-4 w-4" aria-hidden />
              Notifications
            </Link>
          </Button>
        }
      />

      {loading ? <PreferencesSkeleton /> : null}

      {error ? (
        <PageError
          title="Could not load preferences"
          message={error}
          onRetry={() => void loadPreferences()}
        />
      ) : null}

      {!loading && !error ? (
        <div className="max-w-2xl space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <Mail className="h-5 w-5" aria-hidden />
                Email
              </CardTitle>
              <CardDescription>Receive alert emails when rules include your address</CardDescription>
            </CardHeader>
            <div className="flex items-center justify-between px-6 pb-6">
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
                <Bell className="h-5 w-5" aria-hidden />
                In-app
              </CardTitle>
              <CardDescription>Show alerts in the notification bell and center</CardDescription>
            </CardHeader>
            <div className="flex items-center justify-between px-6 pb-6">
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
            <div className="space-y-4 px-6 pb-6">
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
                <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />
                Saving…
              </>
            ) : (
              "Save preferences"
            )}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
