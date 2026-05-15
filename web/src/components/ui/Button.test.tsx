import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Button } from "./Button";

describe("Button", () => {
  it("renders children inside a native button by default", () => {
    render(<Button type="button">Save</Button>);
    const btn = screen.getByRole("button", { name: "Save" });
    expect(btn.tagName).toBe("BUTTON");
    expect(btn).toHaveTextContent("Save");
  });

  it("asChild merges styles onto child element (Radix Slot)", () => {
    render(
      <Button type="button" asChild variant="secondary">
        <a href="/pricing">Pricing</a>
      </Button>,
    );
    const link = screen.getByRole("link", { name: "Pricing" });
    expect(link).toHaveAttribute("href", "/pricing");
    expect(link.className).toContain("border");
  });
});
