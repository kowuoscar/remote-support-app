"use client";

import { useMemo, useState } from "react";
import { SearchInput } from "@/components/ui/search-input";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconSearch } from "@/components/icons";

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
      <div className="flex items-center justify-between gap-3">
        <SearchInput value={query} onChange={setQuery} placeholder="Search agents or countries…" />
        <span className="shrink-0 text-[13px] text-ink-mute">
          {filtered.length} of {agents.length}
        </span>
      </div>

      {filtered.length === 0 ? (
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
                  <Td className="font-medium text-ink">{agent.name}</Td>
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
