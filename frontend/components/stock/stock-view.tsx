import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { Money } from "@/components/ui/money";
import { IconSim, IconSmartphone, IconStock } from "@/components/icons";
import { smartphoneStatusToneByValue, simCardStatusToneByValue } from "@/lib/status";
import {
  SIM_CARD_FLAVOR_LABEL,
  SIM_CARD_STATUS_LABEL,
  SMARTPHONE_STATUS_LABEL,
  type SimCardStatusValue,
  type SmartphoneStatusValue,
  type StockUnitItem,
} from "@/lib/api/types";

/**
 * Agent Stock (returns-and-agent-stock spec, Solution's Agent Stock; agent-stock ticket AC: "a
 * Stock page listing ... model, serial, number, Carrier, flavor, Plan and the Contract each came
 * from"). Read-only — a unit enters through a completed Return and leaves through Stock
 * fulfilment (a later ticket), never edited from here — so this mirrors `AgentFleetView`'s two-table
 * shape with no status/change controls. `showAgentColumn` is the Manager's own addition, whose
 * Stock read spans every Agent at once (ticket AC: "the Manager's ... showing every Agent's
 * Stock").
 */
export function StockView({
  units,
  showAgentColumn = false,
}: {
  units: StockUnitItem[];
  showAgentColumn?: boolean;
}) {
  const smartphones = units.filter((unit) => unit.kind === "SMARTPHONE");
  const simCards = units.filter((unit) => unit.kind === "SIM_CARD");

  if (units.length === 0) {
    return (
      <EmptyState
        icon={<IconStock className="h-5 w-5" />}
        title="Nothing in Stock"
        description="A unit lands here once a Return's Kept in Stock Disposition is completed."
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <Card className="p-0">
        <div className="flex items-center gap-2 border-b border-hairline px-5 py-3.5">
          <IconSmartphone className="h-4 w-4 text-ink-mute" />
          <h2 className="text-sm font-semibold text-ink">Smartphones</h2>
          <span className="tnum ml-auto text-[13px] text-ink-mute">{smartphones.length}</span>
        </div>
        {smartphones.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSmartphone className="h-5 w-5" />}
              title="No Smartphones in Stock"
              description="A Smartphone lands here when a Return's Kept in Stock Disposition is completed."
            />
          </div>
        ) : (
          <TableScroll className="rounded-none border-0">
            <Table>
              <Thead>
                <Tr>
                  <Th>Model</Th>
                  <Th>Serial</Th>
                  {showAgentColumn ? <Th>Agent</Th> : null}
                  <Th>From Contract</Th>
                  <Th>Status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {smartphones.map((unit) => (
                  <Tr key={unit.id}>
                    <Td className="font-medium text-ink">{unit.model}</Td>
                    <Td className="text-ink-secondary">{unit.serial ?? <span className="text-ink-mute">—</span>}</Td>
                    {showAgentColumn ? <Td className="whitespace-nowrap text-ink-secondary">{unit.agentName}</Td> : null}
                    <Td className="whitespace-nowrap text-ink-secondary">
                      {unit.fromClientName ?? <span className="text-ink-mute">—</span>}
                    </Td>
                    <Td>
                      <Badge tone={smartphoneStatusToneByValue[unit.status as SmartphoneStatusValue]}>
                        {SMARTPHONE_STATUS_LABEL[unit.status as SmartphoneStatusValue]}
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
          <span className="tnum ml-auto text-[13px] text-ink-mute">{simCards.length}</span>
        </div>
        {simCards.length === 0 ? (
          <div className="px-5 py-8">
            <EmptyState
              icon={<IconSim className="h-5 w-5" />}
              title="No SIM Cards in Stock"
              description="A SIM Card lands here when a Return's Kept in Stock Disposition is completed."
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
                  {showAgentColumn ? <Th>Agent</Th> : null}
                  <Th>From Contract</Th>
                  <Th>Status</Th>
                </Tr>
              </Thead>
              <Tbody>
                {simCards.map((unit) => (
                  <Tr key={unit.id}>
                    <Td className="tnum font-medium text-ink">{unit.number}</Td>
                    <Td className="text-ink-secondary">
                      {unit.carrierName ? (
                        <>
                          {unit.carrierName}
                          {unit.carrierArchived ? (
                            <span className="ml-1.5">
                              <Badge tone="neutral">Archived</Badge>
                            </span>
                          ) : null}
                        </>
                      ) : (
                        <span className="text-ink-mute">—</span>
                      )}
                    </Td>
                    <Td className="text-ink-secondary">
                      {unit.postpaidPlanName ?? <span className="text-ink-mute">—</span>}
                    </Td>
                    <Td className="text-ink-secondary">{unit.flavor ? SIM_CARD_FLAVOR_LABEL[unit.flavor] : "—"}</Td>
                    <Td className="text-right">
                      {unit.monthlyFeeAmount != null ? (
                        <Money amount={unit.monthlyFeeAmount} currency={unit.agentCurrency} />
                      ) : (
                        <span className="text-ink-mute">—</span>
                      )}
                    </Td>
                    {showAgentColumn ? <Td className="whitespace-nowrap text-ink-secondary">{unit.agentName}</Td> : null}
                    <Td className="whitespace-nowrap text-ink-secondary">
                      {unit.fromClientName ?? <span className="text-ink-mute">—</span>}
                    </Td>
                    <Td>
                      <Badge tone={simCardStatusToneByValue[unit.status as SimCardStatusValue]}>
                        {SIM_CARD_STATUS_LABEL[unit.status as SimCardStatusValue]}
                      </Badge>
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
