import type { Contract } from "./types";

export const contracts: Contract[] = [
  {
    id: "ctr-aurora-fr",
    clientName: "Aurora Retail Group",
    agentName: "Camille Duforet",
    country: "France",
    currency: "EUR",
    label: "Aurora Retail Group — France",
  },
  {
    id: "ctr-aurora-uk",
    clientName: "Aurora Retail Group",
    agentName: "Owen Whitfield",
    country: "United Kingdom",
    currency: "GBP",
    label: "Aurora Retail Group — United Kingdom",
  },
  {
    id: "ctr-meridian-mx",
    clientName: "Meridian Logistics",
    agentName: "Luis Bautista",
    country: "Mexico",
    currency: "MXN",
    label: "Meridian Logistics — Mexico",
  },
  {
    id: "ctr-meridian-es",
    clientName: "Meridian Logistics",
    agentName: "Marta Solano",
    country: "Spain",
    currency: "EUR",
    label: "Meridian Logistics — Spain",
  },
  {
    id: "ctr-kessler-es",
    clientName: "Kessler & Vance LLP",
    agentName: "Marta Solano",
    country: "Spain",
    currency: "EUR",
    label: "Kessler & Vance LLP — Spain",
  },
  {
    id: "ctr-brightpath-ph",
    clientName: "Bright Path Clinics",
    agentName: "Priya Nair",
    country: "Philippines",
    currency: "PHP",
    label: "Bright Path Clinics — Philippines",
  },
  {
    id: "ctr-solene-fr",
    clientName: "Solene Cosmetics",
    agentName: "Camille Duforet",
    country: "France",
    currency: "EUR",
    label: "Solene Cosmetics — France",
  },
  {
    id: "ctr-harborfinch-uk",
    clientName: "Harbor & Finch Realty",
    agentName: "Owen Whitfield",
    country: "United Kingdom",
    currency: "GBP",
    label: "Harbor & Finch Realty — United Kingdom",
  },
];

const primaryContacts: Record<string, string> = {
  "Aurora Retail Group": "Nadia Okafor",
  "Meridian Logistics": "Tom Reyes",
  "Kessler & Vance LLP": "Helena Voss",
  "Bright Path Clinics": "Dr. Mikael Santos",
  "Solene Cosmetics": "Élise Fabron",
  "Harbor & Finch Realty": "Charlotte Finch",
};

export const clients = Array.from(new Set(contracts.map((c) => c.clientName))).map(
  (name, index) => ({
    id: `client-${index + 1}`,
    name,
    contractCount: contracts.filter((c) => c.clientName === name).length,
    primaryContact: primaryContacts[name] ?? "—",
  }),
);

export const agents = Array.from(new Set(contracts.map((c) => c.agentName))).map(
  (name, index) => {
    const agentContracts = contracts.filter((c) => c.agentName === name);
    return {
      id: `agent-${index + 1}`,
      name,
      country: agentContracts[0].country,
      currency: agentContracts[0].currency,
      contractCount: agentContracts.length,
    };
  },
);
