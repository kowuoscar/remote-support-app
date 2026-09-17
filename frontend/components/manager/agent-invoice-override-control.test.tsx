import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { stubFetch, stubPendingFetch } from "@/tests/component/fetch";
import { mockRouter } from "@/tests/component/next-navigation";
import { AgentInvoiceOverrideControl } from "./agent-invoice-override-control";

function renderControl() {
  render(<AgentInvoiceOverrideControl agentId="agent-1" salary={1000} rolloutAdvanceNewAdvance={0} currency="EUR" />);
  const salaryInput = screen.getByRole("spinbutton", { name: /Override Salary \(EUR\)/ });
  const salaryForm = salaryInput.closest("form")!;
  return {
    salaryInput,
    salaryButton: within(salaryForm).getByRole("button", { name: "Override" }),
    salaryForm,
  };
}

describe("AgentInvoiceOverrideControl", () => {
  beforeEach(() => {
    mockRouter.refresh.mockReset();
  });

  it("overrides this invoice's Salary, shows it pending, then confirms and refreshes", async () => {
    const { fetchMock, respond } = stubPendingFetch();
    const { salaryInput, salaryButton, salaryForm } = renderControl();

    await userEvent.clear(salaryInput);
    await userEvent.type(salaryInput, "1250.5");
    await userEvent.click(salaryButton);

    expect(fetchMock).toHaveBeenCalledWith("/api/agents/agent-1/invoice/override", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ salary: 1250.5 }),
    });
    expect(salaryButton).toBeDisabled();
    expect(salaryInput).toBeDisabled();

    respond(200);

    expect(await within(salaryForm).findByText("Applied to this invoice only.")).toBeInTheDocument();
    expect(salaryButton).toBeEnabled();
    await waitFor(() => expect(mockRouter.refresh).toHaveBeenCalledOnce());
  });

  it("overrides the Rollout Advance new-advance line independently of Salary", async () => {
    const fetchMock = stubFetch(200);
    render(<AgentInvoiceOverrideControl agentId="agent-1" salary={1000} rolloutAdvanceNewAdvance={0} currency="EUR" />);
    const input = screen.getByRole("spinbutton", { name: /Override Rollout Advance \(new\) \(EUR\)/ });

    await userEvent.clear(input);
    await userEvent.type(input, "300");
    await userEvent.click(within(input.closest("form")!).getByRole("button", { name: "Override" }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/agents/agent-1/invoice/override",
        expect.objectContaining({ body: JSON.stringify({ rolloutAdvanceNewAdvance: 300 }) }),
      ),
    );
  });

  it("shows an inline error and lets the Manager retry on a 500 response", async () => {
    stubFetch(500);
    const { salaryInput, salaryButton, salaryForm } = renderControl();

    await userEvent.click(salaryButton);

    expect(await within(salaryForm).findByRole("alert")).toHaveTextContent("Couldn't save. Try again.");
    expect(salaryInput).toBeInvalid();
    expect(salaryButton).toBeEnabled();
    expect(within(salaryForm).queryByText("Applied to this invoice only.")).not.toBeInTheDocument();
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("tells the Manager to refresh when the invoice is no longer awaiting approval (409)", async () => {
    stubFetch(409);
    const { salaryButton, salaryForm } = renderControl();

    await userEvent.click(salaryButton);

    expect(await within(salaryForm).findByRole("alert")).toHaveTextContent(
      "This invoice is no longer awaiting approval, so its lines can't change. Refresh to see its current status.",
    );
    expect(mockRouter.refresh).not.toHaveBeenCalled();
  });

  it("overrides an Agent Invoice by its own id and hands the updated invoice back", async () => {
    const updated = { id: "invoice-1", status: "SENT", salary: 1400 };
    const fetchMock = stubFetch(200, updated);
    const onOverridden = vi.fn();
    render(
      <AgentInvoiceOverrideControl
        invoiceId="invoice-1"
        salary={1000}
        rolloutAdvanceNewAdvance={0}
        currency="EUR"
        onOverridden={onOverridden}
      />,
    );
    const input = screen.getByRole("spinbutton", { name: /Override Salary \(EUR\)/ });

    await userEvent.clear(input);
    await userEvent.type(input, "1400");
    await userEvent.click(within(input.closest("form")!).getByRole("button", { name: "Override" }));

    expect(fetchMock).toHaveBeenCalledWith("/api/agent-invoices/invoice-1/override", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ salary: 1400 }),
    });
    await waitFor(() => expect(onOverridden).toHaveBeenCalledWith(updated));
  });
});
