import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CONTRACTS, PHONES, SIMS } from "@/tests/component/fleet-fixtures";
import { ClientFleetView } from "@/components/client/fleet-view";

describe("ClientFleetView", () => {
  function renderTester(contracts = CONTRACTS.slice(1)) {
    return render(<ClientFleetView smartphones={PHONES} simCards={SIMS} contracts={contracts} initialTab="sim-cards" />);
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
