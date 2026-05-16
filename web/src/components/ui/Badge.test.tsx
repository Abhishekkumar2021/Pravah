import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusBadge } from "./Badge";

describe("StatusBadge", () => {
  it("applies skipped styling via shared job status normalization", () => {
    render(<StatusBadge status="SKIPPED" />);
    const badge = screen.getByText("SKIPPED");
    expect(badge.className).toContain("border-dashed");
  });
});
