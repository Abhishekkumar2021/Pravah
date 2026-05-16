import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@/lib/theme";
import { Checkbox } from "@/components/ui/Checkbox";
import { Switch } from "@/components/ui/Switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";
import { Input } from "@/components/ui/Input";
import { EmptyState } from "@/components/ui/EmptyState";
import { Separator } from "@/components/ui/Separator";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/Dialog";

function withTheme(ui: ReactNode) {
  return <ThemeProvider>{ui}</ThemeProvider>;
}

describe("ui primitives", () => {
  it("renders checkbox and switch", async () => {
    const user = userEvent.setup();
    const onChecked = vi.fn();
    render(
      withTheme(
        <>
          <Checkbox aria-label="agree" onCheckedChange={onChecked} />
          <Switch aria-label="toggle" />
        </>,
      ),
    );
    await user.click(screen.getByRole("checkbox", { name: "agree" }));
    expect(onChecked).toHaveBeenCalled();
    expect(screen.getByRole("switch", { name: "toggle" })).toBeVisible();
  });

  it("renders tabs with content", () => {
    render(
      withTheme(
        <Tabs defaultValue="a">
          <TabsList>
            <TabsTrigger value="a">Tab A</TabsTrigger>
            <TabsTrigger value="b">Tab B</TabsTrigger>
          </TabsList>
          <TabsContent value="a">Content A</TabsContent>
          <TabsContent value="b">Content B</TabsContent>
        </Tabs>,
      ),
    );
    expect(screen.getByText("Content A")).toBeVisible();
  });

  it("renders input, empty state, and separator", () => {
    render(
      withTheme(
        <>
          <Input aria-label="name" placeholder="Name" />
          <Separator />
          <EmptyState title="Nothing here" description="Add a workflow" />
        </>,
      ),
    );
    expect(screen.getByRole("textbox", { name: "name" })).toBeVisible();
    expect(screen.getByText("Nothing here")).toBeVisible();
  });

  it("opens dialog content", async () => {
    const user = userEvent.setup();
    render(
      withTheme(
        <Dialog>
          <DialogTrigger asChild>
            <button type="button">Open</button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Confirm</DialogTitle>
              <DialogDescription>Are you sure?</DialogDescription>
            </DialogHeader>
          </DialogContent>
        </Dialog>,
      ),
    );
    await user.click(screen.getByRole("button", { name: "Open" }));
    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(screen.getByText("Confirm")).toBeVisible();
  });
});
