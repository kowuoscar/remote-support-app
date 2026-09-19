"use client";

import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { SimCardCarrier } from "@/components/fleet/sim-card-carrier";
import { SimCardInstalledInControl } from "@/components/fleet/sim-card-installed-in-control";
import { simCardNumbersInstalledIn } from "@/components/fleet/installed-in";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import { SmartphoneSerialControl } from "@/components/fleet/smartphone-serial-control";
import { Money } from "@/components/ui/money";
import { IconSim, IconSmartphone } from "@/components/icons";
import { simCardStatusToneByValue, smartphoneStatusToneByValue } from "@/lib/status";
import {
  SIM_CARD_FLAVOR_LABEL,
  SIM_CARD_STATUS_LABEL,
  SMARTPHONE_OWNER_LABEL,
  SMARTPHONE_STATUS_LABEL,
  type CatalogCarrierItem,
  type SimCardListItem,
  type SmartphoneListItem,
} from "@/lib/api/types";
import { CreateSmartphoneDialog } from "@/components/manager/create-smartphone-dialog";
import { CreateSimCardDialog } from "@/components/manager/create-sim-card-dialog";

/**
 * A Contract's Fleet, on its detail view (fleet-management ticket ACs: "Manager can add a
 * Smartphone/SIM Card to a Contract's Fleet"). Mirrors ManagerTestersView's "detail-view CRUD
 * scoped to one owning record" shape.
 */
export function ManagerContractFleetView({
  contractId,
  currency,
  smartphones,
  simCards,
  carriers,
  carriersHref,
}: {
  contractId: string;
  currency: string;
  carriers: CatalogCarrierItem[];
  carriersHref: string;
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
}) {
  return (
    <div className="flex flex-col gap-5">
      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSmartphone className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">Smartphones</h2>
          <span className="tnum text-[13px] text-ink-mute">{smartphones.length}</span>
          <div className="ml-auto">
            <CreateSmartphoneDialog contractId={contractId} />
          </div>
        </div>
        {smartphones.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSmartphone className="h-5 w-5" />}
              title="No smartphones yet"
              description="Add this contract's first smartphone — it starts Active."
            />
          </div>
        ) : (
          <TableScroll className="rounded-none border-0">
            <Table>
              <Thead>
                <Tr>
                  <Th>Model</Th>
                  <Th>Serial</Th>
                  <Th>Owner</Th>
                  <Th>SIM Cards</Th>
                  <Th>Status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {smartphones.map((phone) => (
                  <Tr key={phone.id}>
                    <Td className="font-medium text-ink">{phone.model}</Td>
                    <Td>
                      <SmartphoneSerialControl
                        contractId={contractId}
                        smartphoneId={phone.id}
                        serial={phone.serial}
                      />
                    </Td>
                    <Td className="text-ink-secondary">{SMARTPHONE_OWNER_LABEL[phone.owner]}</Td>
                    <Td className="tnum text-ink-secondary">
                      {simCardNumbersInstalledIn(simCards, phone.id)}
                    </Td>
                    <Td>
                      <Badge tone={smartphoneStatusToneByValue[phone.status]}>
                        {SMARTPHONE_STATUS_LABEL[phone.status]}
                      </Badge>
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
          <span className="tnum text-[13px] text-ink-mute">{simCards.length}</span>
          <div className="ml-auto">
            <CreateSimCardDialog
              contractId={contractId}
              currency={currency}
              carriers={carriers}
              carriersHref={carriersHref}
            />
          </div>
        </div>
        {simCards.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSim className="h-5 w-5" />}
              title="No SIM cards yet"
              description="Add this contract's first SIM card — Postpaid needs a plan, Prepaid doesn't."
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
                  <Th>Installed in</Th>
                </Tr>
              </Thead>
              <Tbody>
                {simCards.map((sim) => (
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
                      {sim.cancellationEffectiveDate ? (
                        <span className="mt-1 block text-[12px] text-ink-mute">
                          Cancelled {sim.cancellationEffectiveDate}
                        </span>
                      ) : null}
                    </Td>
                    <Td>
                      <SimCardInstalledInControl
                        contractId={contractId}
                        simCardId={sim.id}
                        installedInSmartphoneId={sim.installedInSmartphoneId}
                        smartphones={smartphones}
                      />
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
