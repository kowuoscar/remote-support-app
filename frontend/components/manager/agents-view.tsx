"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { SearchInput } from "@/components/ui/search-input";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconAgents, IconSearch } from "@/components/icons";
import { CreateAgentDialog } from "@/components/manager/create-agent-dialog";

export interface AgentRow {
  id: string;
  name: string;
  country: string;
  currency: string;
  contractCount: number;
}

export function ManagerAgentsView({ agents }: { agents: AgentRow[] }) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return agents;
    return agents.filter(
      (a) => a.name.toLowerCase().includes(q) || a.country.toLowerCase().includes(q),
    );
  }, [agents, query]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <SearchInput value={query} onChange={setQuery} placeholder="Search agents or countries…" />
        <div className="flex items-center gap-3">
          <span className="shrink-0 text-[13px] text-ink-mute">
            {filtered.length} of {agents.length}
          </span>
          <CreateAgentDialog />
        </div>
      </div>

      {agents.length === 0 ? (
        <EmptyState
          icon={<IconAgents className="h-5 w-5" />}
          title="No agents yet"
          description="Add your first agent with their country and standing salary — currency follows the country automatically."
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<IconSearch className="h-5 w-5" />}
          title={`No agents match “${query}”`}
          description="Try a different name or country, or clear the search to see every agent in the tenant."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Agent</Th>
                <Th>Country</Th>
                <Th>Currency</Th>
                <Th className="text-right">Contracts</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((agent) => (
                <Tr key={agent.id}>
                  <Td className="font-medium text-ink">
                    <Link href={`/manager/agents/${agent.id}`} className="hover:text-primary hover:underline">
                      {agent.name}
                    </Link>
                  </Td>
                  <Td className="text-ink-secondary">{agent.country}</Td>
                  <Td className="tnum text-ink-secondary">{agent.currency}</Td>
                  <Td className="tnum text-right text-ink-secondary">{agent.contractCount}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
