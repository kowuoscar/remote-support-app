import { agents, clients, contracts } from "./contracts";

export { agents, clients, contracts };

export const tenantStats = {
  billedThisMonthUSD: 41280,
  payoutThisMonthUSD: 22940,
  clientCount: clients.length,
  agentCount: agents.length,
  contractCount: contracts.length,
};
