import { render, screen } from "@testing-library/react";
import { describe, expect, it, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { AlphaSetupBanner } from "./AlphaSetupBanner";

describe("AlphaSetupBanner", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("renders setup steps when project and token are missing", () => {
    render(
      <ThemeProvider>
        <AlphaSetupBanner />
      </ThemeProvider>,
    );

    expect(screen.getByText("Alpha setup")).toBeVisible();
    expect(screen.getByText(/Set your project UUID/i)).toBeVisible();
  });

  it("hides when project and token are configured", () => {
    localStorage.setItem("pravah.defaultProjectId", "project-1");
    localStorage.setItem("pravah.devBearerToken", "jwt");

    const { container } = render(
      <ThemeProvider>
        <AlphaSetupBanner />
      </ThemeProvider>,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
