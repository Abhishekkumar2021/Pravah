import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Select } from "./Select";

const options = [
  { value: "all", label: "All statuses" },
  { value: "running", label: "Running" },
];

describe("Select", () => {
  it("shows the selected label on the trigger", () => {
    render(
      <Select
        aria-label="Status"
        value="running"
        onValueChange={vi.fn()}
        options={options}
      />,
    );
    expect(screen.getByRole("combobox", { name: "Status" })).toHaveTextContent("Running");
  });

  it("opens a themed listbox and selects an option", async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    render(
      <Select
        aria-label="Status"
        value="all"
        onValueChange={onValueChange}
        options={options}
      />,
    );

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(screen.getByRole("option", { name: "Running" }));

    expect(onValueChange).toHaveBeenCalledWith("running");
  });

  it("shows placeholder when value is empty and skips empty-string options", async () => {
    const user = userEvent.setup();
    render(
      <Select
        aria-label="Connection"
        value=""
        onValueChange={vi.fn()}
        placeholder="Select a connection"
        options={[
          { value: "", label: "Invalid placeholder option" },
          { value: "conn-1", label: "Warehouse" },
        ]}
      />,
    );

    expect(screen.getByRole("combobox", { name: "Connection" })).toHaveTextContent(
      "Select a connection",
    );

    await user.click(screen.getByRole("combobox", { name: "Connection" }));
    expect(screen.getByRole("option", { name: "Warehouse" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Invalid placeholder option" })).not.toBeInTheDocument();
  });
});
