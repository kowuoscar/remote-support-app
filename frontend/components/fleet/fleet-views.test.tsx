import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { SimCardListItem, SmartphoneListItem } from "@/lib/api/types";
import { stubFetch } from "@/tests/component/fetch";
import { AgentFleetView } from "@/components/agent/fleet-view";
import { ClientFleetView } from "@/components/client/fleet-view";

function contract(id: string, clientName: string, countryLabel: string) {
  return { id, label: `${clientName} — ${countryLabel}`, meta: countryLabel, currency: "USD", clientName, countryLabel };
}

const CONTRACTS = [
  contract("c-zeta", "Zeta Mobile", "Kenya"),
  contract("c-acme-ke", "Acme", "Kenya"),
  contract("c-acme-ug", "Acme", "Uganda"),
];

const phone = (id: string, contractId: string, model: string) =>
  ({ id, contractId, model, serial: null, owner: "COMPANY", status: "ACTIVE" }) as SmartphoneListItem;
const sim = (id: string, contractId: string, number: string) =>
  ({
    id,
    contractId,
    number,
    flavor: "PREPAID",
    status: "ACTIVE",
    monthlyFeeAmount: null,
  }) as SimCardListItem;

const PHONES = [phone("p1", "c-acme-ke", "Pixel 8"), phone("p2", "c-zeta", "Galaxy S24"), phone("p3", "c-zeta", "Moto G")];
const SIMS = [sim("s1", "c-acme-ke", "+254 700 000001"), sim("s2", "c-zeta", "+254 700 000002")];

beforeEach(() => window.history.replaceState(null, "", "/"));
afterEach(() => window.history.replaceState(null, "", "/"));

function renderAgent(props: { initialContractId?: string; initialTab?: string; contracts?: typeof CONTRACTS } = {}) {
  return render(
    <AgentFleetView
      smartphones={PHONES}
      simCards={SIMS}
      carriers={[]}
      contracts={props.contracts ?? CONTRACTS}
      initialContractId={props.initialContractId}
      initialTab={props.initialTab}
    />,
  );
}

const clients = () => screen.getByRole("navigation", { name: "Clients" });
const fleetTabs = () => screen.getByRole("tablist", { name: "Fleet" });

describe("AgentFleetView", () => {
  it("lists the Clients alphabetically, the Country only on a shared name, the first current", () => {
    renderAgent();
    const links = within(clients()).getAllByRole("link");
    expect(links.map((l) => l.textContent?.replace(/\s+/g, " ").trim())).toEqual([
      "Acme Kenya",
      "Acme Uganda",
      "Zeta Mobile",
    ]);
    expect(links[0]).toHaveAttribute("aria-current", "true");
  });

  it("honours ?contract= and falls to the first Client for one that is not the Agent's", () => {
    const { unmount } = renderAgent({ initialContractId: "c-zeta" });
    expect(within(clients()).getByRole("link", { name: "Zeta Mobile" })).toHaveAttribute("aria-current", "true");
    unmount();
    renderAgent({ initialContractId: "someone-elses" });
    expect(within(clients()).getAllByRole("link")[0]).toHaveAttribute("aria-current", "true");
  });

  it("a pick swaps the Fleet with no request and writes ?contract=", async () => {
    const fetchMock = stubFetch(200, {});
    renderAgent();
    await userEvent.click(within(clients()).getByRole("link", { name: "Zeta Mobile" }));
    expect(window.location.search).toBe("?contract=c-zeta");
    expect(screen.getByText("+254 700 000002")).toBeInTheDocument();
    expect(screen.queryByText("+254 700 000001")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a Fleet tablist with SIM Cards selected first, one panel at a time", () => {
    renderAgent();
    const tabs = within(fleetTabs()).getAllByRole("tab");
    expect(tabs.map((t) => t.textContent?.replace(/\s+/g, " ").trim())).toEqual(["SIM Cards 1", "Smartphones 1"]);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tabpanel", { name: /SIM Cards/ })).toBeInTheDocument();
    expect(screen.getAllByRole("tabpanel")).toHaveLength(1);
    expect(screen.queryByRole("columnheader", { name: "Model" })).not.toBeInTheDocument();
  });

  it("ArrowRight selects Smartphones with focus on it; Home and End jump; the address follows", async () => {
    renderAgent();
    const [simsTab] = within(fleetTabs()).getAllByRole("tab");
    simsTab?.focus();
    await userEvent.keyboard("{ArrowRight}");
    const smartphones = within(fleetTabs()).getByRole("tab", { name: /Smartphones/ });
    expect(smartphones).toHaveAttribute("aria-selected", "true");
    expect(smartphones).toHaveFocus();
    expect(window.location.search).toBe("?tab=smartphones");
    expect(screen.getByText("Pixel 8")).toBeInTheDocument();

    await userEvent.keyboard("{Home}");
    const first = within(fleetTabs()).getByRole("tab", { name: /SIM Cards/ });
    expect(first).toHaveAttribute("aria-selected", "true");
    expect(first).toHaveFocus();
    expect(window.location.search).toBe("");

    await userEvent.keyboard("{End}");
    expect(within(fleetTabs()).getByRole("tab", { name: /Smartphones/ })).toHaveFocus();
    await userEvent.keyboard("{ArrowRight}");
    expect(within(fleetTabs()).getByRole("tab", { name: /SIM Cards/ })).toHaveFocus();
  });

  it("honours ?tab=smartphones and keeps the tab across a Client pick", async () => {
    renderAgent({ initialTab: "smartphones" });
    expect(within(fleetTabs()).getByRole("tab", { name: /Smartphones/ })).toHaveAttribute("aria-selected", "true");
    await userEvent.click(within(clients()).getByRole("link", { name: "Zeta Mobile" }));
    expect(within(fleetTabs()).getByRole("tab", { name: /Smartphones 2/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Galaxy S24")).toBeInTheDocument();
    expect(window.location.search).toContain("tab=smartphones");
    expect(window.location.search).toContain("contract=c-zeta");
  });

  it("an unknown ?tab= opens SIM Cards", () => {
    renderAgent({ initialTab: "nonsense" });
    expect(within(fleetTabs()).getByRole("tab", { name: /SIM Cards/ })).toHaveAttribute("aria-selected", "true");
  });

  it("shows no table title repeating the tab label", () => {
    renderAgent();
    expect(screen.queryByRole("heading", { name: /SIM Cards|Smartphones/ })).not.toBeInTheDocument();
  });

  it("a single Contract shows the column with one current row", () => {
    renderAgent({ contracts: [CONTRACTS[0]!] });
    const links = within(clients()).getAllByRole("link");
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("aria-current", "true");
  });

  it("no Contracts shows No contracts yet and no column", () => {
    renderAgent({ contracts: [] });
    expect(screen.getByText("No contracts yet")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Clients" })).not.toBeInTheDocument();
  });
});

describe("ClientFleetView", () => {
  function renderTester(contracts = CONTRACTS.slice(1)) {
    return render(<ClientFleetView smartphones={PHONES} simCards={SIMS} contracts={contracts} />);
  }

  it("two Contracts give a Contracts landmark, one row per Country, sorted", () => {
    renderTester();
    const nav = screen.getByRole("navigation", { name: "Contracts" });
    expect(within(nav).getAllByRole("link").map((l) => l.textContent)).toEqual(["Kenya", "Uganda"]);
    expect(within(nav).getAllByRole("link")[0]).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "Submit Request" })).toBeInTheDocument();
  });

  it("one Contract gives no landmark and today's chip above the tabs", () => {
    renderTester([CONTRACTS[1]!]);
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(screen.getAllByText("Acme — Kenya").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /Acme/ })).not.toBeInTheDocument();
    expect(screen.getByRole("tablist", { name: "Fleet" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Submit Request" })).toBeInTheDocument();
  });
});
