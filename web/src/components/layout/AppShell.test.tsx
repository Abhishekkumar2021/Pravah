import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  it("shows primary nav labels and collapse control text", () => {
    render(
      <MemoryRouter initialEntries={["/app/dashboard"]}>
        <ThemeProvider>
          <Routes>
            <Route path="/app" element={<AppShell />}>
              <Route path="dashboard" element={<div data-testid="page">Dashboard page</div>} />
            </Route>
          </Routes>
        </ThemeProvider>
      </MemoryRouter>,
    );

    expect(screen.getByTestId("page")).toHaveTextContent("Dashboard page");
    expect(screen.getByRole("link", { name: /dashboard/i })).toBeVisible();
    expect(screen.getByRole("link", { name: /workflows/i })).toBeVisible();
    expect(screen.getByRole("link", { name: /runs/i })).toBeVisible();
    expect(screen.getByRole("button", { name: /collapse/i })).toHaveTextContent("Collapse");
  });
});
