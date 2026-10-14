import { useRef } from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockRouter } from "@/tests/component/next-navigation";
import { ChangeNameDialog, type ChangeNameDialogHandle } from "./change-name-dialog";

/** Stands in for `ViewerActions`, which opens the dialog imperatively from the menu item. */
function Harness() {
  const ref = useRef<ChangeNameDialogHandle>(null);
  return (
    <>
      <button type="button" onClick={() => ref.current?.open()}>
        Open
      </button>
      <ChangeNameDialog ref={ref} />
    </>
  );
}

function json(status: number, body?: unknown) {
  return body === undefined
    ? new Response(null, { status })
    : new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** GET /api/me answers `read`, PUT /api/me/name answers `write`. */
function stubBackend(read: Response | Error, write: Response = json(204)) {
  const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
    if (init?.method === "PUT") return write.clone();
    if (read instanceof Error) throw read;
    return read.clone();
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const puts = (fetchMock: ReturnType<typeof stubBackend>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === "PUT");

async function openDialog() {
  render(<Harness />);
  await userEvent.click(screen.getByRole("button", { name: "Open" }));
  return screen.getByRole("dialog");
}

describe("ChangeNameDialog", () => {
  beforeEach(() => {
    mockRouter.push.mockReset();
    mockRouter.refresh.mockReset();
  });

  it("opens with the field holding the current name, read through /api/me", async () => {
    const fetchMock = stubBackend(json(200, { username: "m@example.com", name: "Yazan Abuhassan" }));
    const dialog = await openDialog();

    await waitFor(() => expect(within(dialog).getByLabelText("Name")).toHaveValue("Yazan Abuhassan"));
    expect(fetchMock.mock.calls[0][0]).toBe("/api/me");
    expect(within(dialog).getByLabelText("Name")).toHaveAttribute("autocomplete", "name");
  });

  it("opens empty when no name is set", async () => {
    const fetchMock = stubBackend(json(200, { username: "m@example.com" }));
    const dialog = await openDialog();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(within(dialog).getByLabelText("Name")).toHaveValue("");
  });

  it("opens empty when the read fails", async () => {
    const fetchMock = stubBackend(json(500));
    const dialog = await openDialog();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(within(dialog).getByLabelText("Name")).toHaveValue("");
  });

  it("opens empty when the read cannot be made at all", async () => {
    const fetchMock = stubBackend(new Error("offline"));
    const dialog = await openDialog();

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(within(dialog).getByLabelText("Name")).toHaveValue("");
  });

  it("refuses a blank name in the browser and sends no request", async () => {
    const fetchMock = stubBackend(json(200, { username: "m@example.com" }));
    const dialog = await openDialog();

    await userEvent.type(within(dialog).getByLabelText("Name"), "   ");
    await userEvent.click(within(dialog).getByRole("button", { name: "Change name" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Enter your name.");
    expect(within(dialog).getByLabelText("Name")).toHaveAttribute("aria-invalid", "true");
    expect(puts(fetchMock)).toHaveLength(0);
  });

  it("maps NAME_TOO_LONG to its message and keeps the typed text", async () => {
    stubBackend(json(200, { username: "m@example.com" }), json(400, { code: "NAME_TOO_LONG", message: "x" }));
    const dialog = await openDialog();
    const tooLong = "a".repeat(101);

    await userEvent.click(within(dialog).getByLabelText("Name"));
    await userEvent.paste(tooLong);
    await userEvent.click(within(dialog).getByRole("button", { name: "Change name" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Your name can be at most 100 characters.");
    expect(within(dialog).getByLabelText("Name")).toHaveValue(tooLong);
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("maps NAME_BLANK from the backend to the blank message", async () => {
    stubBackend(json(200, { username: "m@example.com" }), json(400, { code: "NAME_BLANK", message: "x" }));
    const dialog = await openDialog();

    await userEvent.type(within(dialog).getByLabelText("Name"), "Yazan");
    await userEvent.click(within(dialog).getByRole("button", { name: "Change name" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Enter your name.");
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Yazan");
  });

  it("shows the generic message for any other failure and keeps the text", async () => {
    stubBackend(json(200, { username: "m@example.com" }), json(500));
    const dialog = await openDialog();

    await userEvent.type(within(dialog).getByLabelText("Name"), "Yazan");
    await userEvent.click(within(dialog).getByRole("button", { name: "Change name" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't change your name. Try again.");
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Yazan");
  });

  it("on 204 sends the name as a PUT, refreshes, and never signs out", async () => {
    const fetchMock = stubBackend(json(200, { username: "m@example.com", name: "Old Name" }));
    const dialog = await openDialog();
    const field = within(dialog).getByLabelText("Name");
    await waitFor(() => expect(field).toHaveValue("Old Name"));

    await userEvent.clear(field);
    await userEvent.type(field, "Yazan Abuhassan");
    await userEvent.click(within(dialog).getByRole("button", { name: "Change name" }));

    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalled());
    const [[url, init]] = puts(fetchMock);
    expect(url).toBe("/api/me/name");
    expect(JSON.parse(String(init?.body))).toEqual({ name: "Yazan Abuhassan" });
    expect(mockRouter.push).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls.some(([, i]) => i?.method === "DELETE")).toBe(false);
  });
});
