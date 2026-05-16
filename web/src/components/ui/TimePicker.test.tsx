import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { TimePicker } from "./TimePicker";

function ControlledTimePicker({ initial = "09:00" }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return <TimePicker value={value} onChange={setValue} />;
}

describe("TimePicker", () => {
  it("shows hour, minute, and period controls", () => {
    render(<TimePicker value="09:30" onChange={vi.fn()} />);
    expect(screen.getByRole("combobox", { name: "Hour" })).toHaveTextContent("9");
    expect(screen.getByRole("combobox", { name: "Minute" })).toHaveTextContent("30");
    expect(screen.getByRole("radio", { name: "AM" })).toHaveAttribute("aria-checked", "true");
  });

  it("changes hour and minute via selects", async () => {
    const user = userEvent.setup();
    render(<ControlledTimePicker />);

    await user.click(screen.getByRole("combobox", { name: "Hour" }));
    await user.click(screen.getByRole("option", { name: "6" }));
    expect(screen.getByRole("combobox", { name: "Hour" })).toHaveTextContent("6");

    await user.click(screen.getByRole("combobox", { name: "Minute" }));
    await user.click(screen.getByRole("option", { name: "15" }));
    expect(screen.getByRole("combobox", { name: "Minute" })).toHaveTextContent("15");
  });

  it("switches to PM via toggle", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<TimePicker value="09:00" onChange={onChange} />);

    await user.click(screen.getByRole("radio", { name: "PM" }));
    expect(onChange).toHaveBeenCalledWith("21:00");
  });
});
