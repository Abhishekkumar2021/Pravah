import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { TopBar } from "./TopBar";

describe("TopBar", () => {
  it("exposes theme control and sign out with accessible names", () => {
    render(
      <MemoryRouter>
        <ThemeProvider>
          <TopBar />
        </ThemeProvider>
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: /theme preference/i })).toBeVisible();
    expect(screen.getByRole("link", { name: /sign out/i })).toBeVisible();
  });
});
