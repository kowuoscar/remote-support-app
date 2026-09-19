"use client";

import { ContractSwitcher, type ContractOption } from "@/components/ui/contract-switcher";
import { EmptyState } from "@/components/ui/empty-state";
import { FleetTableCard } from "@/components/fleet/fleet-table-card";
import { SmartphoneFleetTable } from "@/components/fleet/smartphone-fleet-table";
import { SimCardFleetTable } from "@/components/fleet/sim-card-fleet-table";
import { useContractScopedFleet } from "@/components/fleet/use-contract-scoped-fleet";
import { IconContracts, IconSim, IconSmartphone } from "@/components/icons";
import { type SimCardListItem, type SmartphoneListItem } from "@/lib/api/types";
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
  const { contractId, setContractId, phones, sims } = useContractScopedFleet(contracts, smartphones, simCards);
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

      <FleetTableCard
        icon={<IconSmartphone className="h-4 w-4 text-ink-mute" />}
        title="Smartphones"
        count={phones.length}
        isEmpty={phones.length === 0}
        emptyIcon={<IconSmartphone className="h-5 w-5" />}
        emptyTitle="No smartphones on this contract yet"
        emptyDescription="A manager provisions smartphones onto this contract's fleet."
      >
        <SmartphoneFleetTable
          phones={phones}
          simCards={sims}
          contractId={contractId}
          changeStatus={(phone) => (
            <SmartphoneStatusControl contractId={contractId} smartphoneId={phone.id} status={phone.status} />
          )}
        />
      </FleetTableCard>

      <FleetTableCard
        icon={<IconSim className="h-4 w-4 text-ink-mute" />}
        title="SIM Cards"
        count={sims.length}
        isEmpty={sims.length === 0}
        emptyIcon={<IconSim className="h-5 w-5" />}
        emptyTitle="No SIM cards on this contract yet"
        emptyDescription="A manager provisions SIM cards onto this contract's fleet."
      >
        <SimCardFleetTable
          sims={sims}
          currency={currency}
          showMonthlyFee
          contractId={contractId}
          smartphones={phones}
          changeStatus={(sim) => (
            <SimCardStatusControl contractId={contractId} simCardId={sim.id} status={sim.status} />
          )}
        />
      </FleetTableCard>
    </div>
  );
}
