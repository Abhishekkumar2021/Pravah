import { render, screen } from "@testing-library/react";
import { describe, expect, it, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { AlphaSetupBanner } from "./AlphaSetupBanner";

describe("AlphaSetupBanner", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("renders setup steps when project is missing", () => {
    render(
      <ThemeProvider>
        <AlphaSetupBanner />
      </ThemeProvider>,
    );

    expect(screen.getByText("Workspace setup")).toBeVisible();
    expect(screen.getByText(/Set your project UUID/i)).toBeVisible();
  });

  it("hides when project is configured", () => {
    localStorage.setItem("pravah.defaultProjectId", "project-1");

    const { container } = render(
      <ThemeProvider>
        <AlphaSetupBanner />
      </ThemeProvider>,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
