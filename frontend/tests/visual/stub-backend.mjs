// A fixed, in-memory stand-in for the backend, for the visual-regression suite only
// (playwright.config.ts). Goldens must not depend on a live database, so backend-driven pages
// captured by the suite (the Carriers pages) read this fixture instead. The caller's role comes
// from the placeholder session token the spec sets; an unknown token gets a 401 on every route,
// which is what the dashboards' backend-driven regions saw before this stub existed.
import { createServer } from "node:http";

const PORT = Number(process.env.STUB_BACKEND_PORT ?? 4174);

const ROLES = {
  "visual-agent-session": { username: "agent@example.com", role: "AGENT", country: "UNITED_STATES" },
  "visual-manager-session": { username: "manager@example.com", role: "MANAGER" },
};

const CURRENCY = {
  FRANCE: "EUR",
  SPAIN: "EUR",
  UNITED_KINGDOM: "GBP",
  MEXICO: "MXN",
  PHILIPPINES: "PHP",
  UNITED_STATES: "USD",
};

const ARCHIVED = "2024-06-01T00:00:00Z";

// Topup Options and Postpaid Plans per Carrier, as [name, price, archivedAt]. One of each list is
// archived and one Carrier has no Topup Options, so the archived rows and the empty list are both
// covered.
const OFFERS = {
  c1: {
    topupOptions: [["Prepaid Refill 25", 25], ["Prepaid Refill 50", 50]],
    postpaidPlans: [["Unlimited Starter", 65.99], ["Unlimited Premium", 85.99]],
  },
  c2: {
    topupOptions: [["Data Pass 5GB", 15], ["Data Pass 15GB", 30], ["Data Pass 1GB", 5, ARCHIVED]],
    postpaidPlans: [["Essentials", 60], ["Go5G", 75]],
  },
  c3: {
    topupOptions: [],
    postpaidPlans: [["Unlimited Welcome", 65], ["Unlimited Plus", 80], ["Start Unlimited", 70, ARCHIVED]],
  },
  c4: { topupOptions: [], postpaidPlans: [] },
};

function offers(carrierId, list) {
  return OFFERS[carrierId][list].map(([name, price, archivedAt = null], index) => ({
    id: `${carrierId}-${list}-${index}`,
    carrierId,
    name,
    price,
    archivedAt,
  }));
}

function catalog(country) {
  const carriers = [
    { id: "c1", name: "AT&T", archivedAt: null },
    { id: "c2", name: "T-Mobile", archivedAt: null },
    { id: "c3", name: "Verizon", archivedAt: null },
    { id: "c4", name: "Sprint", archivedAt: "2024-04-01T00:00:00Z" },
  ].map((carrier) => ({
    ...carrier,
    country,
    topupOptions: offers(carrier.id, "topupOptions"),
    postpaidPlans: offers(carrier.id, "postpaidPlans"),
  }));
  return { country, currency: CURRENCY[country], carriers };
}

// manager-approves-requests ticket: two Pending Approval Requests for the Pending Requests page
// surface — one Tester-raised Provision Smartphone, one Agent-authored Replace SIM, so both a
// "raised by" and a "logged by" row, and both a Provision and a Replace details summary, are
// covered. Ages are fixed, not computed against a live clock, but the page's own "waiting" column
// still resolves them against the *real* current time, so tests/visual/surfaces.spec.ts masks it
// rather than freezing it into the golden (it would otherwise drift by a day every day the suite
// runs — see that file's `timeDrivenRegions`).
const PENDING_REQUESTS = [
  {
    request: {
      id: "11111111-0000-0000-0000-000000000001",
      contractId: "22222222-0000-0000-0000-000000000001",
      type: "PROVISION_SMARTPHONE",
      status: "PENDING_APPROVAL",
      raisedByTesterId: "33333333-0000-0000-0000-000000000001",
      raisedByUsername: "priya.raman@aurora.example",
      agentAuthored: false,
      loggedByUsername: "priya.raman@aurora.example",
      cancellationReason: null,
      description: null,
      createdAt: "2026-09-14T09:00:00Z",
      requestedModel: "iPhone 15 Pro",
    },
    clientName: "Aurora Retail Group",
    agentName: "Jordan Ellis",
    waitingSince: "2026-09-14T09:00:00Z",
  },
  {
    request: {
      id: "11111111-0000-0000-0000-000000000002",
      contractId: "22222222-0000-0000-0000-000000000002",
      type: "REPLACE_SIM",
      status: "PENDING_APPROVAL",
      raisedByTesterId: "33333333-0000-0000-0000-000000000002",
      raisedByUsername: "owen.reyes@meridian.example",
      agentAuthored: true,
      loggedByUsername: "agent@example.com",
      cancellationReason: null,
      description: null,
      createdAt: "2026-09-12T09:00:00Z",
      targetSimCardId: "44444444-0000-0000-0000-000000000001",
      targetSimCardNumber: "+1-555-0100",
    },
    clientName: "Meridian Logistics",
    agentName: "Priya Nair",
    waitingSince: "2026-09-12T09:00:00Z",
  },
];

function send(response, status, body) {
  response.writeHead(status, { "Content-Type": "application/json" });
  response.end(body === undefined ? "" : JSON.stringify(body));
}

createServer((request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${PORT}`);
  if (url.pathname === "/health") return send(response, 200, { status: "UP" });

  const token = (request.headers.authorization ?? "").replace(/^Bearer /, "");
  const caller = ROLES[token];
  if (!caller) return send(response, 401);

  if (url.pathname === "/api/me") {
    return send(response, 200, { username: caller.username, role: caller.role });
  }
  if (url.pathname === "/api/carriers" && request.method === "GET") {
    const country = url.searchParams.get("country") ?? caller.country;
    return CURRENCY[country] ? send(response, 200, catalog(country)) : send(response, 400);
  }
  if (url.pathname === "/api/pending-requests" && request.method === "GET") {
    return caller.role === "MANAGER" ? send(response, 200, PENDING_REQUESTS) : send(response, 403);
  }
  return send(response, 404);
}).listen(PORT, "127.0.0.1");
