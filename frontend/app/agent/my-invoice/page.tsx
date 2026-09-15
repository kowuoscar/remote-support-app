import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Badge } from "@/components/ui/badge";
import { Money } from "@/components/ui/money";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconMyInvoice } from "@/components/icons";
import { SurfacePage } from "@/components/app-shell/surface-page";
import { agentInvoiceStatusLabel, agentInvoiceStatusTone } from "@/lib/status";
import { currentAgent, currentMonthLabel, myAgentInvoices } from "@/lib/demo/agent";

export const metadata = { title: "My Invoice" };

export default function AgentMyInvoicePage() {
  return (
    <SurfacePage
      title="My Invoice"
      subtitle="Local Support Fees, salary and Rollout Advance"
      viewerLabel={`${currentAgent.name} · Agent`}
    >
      <Card className="p-0">
        <div className="border-b border-hairline px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">{currentMonthLabel}</h2>
          <p className="text-[13px] text-ink-mute">This month's invoice</p>
        </div>
        <div className="p-5">
          <EmptyState
            icon={<IconMyInvoice className="h-5 w-5" />}
            title="You haven't started this month's invoice yet"
            description="Local Support Fees accrue automatically as you log Fees against your Contracts through the month. Start the invoice when you're ready to review your salary and Rollout Advance lines before submitting."
            action={<Button variant="primary">Start {currentMonthLabel}'s invoice</Button>}
          />
        </div>
      </Card>

      <Card className="p-0">
        <div className="border-b border-hairline px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Invoice history</h2>
          <p className="text-[13px] text-ink-mute">Status through to paid</p>
        </div>
        <TableScroll className="rounded-none border-0">
          <Table>
            <Thead>
              <Tr>
                <Th>Month</Th>
                <Th className="text-right">Local Support Fees</Th>
                <Th className="text-right">Salary</Th>
                <Th className="text-right">Rollout Advance</Th>
                <Th className="text-right">Total</Th>
                <Th>Status</Th>
              </Tr>
            </Thead>
            <Tbody>
              {myAgentInvoices.map((invoice) => {
                const rolloutNet = invoice.rolloutRepayment + invoice.rolloutNewAdvance;
                return (
                  <Tr key={invoice.id}>
                    <Td className="font-medium text-ink">{invoice.month}</Td>
                    <Td className="text-right">
                      <Money amount={invoice.localSupportFees} currency={invoice.currency} />
                    </Td>
                    <Td className="text-right">
                      <Money amount={invoice.salary} currency={invoice.currency} />
                    </Td>
                    <Td className="text-right">
                      <Money amount={rolloutNet} currency={invoice.currency} />
                    </Td>
                    <Td className="text-right font-medium text-ink">
                      <Money amount={invoice.total} currency={invoice.currency} emphasize />
                    </Td>
                    <Td>
                      <Badge tone={agentInvoiceStatusTone[invoice.status]}>
                        {agentInvoiceStatusLabel[invoice.status]}
                      </Badge>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </TableScroll>
      </Card>
    </SurfacePage>
  );
}
