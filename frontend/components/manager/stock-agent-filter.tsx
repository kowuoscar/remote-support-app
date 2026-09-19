"use client";

import { useId } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * The Manager's Agent filter for the Stock page (agent-stock ticket AC: "The Manager's ... Stock
 * page ... filterable by Agent") — the choice lives in the URL (`?agentId=`), mirroring
 * `CarriersView`'s own `CountryFilter` pattern exactly (same "the page renders whatever the URL
 * says" shape). "All Agents" clears the param rather than sending an empty value.
 */
export function StockAgentFilter({
  agents,
  agentId,
}: {
  agents: { id: string; name: string }[];
  agentId?: string;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const id = useId();

  function queryWith(value: string | null) {
    const params = new URLSearchParams(searchParams.toString());
    if (value === null) params.delete("agentId");
    else params.set("agentId", value);
    const query = params.toString();
    return query ? `${pathname}?${query}` : pathname;
  }

  return (
    <div className="flex items-center gap-2">
      <label htmlFor={id} className="text-[13px] font-medium text-ink-secondary">
        Agent
      </label>
      <select
        id={id}
        value={agentId ?? ""}
        onChange={(event) => router.push(queryWith(event.target.value || null))}
        className="h-9 rounded-lg border border-hairline-strong bg-canvas px-3 text-sm text-ink focus-visible:border-primary"
      >
        <option value="">All Agents</option>
        {agents.map((agent) => (
          <option key={agent.id} value={agent.id}>
            {agent.name}
          </option>
        ))}
      </select>
    </div>
  );
}
