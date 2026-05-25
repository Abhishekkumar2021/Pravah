import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ProjectScopeCard } from "./ProjectScopeCard";

describe("ProjectScopeCard", () => {
  it("renders Save button label visibly", () => {
    render(<ProjectScopeCard />);
    expect(screen.getByRole("button", { name: /^save$/i })).toBeVisible();
    expect(screen.getByRole("button", { name: /^save$/i })).toHaveTextContent("Save");
  });

  it("disables Save when project id is not a valid UUID", async () => {
    const user = userEvent.setup();
    render(<ProjectScopeCard />);
    const field = screen.getByRole("textbox", { name: /default project id/i });
    await user.clear(field);
    await user.type(field, "not-a-uuid");
    expect(screen.getByRole("button", { name: /^save$/i })).toBeDisabled();
  });
});
