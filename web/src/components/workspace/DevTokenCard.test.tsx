import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, beforeEach } from "vitest";
import { DevTokenCard } from "./DevTokenCard";

describe("DevTokenCard", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("saves JWT to localStorage", async () => {
    const user = userEvent.setup();
    render(<DevTokenCard />);

    const area = screen.getByRole("textbox", { name: /development jwt/i });
    await user.click(area);
    await user.paste("test.jwt.token");

    await user.click(screen.getByRole("button", { name: /save token/i }));

    expect(localStorage.getItem("pravah.devBearerToken")).toBe("test.jwt.token");
  });
});
