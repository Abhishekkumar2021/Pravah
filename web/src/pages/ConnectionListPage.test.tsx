import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "@/components/ui/Toast";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
import { ConnectionListPage } from "./ConnectionListPage";

const CONNECTION = {
  id: "conn-1",
  name: "warehouse",
  type: "postgres",
  config: {
    host: "localhost",
    port: 5432,
    database: "pravah",
    username: "pravah",
    credentials: { password: "env:PRAVAH_DB_PASSWORD" },
  },
  createdBy: "user-1",
  createdAt: "2026-05-16T09:00:00Z",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <ToastProvider>
          <ConnectionListPage />
        </ToastProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("ConnectionListPage", () => {
  beforeEach(() => {
    localStorage.setItem("pravah.accessToken", "jwt");
    vi.spyOn(api, "listConnections").mockResolvedValue([CONNECTION]);
    vi.spyOn(api, "testConnection").mockResolvedValue({
      success: true,
      message: "Connection successful",
    });
  });

  it("lists connections with masked password ref", async () => {
    renderPage();
    expect(screen.getByRole("heading", { name: /connections/i })).toBeVisible();
    expect(await screen.findByText("warehouse")).toBeVisible();
    expect(screen.getByText("env:PR****RD")).toBeVisible();
    await waitFor(() => expect(api.listConnections).toHaveBeenCalled());
  });

  it("tests a connection from the row actions", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("warehouse");

    await user.click(screen.getByRole("button", { name: /test connection warehouse/i }));

    await waitFor(() =>
      expect(api.testConnection).toHaveBeenCalledWith("conn-1"),
    );
    expect(await screen.findByText(/connection ok/i)).toBeVisible();
  });
});
