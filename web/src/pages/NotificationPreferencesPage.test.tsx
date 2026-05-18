import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { ToastProvider } from "@/components/ui/Toast";
import * as api from "@/lib/api";
import NotificationPreferencesPage from "./NotificationPreferencesPage";

function renderPage() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <ToastProvider>
          <NotificationPreferencesPage />
        </ToastProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("NotificationPreferencesPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.accessToken", "jwt");
    vi.spyOn(api, "getNotificationPreferences").mockResolvedValue({
      emailEnabled: true,
      inAppEnabled: true,
      eventPreferences: "{}",
      quietHoursStart: null,
      quietHoursEnd: null,
      quietHoursTz: null,
      updatedAt: "2026-01-01T00:00:00Z",
    });
    vi.spyOn(api, "updateNotificationPreferences").mockResolvedValue({
      emailEnabled: false,
      inAppEnabled: true,
      eventPreferences: "{}",
      quietHoursStart: null,
      quietHoursEnd: null,
      quietHoursTz: null,
      updatedAt: "2026-01-02T00:00:00Z",
    });
  });

  it("loads preferences and saves", async () => {
    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(api.getNotificationPreferences).toHaveBeenCalled());
    expect(await screen.findByRole("heading", { name: "Notification preferences" })).toBeVisible();

    await user.click(screen.getByRole("switch", { name: "Enable email notifications" }));
    await user.click(screen.getByRole("button", { name: "Save preferences" }));

    await waitFor(() => expect(api.updateNotificationPreferences).toHaveBeenCalled());
  });
});
