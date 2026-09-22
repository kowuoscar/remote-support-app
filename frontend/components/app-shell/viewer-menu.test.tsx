import { render, screen, fireEvent, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { ViewerMenu } from "./viewer-menu";

/**
 * Behaviour, not markup (spec.md Testing decisions): every assertion here goes through the
 * accessibility tree — roles, `aria-expanded`, focus — never a class name. This is the design
 * system's first component with real focus management and has no prior art in the repository
 * (`ContractSwitcher` has none of this to copy), so it carries the most test weight per line.
 */
describe("ViewerMenu", () => {
  beforeEach(() => {
    mockRouter.push.mockReset();
    mockRouter.refresh.mockReset();
  });

  it("is closed by default, with aria-expanded false", () => {
    render(<ViewerMenu viewerLabel="Jordan Ellis · Agent" />);

    const trigger = screen.getByRole("button", { name: "Jordan Ellis · Agent" });
    expect(trigger).toHaveAttribute("aria-haspopup", "menu");
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("opens the menu with exactly two menuitems and flips aria-expanded to true", async () => {
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);

    await user.click(screen.getByRole("button", { name: "Manager" }));

    expect(screen.getByRole("button", { name: "Manager" })).toHaveAttribute("aria-expanded", "true");
    const menu = screen.getByRole("menu");
    const items = within(menu).getAllByRole("menuitem");
    expect(items.map((item) => item.textContent)).toEqual(["Change password", "Log out"]);
  });

  it("moves focus onto a menu item when it opens", async () => {
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);

    await user.click(screen.getByRole("button", { name: "Manager" }));

    expect(screen.getByRole("menuitem", { name: "Change password" })).toHaveFocus();
  });

  it("closes on Escape and returns focus to the trigger", async () => {
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);
    const trigger = screen.getByRole("button", { name: "Manager" });

    await user.click(trigger);
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).toHaveFocus();
  });

  it("closes on an outside pointer press and returns focus to the trigger", async () => {
    const user = userEvent.setup();
    render(
      <div>
        <ViewerMenu viewerLabel="Manager" />
        <button type="button">Elsewhere</button>
      </div>,
    );
    const trigger = screen.getByRole("button", { name: "Manager" });

    await user.click(trigger);
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Elsewhere" }));

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("moves focus between items with arrow keys, wrapping at both ends", async () => {
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);

    await user.click(screen.getByRole("button", { name: "Manager" }));
    const changePassword = screen.getByRole("menuitem", { name: "Change password" });
    const logOut = screen.getByRole("menuitem", { name: "Log out" });
    expect(changePassword).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(logOut).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(changePassword).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(logOut).toHaveFocus();
  });

  it("activating Change password closes the menu with no navigation and no error", async () => {
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);
    const trigger = screen.getByRole("button", { name: "Manager" });

    await user.click(trigger);
    await user.click(screen.getByRole("menuitem", { name: "Change password" }));

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("activating Log out triggers the existing sign-out flow", async () => {
    const fetchMock = stubFetch(200);
    const user = userEvent.setup();
    render(<ViewerMenu viewerLabel="Manager" />);

    await user.click(screen.getByRole("button", { name: "Manager" }));
    await user.click(screen.getByRole("menuitem", { name: "Log out" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/session", { method: "DELETE" });
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    await vi.waitFor(() => expect(mockRouter.push).toHaveBeenCalledWith("/login"));
    expect(mockRouter.refresh).toHaveBeenCalled();
  });

  it("with the menu closed, an outside press or Escape has no effect on the page", async () => {
    render(
      <div>
        <ViewerMenu viewerLabel="Manager" />
        <button type="button">Elsewhere</button>
      </div>,
    );
    const trigger = screen.getByRole("button", { name: "Manager" });

    fireEvent.pointerDown(screen.getByRole("button", { name: "Elsewhere" }));
    fireEvent.keyDown(document.body, { key: "Escape" });

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(trigger).not.toHaveFocus();
  });
});
