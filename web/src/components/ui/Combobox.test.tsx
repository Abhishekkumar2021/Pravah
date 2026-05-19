import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Combobox, type ComboboxOption } from "./Combobox";

const OPTIONS: ComboboxOption[] = [
  { value: "apple", label: "Apple", description: "A red fruit" },
  { value: "banana", label: "Banana", description: "A yellow fruit" },
  { value: "cherry", label: "Cherry" },
];

describe("Combobox", () => {
  it("renders with placeholder when no value selected", () => {
    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={() => {}}
        placeholder="Select fruit..."
      />
    );

    expect(screen.getByRole("combobox")).toHaveTextContent("Select fruit...");
  });

  it("shows selected option label", () => {
    render(
      <Combobox
        options={OPTIONS}
        value="banana"
        onValueChange={() => {}}
      />
    );

    expect(screen.getByRole("combobox")).toHaveTextContent("Banana");
  });

  it("opens dropdown and shows all options", async () => {
    const user = userEvent.setup();
    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={() => {}}
      />
    );

    await user.click(screen.getByRole("combobox"));

    expect(screen.getByPlaceholderText("Search...")).toBeInTheDocument();
    expect(screen.getByText("Apple")).toBeInTheDocument();
    expect(screen.getByText("Banana")).toBeInTheDocument();
    expect(screen.getByText("Cherry")).toBeInTheDocument();
  });

  it("filters options based on search", async () => {
    const user = userEvent.setup();
    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={() => {}}
      />
    );

    await user.click(screen.getByRole("combobox"));
    await user.type(screen.getByPlaceholderText("Search..."), "ban");

    expect(screen.getByText("Banana")).toBeInTheDocument();
    expect(screen.queryByText("Apple")).not.toBeInTheDocument();
    expect(screen.queryByText("Cherry")).not.toBeInTheDocument();
  });

  it("calls onValueChange when option selected", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={onChange}
      />
    );

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByText("Cherry"));

    expect(onChange).toHaveBeenCalledWith("cherry");
  });

  it("shows empty state when no options match search", async () => {
    const user = userEvent.setup();
    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={() => {}}
        emptyText="Nothing found"
      />
    );

    await user.click(screen.getByRole("combobox"));
    await user.type(screen.getByPlaceholderText("Search..."), "xyz");

    expect(screen.getByText("Nothing found")).toBeInTheDocument();
  });

  it("shows check icon for selected option", async () => {
    const user = userEvent.setup();
    render(
      <Combobox
        options={OPTIONS}
        value="apple"
        onValueChange={() => {}}
      />
    );

    await user.click(screen.getByRole("combobox"));

    const appleButtons = screen.getAllByRole("button").filter((btn) =>
      btn.textContent?.includes("Apple")
    );
    const appleOption = appleButtons.find((btn) => btn.className.includes("bg-blue"));
    expect(appleOption).toBeDefined();
  });

  it("searches by description", async () => {
    const user = userEvent.setup();
    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={() => {}}
      />
    );

    await user.click(screen.getByRole("combobox"));
    await user.type(screen.getByPlaceholderText("Search..."), "yellow");

    expect(screen.getByText("Banana")).toBeInTheDocument();
    expect(screen.queryByText("Apple")).not.toBeInTheDocument();
  });

  it("selects single option on Enter key", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(
      <Combobox
        options={OPTIONS}
        value=""
        onValueChange={onChange}
      />
    );

    await user.click(screen.getByRole("combobox"));
    await user.type(screen.getByPlaceholderText("Search..."), "cher");
    await user.keyboard("{Enter}");

    expect(onChange).toHaveBeenCalledWith("cherry");
  });
});
