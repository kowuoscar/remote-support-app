"use client";

import { useMemo, useState } from "react";
import { SearchInput } from "@/components/ui/search-input";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconSearch } from "@/components/icons";
import type { Contract } from "@/lib/demo/types";

export function ManagerContractsView({ contracts }: { contracts: Contract[] }) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return contracts;
    return contracts.filter(
      (c) =>
        c.clientName.toLowerCase().includes(q) ||
        c.agentName.toLowerCase().includes(q) ||
        c.country.toLowerCase().includes(q),
    );
  }, [contracts, query]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <SearchInput value={query} onChange={setQuery} placeholder="Search client, agent or country…" />
        <span className="shrink-0 text-[13px] text-ink-mute">
          {filtered.length} of {contracts.length}
        </span>
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconSearch className="h-5 w-5" />}
          title={`No contracts match “${query}”`}
          description="Try a different client, agent or country, or clear the search to see every contract in the tenant."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Client</Th>
                <Th>Agent</Th>
                <Th>Country</Th>
                <Th className="text-right">Currency</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((contract) => (
                <Tr key={contract.id}>
                  <Td className="font-medium text-ink">{contract.clientName}</Td>
                  <Td className="text-ink-secondary">{contract.agentName}</Td>
                  <Td className="text-ink-secondary">{contract.country}</Td>
                  <Td className="tnum text-right text-ink-secondary">{contract.currency}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
