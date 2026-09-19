import { waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect } from "vitest";
import type { Mock } from "vitest";

/**
 * Clicks the named submit button inside `dialog`, waits for the mocked `fetch` call it fires, and
 * returns the parsed JSON body that was sent — shared by every dialog component test that fills a
 * form and asserts on the resulting request body (LogRequestDialog, LogFeeDialog,
 * SubmitRequestDialog), extracted from the identical click/waitFor/parse steps each repeated.
 */
export async function submitAndGetRequestBody(
  dialog: HTMLElement,
  fetchMock: Mock,
  buttonName: string,
): Promise<Record<string, unknown>> {
  await userEvent.click(within(dialog).getByRole("button", { name: buttonName }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
  return JSON.parse(String(init.body));
}
