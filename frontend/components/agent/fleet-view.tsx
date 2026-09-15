"use client";

import { useMemo, useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { Money } from "@/components/ui/money";
import { IconSim, IconSmartphone } from "@/components/icons";
import { simStatusTone, smartphoneStatusTone } from "@/lib/status";
import type { SimCard, Smartphone } from "@/lib/demo/types";

export function AgentFleetView({
  smartphones,
  simCards,
  contracts,
}: {
  smartphones: Smartphone[];
  simCards: SimCard[];
  contracts: ContractOption[];
}) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");

  const phones = useMemo(
    () => smartphones.filter((p) => p.contractId === contractId),
    [smartphones, contractId],
  );
  const sims = useMemo(
    () => simCards.filter((s) => s.contractId === contractId),
    [simCards, contractId],
  );
  const currency = contracts.find((c) => c.id === contractId)?.currency ?? "EUR";

  return (
    <div className="flex flex-col gap-5">
      <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />

      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSmartphone className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">Smartphones</h2>
          <span className="tnum ml-auto text-[13px] text-ink-mute">{phones.length}</span>
        </div>
        <TableScroll className="rounded-none border-0">
          <Table>
            <Thead>
              <Tr>
                <Th>Model</Th>
                <Th>Serial</Th>
                <Th>Assigned to</Th>
                <Th>Status</Th>
              </Tr>
            </Thead>
            <Tbody>
              {phones.map((phone) => (
                <Tr key={phone.id}>
                  <Td className="font-medium text-ink">{phone.model}</Td>
                  <Td className="tnum text-ink-secondary">{phone.serial}</Td>
                  <Td className="text-ink-secondary">{phone.assignedTo}</Td>
                  <Td>
                    <Badge tone={smartphoneStatusTone[phone.status]}>{phone.status}</Badge>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      </Card>

      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSim className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">SIM Cards</h2>
          <span className="tnum ml-auto text-[13px] text-ink-mute">{sims.length}</span>
        </div>
        <TableScroll className="rounded-none border-0">
          <Table>
            <Thead>
              <Tr>
                <Th>Number</Th>
                <Th>Carrier</Th>
                <Th>Flavor</Th>
                <Th className="text-right">Monthly fee</Th>
                <Th>Status</Th>
              </Tr>
            </Thead>
            <Tbody>
              {sims.map((sim) => (
                <Tr key={sim.id}>
                  <Td className="tnum font-medium text-ink">{sim.number}</Td>
                  <Td className="text-ink-secondary">{sim.carrier}</Td>
                  <Td className="text-ink-secondary">{sim.flavor}</Td>
                  <Td className="text-right">
                    {sim.monthlyFee > 0 ? (
                      <Money amount={sim.monthlyFee} currency={currency} />
                    ) : (
                      <span className="text-ink-mute">—</span>
                    )}
                  </Td>
                  <Td>
                    <Badge tone={simStatusTone[sim.status]}>{sim.status}</Badge>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      </Card>
    </div>
  );
}
