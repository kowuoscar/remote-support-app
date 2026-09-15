/** Mirrors backend/.../domain/Country.java — a fixed six-country lookup that deterministically
 * fixes an Agent's currency (spec.md: "a country (which fixes their currency)"). */
export type Country = "FRANCE" | "SPAIN" | "UNITED_KINGDOM" | "MEXICO" | "PHILIPPINES" | "UNITED_STATES";

export const COUNTRIES: { value: Country; label: string; currency: string }[] = [
  { value: "FRANCE", label: "France", currency: "EUR" },
  { value: "SPAIN", label: "Spain", currency: "EUR" },
  { value: "UNITED_KINGDOM", label: "United Kingdom", currency: "GBP" },
  { value: "MEXICO", label: "Mexico", currency: "MXN" },
  { value: "PHILIPPINES", label: "Philippines", currency: "PHP" },
  { value: "UNITED_STATES", label: "United States", currency: "USD" },
];

export function countryLabel(country: string): string {
  return COUNTRIES.find((c) => c.value === country)?.label ?? country;
}

// Mirrors backend/.../dto/ClientResponse.java
export interface ClientListItem {
  id: string;
  name: string;
  primaryContactUsername: string | null;
  contractCount: number;
}

// Mirrors backend/.../dto/TesterResponse.java
export interface TesterListItem {
  id: string;
  clientId: string;
  username: string;
  isPrimaryContact: boolean;
}

// Mirrors backend/.../dto/AgentResponse.java
export interface AgentListItem {
  id: string;
  name: string;
  country: Country;
  currency: string;
  salaryAmount: number;
  contractCount: number;
}

// Mirrors backend/.../dto/ContractResponse.java
export interface ContractListItem {
  id: string;
  clientId: string;
  clientName: string;
  agentId: string;
  agentName: string;
  country: string;
  currency: string;
}

// Mirrors backend/.../domain/SmartphoneStatus.java
export type SmartphoneStatusValue = "ACTIVE" | "IN_REPAIR" | "RETIRED";

// Mirrors backend/.../domain/SimCardStatus.java
export type SimCardStatusValue = "ACTIVE" | "RETIRED";

// Mirrors backend/.../domain/SimCardFlavor.java
export type SimCardFlavorValue = "POSTPAID" | "PREPAID";

export const SMARTPHONE_STATUS_LABEL: Record<SmartphoneStatusValue, string> = {
  ACTIVE: "Active",
  IN_REPAIR: "In Repair",
  RETIRED: "Retired",
};

export const SIM_CARD_STATUS_LABEL: Record<SimCardStatusValue, string> = {
  ACTIVE: "Active",
  RETIRED: "Retired",
};

export const SIM_CARD_FLAVOR_LABEL: Record<SimCardFlavorValue, string> = {
  POSTPAID: "Postpaid",
  PREPAID: "Prepaid",
};

/** Mirrors backend/.../domain/SmartphoneStatus.java#canTransitionTo. */
export function nextSmartphoneStatuses(current: SmartphoneStatusValue): SmartphoneStatusValue[] {
  switch (current) {
    case "ACTIVE":
      return ["IN_REPAIR", "RETIRED"];
    case "IN_REPAIR":
      return ["ACTIVE", "RETIRED"];
    case "RETIRED":
      return [];
  }
}

// Mirrors backend/.../dto/SmartphoneResponse.java
export interface SmartphoneListItem {
  id: string;
  contractId: string;
  model: string;
  serial: string;
  assignedTo: string | null;
  status: SmartphoneStatusValue;
}

// Mirrors backend/.../dto/SimCardResponse.java
export interface SimCardListItem {
  id: string;
  contractId: string;
  number: string;
  carrier: string | null;
  flavor: SimCardFlavorValue;
  monthlyFeeAmount: number | null;
  status: SimCardStatusValue;
}
