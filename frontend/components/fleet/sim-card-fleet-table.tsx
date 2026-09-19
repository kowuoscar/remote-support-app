import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { Money } from "@/components/ui/money";
import { SimCardCarrier } from "@/components/fleet/sim-card-carrier";
import { SimCardInstalledInControl, type InstallableSmartphone } from "@/components/fleet/sim-card-installed-in-control";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import { simCardStatusToneByValue } from "@/lib/status";
import { SIM_CARD_FLAVOR_LABEL, SIM_CARD_STATUS_LABEL, type SimCardListItem } from "@/lib/api/types";

/**
 * The SIM Card table markup shared by every Fleet view. `showMonthlyFee` and `changeStatus` are
 * the axes the three roles vary on (Agent: both; Client: neither; Manager: monthly fee only).
 * "Installed in" is always shown — editable (`SimCardInstalledInControl`) when `contractId` and
 * `smartphones` are given (Agent, Manager), otherwise plain text (Client, read-only). Extracted
 * from the near-identical copies in agent/fleet-view.tsx, client/fleet-view.tsx and
 * manager/contract-fleet-view.tsx — same markup and classes each caller had inline, so rendering
 * is unchanged.
 */
export function SimCardFleetTable({
  sims,
  currency,
  showMonthlyFee = false,
  contractId,
  smartphones,
  changeStatus,
}: {
  sims: SimCardListItem[];
  currency?: string;
  showMonthlyFee?: boolean;
  /** Present together with `smartphones` to render an editable Installed-in control (Agent,
   * Manager); omitted for read-only (Client). */
  contractId?: string;
  smartphones?: InstallableSmartphone[];
  changeStatus?: (sim: SimCardListItem) => ReactNode;
}) {
  return (
    <Table>
      <Thead>
        <Tr>
          <Th>Number</Th>
          <Th>Carrier</Th>
          <Th>Plan</Th>
          <Th>Flavor</Th>
          {showMonthlyFee ? <Th className="text-right">Monthly fee</Th> : null}
          <Th>Status</Th>
          <Th>Installed in</Th>
          {changeStatus ? <Th>Change status</Th> : null}
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
            {showMonthlyFee ? (
              <Td className="text-right">
                {sim.monthlyFeeAmount != null ? (
                  <Money amount={sim.monthlyFeeAmount} currency={currency ?? ""} />
                ) : (
                  <span className="text-ink-mute">—</span>
                )}
              </Td>
            ) : null}
            <Td>
              <Badge tone={simCardStatusToneByValue[sim.status]}>{SIM_CARD_STATUS_LABEL[sim.status]}</Badge>
            </Td>
            <Td className={contractId && smartphones ? undefined : "text-ink-secondary"}>
              {contractId && smartphones ? (
                <SimCardInstalledInControl
                  contractId={contractId}
                  simCardId={sim.id}
                  installedInSmartphoneId={sim.installedInSmartphoneId}
                  smartphones={smartphones}
                />
              ) : (
                (sim.installedInSmartphoneModel ?? "—")
              )}
            </Td>
            {changeStatus ? <Td>{changeStatus(sim)}</Td> : null}
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
}
