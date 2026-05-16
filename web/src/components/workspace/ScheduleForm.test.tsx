import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ScheduleForm } from "@/components/workspace/ScheduleForm";
import { ToastProvider } from "@/components/ui/Toast";
import * as api from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...mod,
    previewCron: vi.fn(),
    createSchedule: vi.fn(),
  };
});

const pipelineId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001";

function renderForm() {
  const onCreated = vi.fn();
  const onError = vi.fn();
  render(
    <ToastProvider>
      <ScheduleForm pipelineId={pipelineId} onCreated={onCreated} onError={onError} />
    </ToastProvider>,
  );
  return { onCreated, onError };
}

describe("ScheduleForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.previewCron).mockResolvedValue({
      description: "At 09:00",
      nextRuns: ["2026-05-17T09:00:00Z"],
    });
  });

  it("loads cron preview after debounce", async () => {
    renderForm();

    await waitFor(() => {
      expect(api.previewCron).toHaveBeenCalled();
    });
    expect(await screen.findByText(/at 09:00/i)).toBeInTheDocument();
  });

  it("does not submit when name is empty", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByRole("button", { name: /create schedule/i }));

    expect(api.createSchedule).not.toHaveBeenCalled();
  });

  it("surfaces preview errors", async () => {
    vi.mocked(api.previewCron).mockRejectedValue(new api.ApiError("Invalid cron", 400));
    renderForm();

    expect(await screen.findByText(/invalid cron/i)).toBeInTheDocument();
  });

  it("creates schedule and notifies on success", async () => {
    const user = userEvent.setup();
    vi.mocked(api.createSchedule).mockResolvedValue({
      id: "sched-1",
      pipelineId,
      name: "Morning run",
      cronExpression: "0 9 * * *",
      timezone: "UTC",
      active: true,
      nextRunAt: "2026-05-17T09:00:00Z",
      lastRunAt: null,
      createdAt: "2026-05-16T09:00:00Z",
    });
    const { onCreated } = renderForm();

    await user.type(screen.getByLabelText(/schedule name/i), "Morning run");
    await user.click(screen.getByRole("button", { name: /create schedule/i }));

    await waitFor(() => {
      expect(api.createSchedule).toHaveBeenCalledWith(
        expect.objectContaining({
          pipelineId,
          name: "Morning run",
          cronExpression: "0 9 * * *",
        }),
      );
    });
    expect(onCreated).toHaveBeenCalled();
  });

  it("reports create failures via toast and onError", async () => {
    const user = userEvent.setup();
    vi.mocked(api.createSchedule).mockRejectedValue(new api.ApiError("Forbidden", 403));
    const { onCreated, onError } = renderForm();

    await user.type(screen.getByLabelText(/schedule name/i), "Blocked");
    await user.click(screen.getByRole("button", { name: /create schedule/i }));

    await waitFor(() => {
      expect(onError).toHaveBeenCalledWith("Forbidden");
    });
    expect(onCreated).not.toHaveBeenCalled();
  });
});
