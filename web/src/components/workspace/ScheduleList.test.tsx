import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ScheduleList } from "@/components/workspace/ScheduleList";
import { ToastProvider } from "@/components/ui/Toast";
import * as api from "@/lib/api";

vi.mock("@/lib/api", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/lib/api")>();
  return {
    ...mod,
    pauseSchedule: vi.fn(),
    resumeSchedule: vi.fn(),
    deleteSchedule: vi.fn(),
  };
});

const sampleSchedule = {
  id: "sched-1",
  pipelineId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0001",
  name: "Daily ETL",
  cronExpression: "0 9 * * *",
  timezone: "UTC",
  active: true,
  nextRunAt: "2026-05-17T09:00:00Z",
  lastRunAt: null,
  createdAt: "2026-05-16T09:00:00Z",
};

function renderList(props?: Partial<Parameters<typeof ScheduleList>[0]>) {
  const onChanged = vi.fn();
  const onError = vi.fn();
  render(
    <ToastProvider>
      <ScheduleList
        schedules={[]}
        loading={false}
        onChanged={onChanged}
        onError={onError}
        {...props}
      />
    </ToastProvider>,
  );
  return { onChanged, onError };
}

describe("ScheduleList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows empty state when there are no schedules", () => {
    renderList();
    expect(screen.getByText(/no schedules yet/i)).toBeInTheDocument();
  });

  it("shows loading state", () => {
    renderList({ loading: true, schedules: [sampleSchedule] });
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("pauses an active schedule", async () => {
    const user = userEvent.setup();
    vi.mocked(api.pauseSchedule).mockResolvedValue({ ...sampleSchedule, active: false });
    const { onChanged } = renderList({ schedules: [sampleSchedule] });

    await user.click(screen.getByRole("button", { name: /pause schedule/i }));

    expect(api.pauseSchedule).toHaveBeenCalledWith("sched-1");
    expect(onChanged).toHaveBeenCalled();
  });

  it("resumes a paused schedule", async () => {
    const user = userEvent.setup();
    vi.mocked(api.resumeSchedule).mockResolvedValue({ ...sampleSchedule, active: true });
    const { onChanged } = renderList({
      schedules: [{ ...sampleSchedule, active: false }],
    });

    await user.click(screen.getByRole("button", { name: /resume schedule/i }));

    expect(api.resumeSchedule).toHaveBeenCalledWith("sched-1");
    expect(onChanged).toHaveBeenCalled();
  });

  it("confirms delete and calls API", async () => {
    const user = userEvent.setup();
    vi.mocked(api.deleteSchedule).mockResolvedValue(undefined);
    const { onChanged } = renderList({ schedules: [sampleSchedule] });

    await user.click(screen.getByRole("button", { name: /^delete schedule$/i }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: /^delete schedule$/i }));

    expect(api.deleteSchedule).toHaveBeenCalledWith("sched-1");
    expect(onChanged).toHaveBeenCalled();
  });
});
