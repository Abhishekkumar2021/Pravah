import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "./Pagination";

describe("Pagination", () => {
  it("renders current page and total", () => {
    render(<Pagination page={2} totalPages={10} totalElements={100} onPageChange={() => {}} />);
    expect(screen.getByText("Page 3")).toBeInTheDocument();
    expect(screen.getByText(/of 10/i)).toBeInTheDocument();
  });

  it("disables previous button on first page", () => {
    render(<Pagination page={0} totalPages={10} totalElements={100} onPageChange={() => {}} />);
    const prevButton = screen.getByRole("button", { name: /previous/i });
    expect(prevButton).toBeDisabled();
  });

  it("disables next button on last page", () => {
    render(<Pagination page={9} totalPages={10} totalElements={100} onPageChange={() => {}} />);
    const nextButton = screen.getByRole("button", { name: /next/i });
    expect(nextButton).toBeDisabled();
  });

  it("calls onPageChange when clicking next", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination page={4} totalPages={10} totalElements={100} onPageChange={onChange} />);
    await user.click(screen.getByRole("button", { name: /next/i }));

    expect(onChange).toHaveBeenCalledWith(5);
  });

  it("calls onPageChange when clicking previous", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination page={4} totalPages={10} totalElements={100} onPageChange={onChange} />);
    await user.click(screen.getByRole("button", { name: /previous/i }));

    expect(onChange).toHaveBeenCalledWith(3);
  });

  it("shows first/last buttons when totalPages > 2", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination page={4} totalPages={10} totalElements={100} onPageChange={onChange} />);

    const firstButton = screen.getByRole("button", { name: /first/i });
    const lastButton = screen.getByRole("button", { name: /last/i });

    expect(firstButton).toBeInTheDocument();
    expect(lastButton).toBeInTheDocument();

    await user.click(firstButton);
    expect(onChange).toHaveBeenCalledWith(0);

    await user.click(lastButton);
    expect(onChange).toHaveBeenCalledWith(9);
  });

  it("hides first/last buttons when totalPages <= 2", () => {
    render(<Pagination page={0} totalPages={2} totalElements={20} onPageChange={() => {}} />);

    expect(screen.queryByRole("button", { name: /first/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /last/i })).not.toBeInTheDocument();
  });

  it("shows jump button when totalPages > 5", async () => {
    const user = userEvent.setup();
    render(<Pagination page={0} totalPages={10} totalElements={100} onPageChange={() => {}} />);
    
    const jumpButton = screen.getByRole("button", { name: /go to page/i });
    expect(jumpButton).toBeInTheDocument();
    
    await user.click(jumpButton);
    expect(screen.getByRole("spinbutton")).toBeInTheDocument();
  });

  it("hides jump button when totalPages <= 5", () => {
    render(<Pagination page={0} totalPages={5} totalElements={50} onPageChange={() => {}} />);
    expect(screen.queryByRole("button", { name: /go to page/i })).not.toBeInTheDocument();
  });

  it("jumps to page when entering number and pressing Enter", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<Pagination page={0} totalPages={20} totalElements={200} onPageChange={onChange} />);

    await user.click(screen.getByRole("button", { name: /go to page/i }));
    await user.type(screen.getByRole("spinbutton"), "15");
    await user.keyboard("{Enter}");

    expect(onChange).toHaveBeenCalledWith(14);
  });

  it("returns null when totalPages <= 1", () => {
    const { container } = render(<Pagination page={0} totalPages={1} totalElements={10} onPageChange={() => {}} />);
    expect(container.firstChild).toBeNull();
  });
});
