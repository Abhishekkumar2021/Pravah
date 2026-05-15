import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { LoginPage } from "./LoginPage";

const { navigateMock } = vi.hoisted(() => ({ navigateMock: vi.fn() }));

vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

function renderLogin() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <LoginPage />
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    navigateMock.mockClear();
  });

  it("shows visible labels on primary and secondary actions", () => {
    renderLogin();
    expect(screen.getByRole("button", { name: /continue/i })).toBeVisible();
    expect(screen.getByRole("button", { name: /continue/i })).toHaveTextContent("Continue");
    expect(screen.getByRole("button", { name: /github/i })).toHaveTextContent("GitHub");
    expect(screen.getByRole("button", { name: /^google$/i })).toHaveTextContent("Google");
  });

  it("submits via Continue and navigates to the app dashboard", async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.click(screen.getByRole("button", { name: /continue/i }));
    expect(navigateMock).toHaveBeenCalledWith("/app/dashboard");
  });
});
