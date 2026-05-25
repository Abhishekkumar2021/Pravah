import { useState } from "react";
import { Link } from "react-router-dom";
import { Bell, Key, Moon, Palette, Settings as SettingsIcon, Shield, Sun, User, Monitor, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { useTheme, type ThemePreference } from "@/lib/theme";
import { getStoredUser, updateProfileName } from "@/lib/api";

const tabs = [
  { id: "profile", icon: User, label: "Profile" },
  { id: "appearance", icon: Palette, label: "Appearance" },
  { id: "notifications", icon: Bell, label: "Notifications" },
  { id: "security", icon: Shield, label: "Security" },
  { id: "api-keys", icon: Key, label: "API Keys" },
] as const;

export function SettingsPage() {
  const { preference: theme, setPreference: setTheme } = useTheme();
  const user = getStoredUser();
  const [tab, setTab] = useState<(typeof tabs)[number]["id"]>("profile");

  const [displayName, setDisplayName] = useState(user?.name ?? "");
  const [email] = useState(user?.email ?? "");
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  const handleSaveProfile = async () => {
    if (!user?.id) {
      setSaveMessage("Not signed in");
      return;
    }
    setSaving(true);
    setSaveMessage(null);
    try {
      await updateProfileName(user.id, displayName.trim());
      setSaveMessage("Profile saved");
      setTimeout(() => setSaveMessage(null), 3000);
    } catch {
      setSaveMessage("Failed to save");
    } finally {
      setSaving(false);
    }
  };

  const themeOptions = [
    { value: "system", label: "System" },
    { value: "light", label: "Light" },
    { value: "dark", label: "Dark" },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="page-title flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-neutral-500 to-neutral-600 text-white shadow-lg shadow-neutral-500/20 ring-1 ring-neutral-400/20">
            <SettingsIcon className="h-5 w-5" />
          </span>
          <span className="bg-gradient-to-r from-neutral-900 to-neutral-700 bg-clip-text text-transparent dark:from-neutral-100 dark:to-neutral-300">
            Settings
          </span>
        </h1>
        <p className="page-desc mt-2">Manage your account and preferences</p>
      </div>

      <Tabs value={tab} onValueChange={(v) => setTab(v as (typeof tabs)[number]["id"])}>
        <TabsList aria-label="Settings sections">
          {tabs.map((t) => (
            <TabsTrigger key={t.id} value={t.id} className="gap-1.5">
              <t.icon className="h-3.5 w-3.5" aria-hidden />
              <span className="hidden sm:inline">{t.label}</span>
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>Profile</CardTitle>
              <CardDescription>Manage your account information</CardDescription>
            </CardHeader>
            <div className="space-y-4 p-4 pt-0">
              <div>
                <Label htmlFor="profile-name">Display name</Label>
                <Input
                  id="profile-name"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="mt-1.5"
                />
              </div>
              <div>
                <Label htmlFor="profile-email">Email</Label>
                <Input
                  id="profile-email"
                  type="email"
                  value={email}
                  disabled
                  className="mt-1.5"
                />
                <p className="mt-1 text-xs text-neutral-500">
                  Contact support to change your email
                </p>
              </div>
              <div className="flex items-center justify-end gap-2 border-t border-neutral-200 pt-4 dark:border-neutral-800">
                {saveMessage && (
                  <p className="text-sm text-emerald-600 dark:text-emerald-400">{saveMessage}</p>
                )}
                <Button
                  type="button"
                  variant="primary"
                  onClick={() => void handleSaveProfile()}
                  disabled={saving}
                >
                  {saving ? "Saving…" : "Save changes"}
                </Button>
              </div>
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="appearance">
          <Card>
            <CardHeader>
              <CardTitle>Appearance</CardTitle>
              <CardDescription>Customize how Pravah looks</CardDescription>
            </CardHeader>
            <div className="space-y-6 p-4 pt-0">
              <div>
                <Label htmlFor="theme-select">Theme</Label>
                <Select
                  id="theme-select"
                  aria-label="Theme preference"
                  value={theme}
                  onValueChange={(v) => setTheme(v as ThemePreference)}
                  options={themeOptions}
                  className="mt-1.5 w-full sm:w-48"
                />
                <p className="mt-1 text-xs text-neutral-500">
                  System will follow your OS preference
                </p>
              </div>

              <div className="grid gap-3 sm:grid-cols-3">
                <button
                  type="button"
                  onClick={() => setTheme("light")}
                  className={`flex flex-col items-center gap-2 rounded-xl border-2 p-4 transition-all ${
                    theme === "light"
                      ? "border-blue-500 bg-blue-50 ring-2 ring-blue-500/20 dark:bg-blue-950/30"
                      : "border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600"
                  }`}
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-amber-600">
                    <Sun className="h-5 w-5" />
                  </div>
                  <span className="text-sm font-medium">Light</span>
                </button>
                <button
                  type="button"
                  onClick={() => setTheme("dark")}
                  className={`flex flex-col items-center gap-2 rounded-xl border-2 p-4 transition-all ${
                    theme === "dark"
                      ? "border-blue-500 bg-blue-50 ring-2 ring-blue-500/20 dark:bg-blue-950/30"
                      : "border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600"
                  }`}
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 dark:bg-indigo-900/50 dark:text-indigo-400">
                    <Moon className="h-5 w-5" />
                  </div>
                  <span className="text-sm font-medium">Dark</span>
                </button>
                <button
                  type="button"
                  onClick={() => setTheme("system")}
                  className={`flex flex-col items-center gap-2 rounded-xl border-2 p-4 transition-all ${
                    theme === "system"
                      ? "border-blue-500 bg-blue-50 ring-2 ring-blue-500/20 dark:bg-blue-950/30"
                      : "border-neutral-200 hover:border-neutral-300 dark:border-neutral-700 dark:hover:border-neutral-600"
                  }`}
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-neutral-100 text-neutral-600 dark:bg-neutral-800 dark:text-neutral-400">
                    <Monitor className="h-5 w-5" />
                  </div>
                  <span className="text-sm font-medium">System</span>
                </button>
              </div>
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>Notifications</CardTitle>
              <CardDescription>
                Delivery preferences and quiet hours are managed on a dedicated page wired to the API.
              </CardDescription>
            </CardHeader>
            <div className="flex flex-col gap-4 p-4 pt-0 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-sm text-neutral-600 dark:text-neutral-400">
                Configure email and in-app delivery, plus optional quiet hours.
              </p>
              <Button variant="primary" asChild>
                <Link to="/app/notification-preferences">
                  Open notification preferences
                  <ArrowRight className="h-4 w-4" aria-hidden />
                </Link>
              </Button>
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="security">
          <Card>
            <CardHeader>
              <CardTitle>Security</CardTitle>
              <CardDescription>Manage your password and security settings</CardDescription>
            </CardHeader>
            <div className="space-y-4 p-4 pt-0">
              <p className="text-sm text-neutral-600 dark:text-neutral-400">
                In-app password change is not available yet. Use the secure reset flow to set a new password
                via email.
              </p>
              <div className="flex flex-wrap gap-2 border-t border-neutral-200 pt-4 dark:border-neutral-800">
                <Button type="button" variant="primary" asChild>
                  <Link to="/forgot-password">Reset password via email</Link>
                </Button>
              </div>
            </div>
          </Card>
        </TabsContent>

        <TabsContent value="api-keys">
          <Card>
            <CardHeader>
              <CardTitle>API Keys</CardTitle>
              <CardDescription>Manage your API keys for programmatic access</CardDescription>
            </CardHeader>
            <div className="p-4 pt-0">
              <div className="flex flex-col items-center py-8 text-center">
                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
                  <Key className="h-6 w-6 text-neutral-400" />
                </div>
                <p className="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  No API keys yet
                </p>
                <p className="mt-1 text-sm text-neutral-500">
                  API key management will be available in a future release.
                </p>
                <Button type="button" variant="secondary" className="mt-4" disabled>
                  Create API key
                </Button>
              </div>
            </div>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
