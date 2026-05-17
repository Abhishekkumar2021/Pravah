import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import * as api from "@/lib/api";
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
    vi.spyOn(api, "login").mockResolvedValue({
      accessToken: "jwt",
      userId: "u1",
      tenantId: "t1",
      expiresAt: "2026-05-16T12:00:00Z",
      user: {
        id: "u1",
        tenantId: "t1",
        email: "dev@localhost.pravah",
        name: "Dev",
        status: "ACTIVE",
        lastLoginAt: null,
        createdAt: "2026-01-01T00:00:00Z",
      },
    });
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
    await user.type(screen.getByLabelText(/^password$/i), "PravahDev1!");
    await user.click(screen.getByRole("button", { name: /continue/i }));
    expect(api.login).toHaveBeenCalledWith("dev@localhost.pravah", "PravahDev1!");
    expect(navigateMock).toHaveBeenCalledWith("/app/dashboard", { replace: true });
  });
});
