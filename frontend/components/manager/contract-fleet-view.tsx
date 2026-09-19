"use client";

import { FleetTableCard } from "@/components/fleet/fleet-table-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import { IconSim, IconSmartphone } from "@/components/icons";
import { type CatalogCarrierItem, type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";
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
      <FleetTableCard
        icon={<IconSmartphone className="h-4 w-4 text-ink-mute" />}
        title="Smartphones"
        count={smartphones.length}
        action={<CreateSmartphoneDialog contractId={contractId} />}
        isEmpty={smartphones.length === 0}
        emptyIcon={<IconSmartphone className="h-5 w-5" />}
        emptyTitle="No smartphones yet"
        emptyDescription="Add this contract's first smartphone — it starts Active."
      >
        <SmartphoneFleetTable phones={smartphones} simCards={simCards} contractId={contractId} />
      </FleetTableCard>

      <FleetTableCard
        icon={<IconSim className="h-4 w-4 text-ink-mute" />}
        title="SIM Cards"
        count={simCards.length}
        action={
          <CreateSimCardDialog
            contractId={contractId}
            currency={currency}
            carriers={carriers}
            carriersHref={carriersHref}
          />
        }
        isEmpty={simCards.length === 0}
        emptyIcon={<IconSim className="h-5 w-5" />}
        emptyTitle="No SIM cards yet"
        emptyDescription="Add this contract's first SIM card — Postpaid needs a plan, Prepaid doesn't."
      >
        <SimCardFleetTable
          sims={simCards}
          currency={currency}
          showMonthlyFee
          contractId={contractId}
          smartphones={smartphones}
        />
      </FleetTableCard>
    </div>
  );
}
