import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { stubFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import type { RequestListItem, SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { RequestStatusControl } from "./request-status-control";
import { carrier } from "./completion/test-fixtures";

/**
 * Code review finding: `confirmComplete`'s completion PATCH body used to be built by an
 * `if (type === "PROVISION_SMARTPHONE") ... else if ...` cascade right here in the shell, even
 * though rendering already dispatched through `REQUEST_COMPLETION_COMPONENTS`. That building moved
 * into each per-type completion piece (`components/agent/completion/*-completion.tsx`'s own
 * `build*CompletionBody`, registered alongside it in `REQUEST_COMPLETION_BODY_BUILDERS`). These
 * tests exercise the shell end to end — fill the rendered form, submit, assert the exact PATCH
 * body — to prove that move changed no behaviour, for every type that ever touched the cascade.
 */
function baseRequest(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "PROVISION_SMARTPHONE",
    status: "IN_PROGRESS",
    raisedByTesterId: "tester-1",
    raisedByUsername: "tester@example.com",
    agentAuthored: false,
    loggedByUsername: "tester@example.com",
    cancellationReason: null,
    description: null,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function smartphone(overrides: Partial<SmartphoneListItem> = {}): SmartphoneListItem {
  return { id: "phone-1", contractId: "contract-1", model: "Pixel 8", serial: "SN-1", owner: "COMPANY", status: "ACTIVE", ...overrides };
}

function simCard(overrides: Partial<SimCardListItem> = {}): SimCardListItem {
  return {
    id: "sim-1",
    contractId: "contract-1",
    number: "+1-555-0100",
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status: "ACTIVE",
    ...overrides,
  };
}

/** Enters the completion form (a Request whose type can carry a Fee always shows one first). */
async function openCompletionForm() {
  await userEvent.click(screen.getByRole("button", { name: "Mark Completed" }));
}

async function fillFeeAmount(amount = "9.99") {
  const input = screen.getByLabelText(/Fee amount/);
  await userEvent.clear(input);
  await userEvent.type(input, amount);
}

async function submit() {
  await userEvent.click(screen.getByRole("button", { name: "Mark Completed" }));
}

describe("RequestStatusControl completion payload", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("Provision Smartphone, new-style (requestedModel already set): sends no Fleet fields", async () => {
    const fetchMock = stubFetch(200, {});
    render(<RequestStatusControl request={baseRequest({ requestedModel: "iPhone 15" })} currency="USD" />);

    await openCompletionForm();
    await fillFeeAmount();
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({ body: JSON.stringify({ status: "COMPLETED" }) }),
    );
  });

  it("Provision Smartphone, legacy (no requestedModel): sends the full new-Smartphone form", async () => {
    const fetchMock = stubFetch(200, {});
    render(
      <RequestStatusControl
        request={baseRequest()}
        currency="USD"
        activeSmartphones={[smartphone({ id: "old-phone", model: "Pixel 7", serial: "SN-OLD" })]}
      />,
    );

    await openCompletionForm();
    await fillFeeAmount();
    await userEvent.type(screen.getByLabelText("New smartphone model"), "Pixel 9");
    await userEvent.type(screen.getByLabelText("New smartphone serial (optional)"), "SN-NEW");
    await userEvent.selectOptions(screen.getByLabelText(/Retiring which smartphone/), "old-phone");
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({
        body: JSON.stringify({
          status: "COMPLETED",
          newSmartphone: { model: "Pixel 9", serial: "SN-NEW" },
          replacesSmartphoneId: "old-phone",
        }),
      }),
    );
  });

  it("Provision SIM, new-style (requestedFlavor already set): sends only the SIM number", async () => {
    const fetchMock = stubFetch(200, {});
    render(
      <RequestStatusControl
        request={baseRequest({ type: "PROVISION_SIM", requestedFlavor: "PREPAID", requestedCarrierName: "Carrier A" })}
        currency="USD"
      />,
    );

    await openCompletionForm();
    await fillFeeAmount();
    await userEvent.type(screen.getByLabelText("New SIM number"), "+1-555-0200");
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({
        body: JSON.stringify({ status: "COMPLETED", simCardNumber: "+1-555-0200" }),
      }),
    );
  });

  it("Provision SIM, legacy (no requestedFlavor): sends the full new-SIM form", async () => {
    const fetchMock = stubFetch(200, {});
    const carrierA = carrier("carrier-a", [{ id: "plan-1", archivedAt: null }]);
    render(
      <RequestStatusControl
        request={baseRequest({ type: "PROVISION_SIM" })}
        currency="USD"
        carriers={[carrierA]}
        activeSimCards={[simCard({ id: "old-sim", number: "+1-555-0100" })]}
      />,
    );

    await openCompletionForm();
    await fillFeeAmount();
    await userEvent.type(screen.getByLabelText("New SIM number"), "+1-555-0300");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Carrier" }), "carrier-a");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Flavor" }), "POSTPAID");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Postpaid plan" }), "plan-1");
    await userEvent.selectOptions(screen.getByLabelText(/Retiring which SIM/), "old-sim");
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({
        body: JSON.stringify({
          status: "COMPLETED",
          newSimCard: { number: "+1-555-0300", carrierId: "carrier-a", flavor: "POSTPAID", postpaidPlanId: "plan-1" },
          replacesSimCardId: "old-sim",
        }),
      }),
    );
  });

  it("Replace SIM: always sends the full new-SIM form (no requested fields to fall back on)", async () => {
    const fetchMock = stubFetch(200, {});
    const carrierA = carrier("carrier-a");
    render(
      <RequestStatusControl
        request={baseRequest({ type: "REPLACE_SIM", targetSimCardId: "old-sim", targetSimCardNumber: "+1-555-0100" })}
        currency="USD"
        carriers={[carrierA]}
        activeSimCards={[simCard({ id: "old-sim", number: "+1-555-0100" })]}
      />,
    );

    await openCompletionForm();
    await fillFeeAmount();
    await userEvent.type(screen.getByLabelText("New SIM number"), "+1-555-0400");
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Carrier" }), "carrier-a");
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({
        body: JSON.stringify({
          status: "COMPLETED",
          newSimCard: { number: "+1-555-0400", carrierId: "carrier-a", flavor: "PREPAID", postpaidPlanId: undefined },
        }),
      }),
    );
  });

  it("Replace Smartphone: no Agent input, sends the bare status body", async () => {
    const fetchMock = stubFetch(200, {});
    render(
      <RequestStatusControl
        request={baseRequest({
          type: "REPLACE_SMARTPHONE",
          targetSmartphoneId: "old-phone",
          targetSmartphoneModel: "Pixel 7",
        })}
        currency="USD"
      />,
    );

    await openCompletionForm();
    await fillFeeAmount();
    await submit();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({ body: JSON.stringify({ status: "COMPLETED" }) }),
    );
  });

  it("Reboot: no completion form at all, a single click sends the bare status body", async () => {
    const fetchMock = stubFetch(200, {});
    render(<RequestStatusControl request={baseRequest({ type: "REBOOT" })} currency="USD" />);

    await userEvent.click(screen.getByRole("button", { name: "Mark Completed" }));

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/contracts/contract-1/requests/request-1/status",
      expect.objectContaining({ body: JSON.stringify({ status: "COMPLETED" }) }),
    );
  });
});
