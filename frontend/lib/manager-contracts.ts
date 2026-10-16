import type { ContractListItem } from "@/lib/api/types";

const collator = new Intl.Collator(undefined, { sensitivity: "base" });

/** One Client of the Manager's Contracts page with the Contracts it holds, first Agent by name first. */
export type ClientContracts = { clientId: string; clientName: string; contracts: ContractListItem[] };

/** Clients that hold at least one Contract, by Client name; each one's Contracts by Agent name. */
export function groupByClient(contracts: ContractListItem[]): ClientContracts[] {
  const byClient = new Map<string, ClientContracts>();
  for (const contract of contracts) {
    const group = byClient.get(contract.clientId) ?? {
      clientId: contract.clientId,
      clientName: contract.clientName,
      contracts: [],
    };
    group.contracts.push(contract);
    byClient.set(contract.clientId, group);
  }
  const groups = [...byClient.values()];
  for (const group of groups) group.contracts.sort((a, b) => collator.compare(a.agentName, b.agentName));
  return groups.sort((a, b) => collator.compare(a.clientName, b.clientName));
}

/**
 * The selected Contract (manager-contracts spec): the `contract` parameter when it names one, else
 * the first Client by name and that Client's first Agent by name. An unknown value is ignored
 * silently. `null` when there are no Contracts.
 */
export function resolveSelectedContract(
  contracts: ContractListItem[],
  param: string | undefined,
): ContractListItem | null {
  return contracts.find((c) => c.id === param) ?? groupByClient(contracts)[0]?.contracts[0] ?? null;
}
