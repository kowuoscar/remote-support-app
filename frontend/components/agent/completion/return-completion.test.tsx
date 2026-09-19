import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { RequestListItem } from "@/lib/api/types";
import { buildReturnCompletionBody, returnCompletionNeedsOwnForm, ReturnCompletion } from "./return-completion";

function request(overrides: Partial<RequestListItem> = {}): RequestListItem {
  return {
    id: "request-1",
    contractId: "contract-1",
    type: "RETURN",
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

describe("returnCompletionNeedsOwnForm", () => {
  it("is false when the Return holds no Cancelled SIM Card", () => {
    expect(
      returnCompletionNeedsOwnForm(
        request({
          returnedUnits: [{ id: "unit-1", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_COMPANY" }],
        }),
      ),
    ).toBe(false);
  });

  it("is true when a SIM Card was chosen Cancelled", () => {
    expect(
      returnCompletionNeedsOwnForm(
        request({
          returnedUnits: [{ id: "unit-1", simCardId: "sim-1", simCardNumber: "+1-555-0100", disposition: "CANCELLED" }],
        }),
      ),
    ).toBe(true);
  });
});

describe("ReturnCompletion", () => {
  it("renders a required date field per cancelled SIM Card, and nothing for a posted Smartphone", () => {
    render(
      <ReturnCompletion
        request={request({
          returnedUnits: [
            { id: "unit-phone", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_COMPANY" },
            { id: "unit-sim", simCardId: "sim-1", simCardNumber: "+1-555-0100", disposition: "CANCELLED" },
          ],
        })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
      />,
    );

    const dateField = screen.getByLabelText(/\+1-555-0100/);
    expect(dateField).toHaveAttribute("type", "date");
    expect(dateField).toBeRequired();
    expect(screen.queryByText("Pixel 8")).not.toBeInTheDocument();
  });

  it("renders nothing when the Return cancels no SIM Card", () => {
    const { container } = render(
      <ReturnCompletion
        request={request({
          returnedUnits: [{ id: "unit-phone", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_COMPANY" }],
        })}
        carriers={[]}
        carriersHref=""
        currency="USD"
        activeSmartphones={[]}
        activeSimCards={[]}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});

describe("buildReturnCompletionBody", () => {
  it("builds one simCardCancellations entry per cancelled SIM Card, keyed by its own id", () => {
    const req = request({
      returnedUnits: [{ id: "unit-sim", simCardId: "sim-1", simCardNumber: "+1-555-0100", disposition: "CANCELLED" }],
    });
    const formData = new FormData();
    formData.set("cancellationDate-sim-1", "2026-08-15");

    expect(buildReturnCompletionBody(req, formData)).toEqual({
      simCardCancellations: [{ simCardId: "sim-1", effectiveDate: "2026-08-15" }],
    });
  });

  it("sends nothing when the Return cancels no SIM Card", () => {
    const req = request({
      returnedUnits: [{ id: "unit-phone", smartphoneId: "phone-1", smartphoneModel: "Pixel 8", disposition: "POSTED_TO_COMPANY" }],
    });
    expect(buildReturnCompletionBody(req, new FormData())).toEqual({});
  });
});
