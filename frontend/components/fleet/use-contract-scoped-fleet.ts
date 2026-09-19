import { useMemo, useState } from "react";
import type { ContractOption } from "@/components/ui/contract-switcher";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";

/**
 * Scopes a Fleet's Smartphones and SIM Cards to the selected Contract, and tracks that selection —
 * shared by AgentFleetView and ClientFleetView, which otherwise carried identical copies.
 */
export function useContractScopedFleet(
  contracts: ContractOption[],
  smartphones: SmartphoneListItem[],
  simCards: SimCardListItem[],
) {
  const [contractId, setContractId] = useState(contracts[0]?.id ?? "");

  const phones = useMemo(
    () => smartphones.filter((p) => p.contractId === contractId),
    [smartphones, contractId],
  );
  const sims = useMemo(
    () => simCards.filter((s) => s.contractId === contractId),
    [simCards, contractId],
  );

  return { contractId, setContractId, phones, sims };
}
