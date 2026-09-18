"use client";

import { useMemo, useState } from "react";
import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { SimCardCarrier } from "@/components/fleet/sim-card-carrier";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import { Money } from "@/components/ui/money";
import { IconContracts, IconSim, IconSmartphone } from "@/components/icons";
import { simCardStatusToneByValue, smartphoneStatusToneByValue } from "@/lib/status";
import {
  SIM_CARD_FLAVOR_LABEL,
  SIM_CARD_STATUS_LABEL,
  SMARTPHONE_STATUS_LABEL,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";
import { SimCardStatusControl, SmartphoneStatusControl } from "@/components/agent/fleet-status-controls";

export function AgentFleetView({
  smartphones,
  simCards,
  contracts,
}: {
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
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
  const currency = contracts.find((c) => c.id === contractId)?.currency ?? "";

  if (contracts.length === 0) {
    return (
      <EmptyState
        icon={<IconContracts className="h-5 w-5" />}
        title="No contracts yet"
        description="Once a manager creates a contract for you, its fleet of smartphones and SIM cards shows up here."
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />

      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSmartphone className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">Smartphones</h2>
          <span className="tnum ml-auto text-[13px] text-ink-mute">{phones.length}</span>
        </div>
        {phones.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSmartphone className="h-5 w-5" />}
              title="No smartphones on this contract yet"
              description="A manager provisions smartphones onto this contract's fleet."
            />
          </div>
        ) : (
          <TableScroll className="rounded-none border-0">
            <Table>
              <Thead>
                <Tr>
                  <Th>Model</Th>
                  <Th>Serial</Th>
                  <Th>Assigned to</Th>
                  <Th>Status</Th>
                  <Th>Change status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {phones.map((phone) => (
                  <Tr key={phone.id}>
                    <Td className="font-medium text-ink">{phone.model}</Td>
                    <Td className="tnum text-ink-secondary">{phone.serial}</Td>
                    <Td className="text-ink-secondary">{phone.assignedTo ?? "—"}</Td>
                    <Td>
                      <Badge tone={smartphoneStatusToneByValue[phone.status]}>
                        {SMARTPHONE_STATUS_LABEL[phone.status]}
                      </Badge>
                    </Td>
                    <Td>
                      <SmartphoneStatusControl
                        contractId={contractId}
                        smartphoneId={phone.id}
                        status={phone.status}
                      />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </TableScroll>
        )}
      </Card>

      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSim className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">SIM Cards</h2>
          <span className="tnum ml-auto text-[13px] text-ink-mute">{sims.length}</span>
        </div>
        {sims.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSim className="h-5 w-5" />}
              title="No SIM cards on this contract yet"
              description="A manager provisions SIM cards onto this contract's fleet."
            />
          </div>
        ) : (
          <TableScroll className="rounded-none border-0">
            <Table>
              <Thead>
                <Tr>
                  <Th>Number</Th>
                  <Th>Carrier</Th>
                  <Th>Plan</Th>
                  <Th>Flavor</Th>
                  <Th className="text-right">Monthly fee</Th>
                  <Th>Status</Th>
                  <Th>Change status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {sims.map((sim) => (
                  <Tr key={sim.id}>
                    <Td className="tnum font-medium text-ink">{sim.number}</Td>
                    <Td>
                      <SimCardCarrier sim={sim} />
                    </Td>
                    <Td>
                      <SimCardPlan sim={sim} />
                    </Td>
                    <Td className="text-ink-secondary">{SIM_CARD_FLAVOR_LABEL[sim.flavor]}</Td>
                    <Td className="text-right">
                      {sim.monthlyFeeAmount != null ? (
                        <Money amount={sim.monthlyFeeAmount} currency={currency} />
                      ) : (
                        <span className="text-ink-mute">—</span>
                      )}
                    </Td>
                    <Td>
                      <Badge tone={simCardStatusToneByValue[sim.status]}>
                        {SIM_CARD_STATUS_LABEL[sim.status]}
                      </Badge>
                    </Td>
                    <Td>
                      <SimCardStatusControl contractId={contractId} simCardId={sim.id} status={sim.status} />
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </TableScroll>
        )}
      </Card>
    </div>
  );
}
