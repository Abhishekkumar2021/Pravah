import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TriggerRunButton } from "@/components/workspace/TriggerRunButton";
import * as api from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...mod,
    getDevBearerToken: vi.fn(),
    subscribeDevBearerToken: vi.fn(() => () => {}),
    createExecution: vi.fn(),
  };
});

const navigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const mod = await importOriginal<typeof import("react-router-dom")>();
  return {
    ...mod,
    useNavigate: () => navigate,
  };
});

describe("TriggerRunButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getDevBearerToken).mockReturnValue(undefined);
  });

  it("disables run when no dev token", () => {
    render(
      <MemoryRouter>
        <TriggerRunButton pipelineId="00000000-0000-4000-8000-000000000001" />
      </MemoryRouter>,
    );
    expect(screen.getByRole("button", { name: /run/i })).toBeDisabled();
  });

  it("starts execution and navigates on success", async () => {
    const user = userEvent.setup();
    vi.mocked(api.getDevBearerToken).mockReturnValue("token");
    vi.mocked(api.createExecution).mockResolvedValue({
      id: "exec-1",
      pipelineId: "00000000-0000-4000-8000-000000000001",
      pipelineVersion: 1,
      status: "pending",
      jobs: [],
    });

    render(
      <MemoryRouter>
        <TriggerRunButton pipelineId="00000000-0000-4000-8000-000000000001" pipelineVersion={1} />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: /^run$/i }));
    expect(api.createExecution).toHaveBeenCalledWith("00000000-0000-4000-8000-000000000001", 1);
    expect(navigate).toHaveBeenCalledWith("/app/runs/exec-1");
  });
});
