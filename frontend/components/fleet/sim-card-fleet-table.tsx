import type { ReactNode } from "react";
import { Badge } from "@/components/ui/badge";
import { Table, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { Money } from "@/components/ui/money";
import { SimCardNumberControl } from "@/components/fleet/sim-card-number-control";
import { SimCardCarrier } from "@/components/fleet/sim-card-carrier";
import { SimCardInstalledInControl, type InstallableSmartphone } from "@/components/fleet/sim-card-installed-in-control";
import { SimCardPlan } from "@/components/fleet/sim-card-plan";
import { SimCardPlanControl } from "@/components/fleet/sim-card-plan-control";
import { simCardStatusToneByValue } from "@/lib/status";
import { formatLocalDate } from "@/lib/format";
import {
  SIM_CARD_FLAVOR_LABEL,
  SIM_CARD_STATUS_LABEL,
  type CatalogCarrierItem,
  type SimCardListItem,
} from "@/lib/api/types";

/** The plan cell: the correction control for a Postpaid SIM on an active catalog Carrier, else text. */
function SimCardPlanCell({
  sim,
  contractId,
  carrier,
  currency,
  carriersHref,
}: {
  sim: SimCardListItem;
  contractId: string | undefined;
  carrier: CatalogCarrierItem | undefined;
  currency: string;
  carriersHref: string;
}) {
  // With no Carriers page to point to (a Tester), a Carrier with no active plan offers nothing.
  const hasActivePlan = carrier?.postpaidPlans.some((plan) => plan.archivedAt === null) ?? false;
  if (contractId && carrier && sim.flavor === "POSTPAID" && (hasActivePlan || carriersHref)) {
    return (
      <SimCardPlanControl
        contractId={contractId}
        simCardId={sim.id}
        number={sim.number}
        sim={sim}
        carrier={carrier}
        currency={currency}
        carriersHref={carriersHref}
      />
    );
  }
  return <SimCardPlan sim={sim} />;
}

/**
 * The SIM Card table markup shared by every Fleet view. `showMonthlyFee` and `changeStatus` are
 * the axes the three roles vary on (Agent: both; Client: neither; Manager: monthly fee only).
 * The number is a correction control (`SimCardNumberControl`) only when `canCorrect` is given
 * (Manager, Tester). "Installed in" is always shown — editable (`SimCardInstalledInControl`) when `contractId` and
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
  canCorrect,
  carriers,
  carriersHref = "",
  changeStatus,
}: {
  sims: SimCardListItem[];
  currency?: string;
  showMonthlyFee?: boolean;
  /** Present together with `smartphones` to render an editable Installed-in control (Agent,
   * Manager); omitted for read-only (Client). */
  contractId?: string;
  smartphones?: InstallableSmartphone[];
  /** With `contractId`, renders the number correction control (Manager, Tester); omitted for
   * plain text. Independent of the Installed-in control, which `smartphones` governs. */
  canCorrect?: boolean;
  /** The Contract's Carrier catalog. With `canCorrect`, a Postpaid SIM whose Carrier is in it and
   * active gets the plan correction control; every other plan cell reads as plain text. */
  carriers?: CatalogCarrierItem[];
  /** Where the plan picker points when a Carrier has no active plan (the Carriers page). Omitted
   * for a Tester, who has none: a Carrier with no active plan then gets no plan control. */
  carriersHref?: string;
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
            <Td className="tnum font-medium text-ink">
              {contractId && canCorrect ? (
                <SimCardNumberControl contractId={contractId} simCardId={sim.id} number={sim.number} />
              ) : (
                sim.number
              )}
            </Td>
            <Td>
              <SimCardCarrier sim={sim} />
            </Td>
            <Td>
              <SimCardPlanCell
                sim={sim}
                contractId={canCorrect ? contractId : undefined}
                carrier={carriers?.find((carrier) => carrier.id === sim.carrierId && carrier.archivedAt === null)}
                currency={currency ?? ""}
                carriersHref={carriersHref}
              />
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
              {sim.cancellationEffectiveDate ? (
                <span className="mt-1 block text-[12px] text-ink-mute">
                  Cancelled {formatLocalDate(sim.cancellationEffectiveDate)}
                </span>
              ) : null}
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
