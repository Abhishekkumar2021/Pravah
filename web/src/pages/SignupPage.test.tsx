import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import * as api from "@/lib/api";
import { SignupPage } from "./SignupPage";

describe("SignupPage", () => {
  it("submits registration and shows confirmation", async () => {
    vi.spyOn(api, "register").mockResolvedValue({
      email: "new@localhost.pravah",
      message: "Check your email",
    });
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <SignupPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/full name/i), "New User");
    await user.type(screen.getByLabelText(/^email$/i), "new@localhost.pravah");
    await user.type(screen.getByLabelText(/^password$/i), "PravahDev1!");
    await user.click(screen.getByRole("button", { name: /create account/i }));

    await waitFor(() => {
      expect(screen.getByText(/check your inbox/i)).toBeInTheDocument();
    });
    expect(api.register).toHaveBeenCalledWith(
      "new@localhost.pravah",
      "PravahDev1!",
      "New User",
    );
  });
});
