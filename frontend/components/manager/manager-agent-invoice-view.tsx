import { Card } from "@/components/ui/card";
import { IconCheckCircle } from "@/components/icons";
import { AgentInvoiceView } from "@/components/agent/agent-invoice-view";
import { AgentInvoiceOverrideControl } from "@/components/manager/agent-invoice-override-control";
import { ApproveAgentInvoiceControl } from "@/components/manager/approve-agent-invoice-control";
import { MarkAgentInvoicePaidControl } from "@/components/manager/mark-agent-invoice-paid-control";
import type { AgentInvoiceDetail } from "@/lib/api/types";

/**
 * A Manager's review of this Agent's current-month Agent Invoice (spec.md user stories 9-12;
 * agent-invoice-submission-and-approval ticket AC). Surfaces on the Agent detail page — the
 * natural place to review that Agent's invoice — the same "detail-view review scoped to one
 * owning record" shape {@code ManagerContractClientInvoiceView} established for Client Invoice on
 * the Contract detail page.
 *
 * <p>A Manager can already view a {@code draft} invoice too (spec.md Access control: oversight
 * parity, unchanged from the previous ticket's {@code AgentInvoiceView}); no override/approve
 * action is offered for one, since a draft's numbers aren't frozen yet — nothing to override
 * (ticket AC: override applies "at approval time", i.e. once {@code sent}). Once {@code approved}
 * or {@code paid}, the review section drops away too — the invoice is either awaiting the Manager
 * to mark it paid, or the whole lifecycle is complete.
 */
export function ManagerAgentInvoiceView({
  agentId,
  invoice,
}: {
  agentId: string;
  invoice: AgentInvoiceDetail | null;
}) {
  if (!invoice) {
    return null;
  }

  return (
    <div className="flex flex-col gap-4">
      <AgentInvoiceView invoice={invoice} />

      {invoice.status === "SENT" ? (
        <Card className="flex flex-col gap-4 p-5">
          <div className="flex items-center gap-2 border-b border-hairline pb-3.5">
            <IconCheckCircle className="h-4 w-4 text-ink-mute" />
            <h2 className="text-sm font-semibold text-ink">Review this invoice</h2>
          </div>
          <AgentInvoiceOverrideControl
            agentId={agentId}
            salary={invoice.salary}
            rolloutAdvanceNewAdvance={invoice.rolloutAdvanceNewAdvance}
            currency={invoice.currency}
          />
          <div className="flex justify-end">
            <ApproveAgentInvoiceControl agentId={agentId} />
          </div>
        </Card>
      ) : null}

      {invoice.status === "APPROVED" ? (
        <div className="flex justify-end">
          <MarkAgentInvoicePaidControl agentId={agentId} />
        </div>
      ) : null}
    </div>
  );
}
