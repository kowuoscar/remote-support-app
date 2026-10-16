import { describe, expect, it } from "vitest";
import { fleetTabFromParam, tabParam } from "@/components/fleet/fleet-tabs-card";

describe("fleetTabFromParam", () => {
  it("opens Smartphones only for the exact value, SIM Cards for anything else", () => {
    expect(fleetTabFromParam("smartphones")).toBe("smartphones");
    expect(fleetTabFromParam("nonsense")).toBe("sim-cards");
    expect(fleetTabFromParam(undefined)).toBe("sim-cards");
  });
});

describe("tabParam", () => {
  it("leaves SIM Cards, the default, out of the address", () => {
    expect(tabParam("sim-cards")).toBeNull();
    expect(tabParam("smartphones")).toBe("smartphones");
  });
});
