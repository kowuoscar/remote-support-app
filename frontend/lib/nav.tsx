import type { ComponentType, SVGProps } from "react";
import {
  IconAgents,
  IconCarrier,
  IconClients,
  IconContracts,
  IconDashboard,
  IconFleet,
  IconInvoices,
  IconMyInvoice,
  IconRequests,
} from "@/components/icons";

export interface NavItem {
  href: string;
  label: string;
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

export const managerNav: NavItem[] = [
  { href: "/manager", label: "Dashboard", icon: IconDashboard },
  { href: "/manager/requests", label: "Requests", icon: IconRequests },
  { href: "/manager/clients", label: "Clients", icon: IconClients },
  { href: "/manager/agents", label: "Agents", icon: IconAgents },
  { href: "/manager/contracts", label: "Contracts", icon: IconContracts },
  { href: "/manager/carriers", label: "Carriers", icon: IconCarrier },
  { href: "/manager/invoices", label: "Invoices", icon: IconInvoices },
];

export const agentNav: NavItem[] = [
  { href: "/agent", label: "Dashboard", icon: IconDashboard },
  { href: "/agent/requests", label: "Requests", icon: IconRequests },
  { href: "/agent/fleet", label: "Fleet", icon: IconFleet },
  { href: "/agent/carriers", label: "Carriers", icon: IconCarrier },
  { href: "/agent/client-invoices", label: "Client Invoices", icon: IconInvoices },
  { href: "/agent/my-invoice", label: "My Invoice", icon: IconMyInvoice },
];

export const clientNav: NavItem[] = [
  { href: "/client", label: "Dashboard", icon: IconDashboard },
  { href: "/client/fleet", label: "Fleet", icon: IconFleet },
  { href: "/client/requests", label: "Requests", icon: IconRequests },
  { href: "/client/invoices", label: "Invoices", icon: IconInvoices },
];

export type Surface = "manager" | "agent" | "client";

/**
 * Icon components are function references, which cannot cross the
 * Server → Client Component boundary as props. NavRail is a Client
 * Component, so it imports the config for its surface directly from here
 * rather than receiving the array as a prop from a Server Component layout.
 */
export const navConfigs: Record<Surface, NavItem[]> = {
  manager: managerNav,
  agent: agentNav,
  client: clientNav,
};
