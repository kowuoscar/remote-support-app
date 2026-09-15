"use client";

import { useMemo, useState } from "react";
import { SearchInput } from "@/components/ui/search-input";
import { EmptyState } from "@/components/ui/empty-state";
import { Table, TableScroll, Tbody, Td, Th, Thead, Tr } from "@/components/ui/table";
import { IconSearch } from "@/components/icons";

export interface ClientRow {
  id: string;
  name: string;
  primaryContact: string;
  contractCount: number;
}

export function ManagerClientsView({ clients }: { clients: ClientRow[] }) {
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return clients;
    return clients.filter(
      (c) => c.name.toLowerCase().includes(q) || c.primaryContact.toLowerCase().includes(q),
    );
  }, [clients, query]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <SearchInput value={query} onChange={setQuery} placeholder="Search clients or contacts…" />
        <span className="shrink-0 text-[13px] text-ink-mute">
          {filtered.length} of {clients.length}
        </span>
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<IconSearch className="h-5 w-5" />}
          title={`No clients match “${query}”`}
          description="Try a different name or primary contact, or clear the search to see every client in the tenant."
        />
      ) : (
        <TableScroll>
          <Table>
            <Thead>
              <Tr>
                <Th>Client</Th>
                <Th>Primary contact</Th>
                <Th className="text-right">Contracts</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filtered.map((client) => (
                <Tr key={client.id}>
                  <Td className="font-medium text-ink">{client.name}</Td>
                  <Td className="text-ink-secondary">{client.primaryContact}</Td>
                  <Td className="tnum text-right text-ink-secondary">{client.contractCount}</Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        </TableScroll>
      )}
    </div>
  );
}
