"use client";

import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { SubmitRequestDialog } from "@/components/client/submit-request-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { FleetTableCard } from "@/components/fleet/fleet-table-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import { useContractScopedFleet } from "@/components/fleet/use-contract-scoped-fleet";
import { IconContracts, IconSim, IconSmartphone } from "@/components/icons";
import { type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";

export function ClientFleetView({
  smartphones,
  simCards,
  contracts,
}: {
  smartphones: SmartphoneListItem[];
  simCards: SimCardListItem[];
  contracts: ContractOption[];
}) {
  const { contractId, setContractId, phones, sims } = useContractScopedFleet(contracts, smartphones, simCards);

  if (contracts.length === 0) {
    return (
      <EmptyState
        icon={<IconContracts className="h-5 w-5" />}
        title="No contracts yet"
        description="Once your company has a contract in place, its fleet of smartphones and SIM cards shows up here."
      />
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <ContractSwitcher contracts={contracts} value={contractId} onChange={setContractId} />
        <SubmitRequestDialog contracts={contracts} />
      </div>

      <FleetTableCard
        icon={<IconSmartphone className="h-4 w-4 text-ink-mute" />}
        title="Smartphones"
        count={phones.length}
        isEmpty={phones.length === 0}
        emptyIcon={<IconSmartphone className="h-5 w-5" />}
        emptyTitle="No smartphones on this contract yet"
        emptyDescription="Your Agent provisions smartphones onto this contract's fleet."
      >
        <SmartphoneFleetTable phones={phones} />
      </FleetTableCard>

      <FleetTableCard
        icon={<IconSim className="h-4 w-4 text-ink-mute" />}
        title="SIM Cards"
        count={sims.length}
        isEmpty={sims.length === 0}
        emptyIcon={<IconSim className="h-5 w-5" />}
        emptyTitle="No SIM cards on this contract yet"
        emptyDescription="Your Agent provisions SIM cards onto this contract's fleet."
      >
        <SimCardFleetTable sims={sims} />
      </FleetTableCard>
    </div>
  );
}
