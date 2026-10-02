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
  // real-agent-dashboard: an Agent login that is linked to no Agent record (GET /api/me/agent is
  // 404), and one whose identity read fails outright (500). Not captured as goldens; the
  // not-linked and page-level failure states are asserted in surfaces.spec.ts.
  "visual-agent-unlinked-session": { username: "unlinked@example.com", role: "AGENT", unlinked: true },
  "visual-agent-failing-identity-session": {
    username: "failing@example.com",
    role: "AGENT",
    failingIdentity: true,
  },
  // real-agent-dashboard: a linked Agent whose invoice route answers 500, and whose second
  // Contract's Requests route does too, so the unavailable card states are playable.
  "visual-agent-degraded-session": { username: "degraded@example.com", role: "AGENT", degraded: true },
  // real-client-dashboard: a Tester linked to "Solstice Retail Group", one linked to no Client
  // (GET /api/me/client is 404), and one whose identity read fails outright (500). The last two are
  // not captured as goldens; they are asserted in surfaces.spec.ts.
  "visual-tester-session": { username: "dana.whitfield@solsticeretail.example", role: "TESTER" },
  "visual-tester-unlinked-session": { username: "unlinked.tester@example.com", role: "TESTER", unlinked: true },
  "visual-tester-failing-identity-session": {
    username: "failing.tester@example.com",
    role: "TESTER",
    failingIdentity: true,
  },
  // real-client-dashboard: a linked Tester whose second Contract's SIM Cards and Requests routes
  // answer 500 (Active Fleet and Open Requests both unavailable), and one whose Contract list
  // answers 500 (every Contract-derived region unavailable). Asserted in client-dashboard-regions.spec.ts.
  "visual-tester-degraded-session": { username: "degraded.tester@example.com", role: "TESTER", degraded: true },
  "visual-tester-contracts-failing-session": {
    username: "contracts.failing.tester@example.com",
    role: "TESTER",
    contractsFailing: true,
  },
};

// real-client-dashboard: the Client the linked Tester belongs to.
const TESTER_CLIENT = { clientId: "55555555-0000-0000-0000-0000000000a1", name: "Solstice Retail Group" };

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

// agent-stock ticket: two Agents (only Jordan Ellis is the seeded caller's own — the Manager's
// filter dropdown also needs a second one to be a real choice) and two Stock units, both held by
// Jordan Ellis — one Smartphone, one Postpaid SIM Card with a Carrier and Plan — so both of the
// Stock page's tables render non-empty in the same golden.
const AGENTS = [
  {
    id: "a0000000-0000-0000-0000-000000000001",
    name: "Jordan Ellis",
    country: "UNITED_STATES",
    currency: "USD",
    salaryAmount: 3200,
    contractCount: 2,
    loginUsername: "agent@example.com",
  },
  {
    id: "a0000000-0000-0000-0000-000000000002",
    name: "Priya Nair",
    country: "UNITED_KINGDOM",
    currency: "GBP",
    salaryAmount: 2800,
    contractCount: 1,
    loginUsername: null,
  },
];

// The password the stubbed reset route reveals (the golden of the reveal step).
const RESET_PASSWORD = "k7mq-x3vh-p9te";

// reset-a-testers-password-ui: one Client with two Testers (the primary contact and one more), so
// the Testers table renders its trailing Reset password action on more than one row.
const CLIENT = {
  id: "55555555-0000-0000-0000-000000000001",
  name: "Aurora Retail Group",
  primaryContactUsername: "priya.raman@aurora.example",
  contractCount: 1,
};
const CLIENT_TESTERS = [
  { id: "33333333-0000-0000-0000-000000000001", clientId: CLIENT.id, username: "priya.raman@aurora.example", isPrimaryContact: true },
  { id: "33333333-0000-0000-0000-000000000003", clientId: CLIENT.id, username: "nadia.okafor@aurora.example", isPrimaryContact: false },
];

const STOCK_UNITS = [
  {
    id: "b0000000-0000-0000-0000-000000000001",
    kind: "SMARTPHONE",
    agentId: AGENTS[0].id,
    agentName: AGENTS[0].name,
    agentCurrency: AGENTS[0].currency,
    model: "iPhone 15 Pro",
    serial: "F2LW9X3RQD",
    status: "ACTIVE",
    fromContractId: "22222222-0000-0000-0000-000000000001",
    fromClientName: "Aurora Retail Group",
  },
  {
    id: "b0000000-0000-0000-0000-000000000002",
    kind: "SIM_CARD",
    agentId: AGENTS[0].id,
    agentName: AGENTS[0].name,
    agentCurrency: AGENTS[0].currency,
    number: "+1-555-0142",
    carrierId: "c1",
    carrierName: "AT&T",
    carrierArchived: false,
    flavor: "POSTPAID",
    postpaidPlanId: "c1-postpaidPlans-0",
    postpaidPlanName: "Unlimited Starter",
    postpaidPlanArchived: false,
    monthlyFeeAmount: 65.99,
    status: "ACTIVE",
    fromContractId: "22222222-0000-0000-0000-000000000002",
    fromClientName: "Meridian Logistics",
  },
];

// real-agent-dashboard: the Agent's two Contracts and their Requests. `createdAt` is computed from
// the stub's own current time (now minus a fixed age), never a calendar date, so the dashboard's
// "raised <age>" rows read the same on every run and the goldens stop drifting. Four statuses
// (Submitted, In Progress, Completed, Pending Approval) and seven Requests across both Contracts:
// five fill the Recent Requests card, two older ones fall off it, and four are open.
const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;
const AGENT_CONTRACTS = [
  {
    id: "22222222-0000-0000-0000-000000000001",
    clientId: "55555555-0000-0000-0000-000000000001",
    clientName: "Aurora Retail Group",
    agentId: AGENTS[0].id,
    agentName: AGENTS[0].name,
    country: "UNITED_STATES",
    currency: "USD",
  },
  {
    id: "22222222-0000-0000-0000-000000000002",
    clientId: "55555555-0000-0000-0000-000000000002",
    clientName: "Meridian Logistics",
    agentId: AGENTS[0].id,
    agentName: AGENTS[0].name,
    country: "UNITED_STATES",
    currency: "USD",
  },
];

// [contract index, type, status, raisedByUsername, age in ms]. Ages carry an extra couple of hours
// so the elapsed whole-day count stays put however long a render takes.
const AGENT_REQUESTS = [
  [0, "TOPUP", "SUBMITTED", "nadia.okafor@aurora.example", 2 * HOUR_MS],
  [1, "OTHER", "IN_PROGRESS", "owen.reyes@meridian.example", DAY_MS + 2 * HOUR_MS],
  [0, "REBOOT", "COMPLETED", "nadia.okafor@aurora.example", 2 * DAY_MS + 2 * HOUR_MS],
  [1, "PROVISION_SMARTPHONE", "PENDING_APPROVAL", "owen.reyes@meridian.example", 3 * DAY_MS + 2 * HOUR_MS],
  [0, "SIM_SWAP", "SUBMITTED", "nadia.okafor@aurora.example", 5 * DAY_MS + 2 * HOUR_MS],
  [1, "REBOOT", "IN_PROGRESS", "owen.reyes@meridian.example", 9 * DAY_MS + 2 * HOUR_MS],
  [0, "OTHER", "COMPLETED", "nadia.okafor@aurora.example", 20 * DAY_MS + 2 * HOUR_MS],
];

function agentRequests(contractIndex) {
  const now = Date.now();
  return AGENT_REQUESTS.flatMap(([index, type, status, raisedByUsername, age], position) =>
    index === contractIndex
      ? [
          {
            id: `66666666-0000-0000-0000-00000000000${position + 1}`,
            contractId: AGENT_CONTRACTS[index].id,
            type,
            status,
            raisedByTesterId: `33333333-0000-0000-0000-00000000000${index + 1}`,
            raisedByUsername,
            agentAuthored: false,
            loggedByUsername: raisedByUsername,
            cancellationReason: null,
            description: type === "OTHER" ? "Counter display unit" : null,
            createdAt: new Date(now - age).toISOString(),
          },
        ]
      : [],
  );
}

// real-client-dashboard: the linked Tester's two Contracts, one United States (USD) and one United
// Kingdom (GBP), each with mixed-status Fleet and Requests so the dashboard's filters show in its
// figures. Active Fleet = Smartphones ACTIVE/IN_REPAIR (2 + 1) + SIM Cards ACTIVE (2 + 1) = 6, with
// a Retired unit per Contract left out; Open Requests = Pending Approval, Submitted and In Progress
// (1 + 1 + 1) = 3, with Completed, Rejected and Cancelled left out.
const TESTER_CONTRACTS = [
  {
    id: "22222222-0000-0000-0000-0000000000b1",
    clientId: TESTER_CLIENT.clientId,
    clientName: TESTER_CLIENT.name,
    agentId: AGENTS[0].id,
    agentName: AGENTS[0].name,
    country: "UNITED_STATES",
    currency: "USD",
  },
  {
    id: "22222222-0000-0000-0000-0000000000b2",
    clientId: TESTER_CLIENT.clientId,
    clientName: TESTER_CLIENT.name,
    agentId: AGENTS[1].id,
    agentName: AGENTS[1].name,
    country: "UNITED_KINGDOM",
    currency: "GBP",
  },
];

// [contract index, model, status]
const TESTER_SMARTPHONES = [
  [0, "iPhone 15", "ACTIVE"],
  [0, "Pixel 8", "IN_REPAIR"],
  [0, "Galaxy S21", "RETIRED"],
  [1, "iPhone 14", "ACTIVE"],
  [1, "Galaxy S20", "RETIRED"],
];

// [contract index, number, status]
const TESTER_SIM_CARDS = [
  [0, "+1-555-0201", "ACTIVE"],
  [0, "+1-555-0202", "ACTIVE"],
  [0, "+1-555-0203", "RETIRED"],
  [1, "+44-7700-900201", "ACTIVE"],
  [1, "+44-7700-900202", "RETIRED"],
];

// [contract index, type, status, age in ms]
const TESTER_REQUESTS = [
  [0, "TOPUP", "SUBMITTED", 2 * HOUR_MS],
  [0, "PROVISION_SMARTPHONE", "PENDING_APPROVAL", DAY_MS + 2 * HOUR_MS],
  [0, "REBOOT", "COMPLETED", 3 * DAY_MS + 2 * HOUR_MS],
  [1, "OTHER", "IN_PROGRESS", 2 * DAY_MS + 2 * HOUR_MS],
  [1, "SIM_SWAP", "REJECTED", 5 * DAY_MS + 2 * HOUR_MS],
  [1, "REBOOT", "CANCELLED", 9 * DAY_MS + 2 * HOUR_MS],
];

function testerRows(rows, contractIndex, build) {
  return rows.flatMap((row, position) => (row[0] === contractIndex ? [build(row, position)] : []));
}

function testerSmartphones(contractIndex) {
  return testerRows(TESTER_SMARTPHONES, contractIndex, ([, model, status], position) => ({
    id: `77777777-0000-0000-0000-00000000000${position + 1}`,
    contractId: TESTER_CONTRACTS[contractIndex].id,
    model,
    serial: `SER${position + 1}`,
    owner: "CLIENT",
    status,
  }));
}

function testerSimCards(contractIndex) {
  return testerRows(TESTER_SIM_CARDS, contractIndex, ([, number, status], position) => ({
    id: `88888888-0000-0000-0000-00000000000${position + 1}`,
    contractId: TESTER_CONTRACTS[contractIndex].id,
    number,
    flavor: "PREPAID",
    monthlyFeeAmount: null,
    status,
  }));
}

function testerRequests(contractIndex) {
  const now = Date.now();
  return testerRows(TESTER_REQUESTS, contractIndex, ([, type, status, age], position) => ({
    id: `99999999-0000-0000-0000-00000000000${position + 1}`,
    contractId: TESTER_CONTRACTS[contractIndex].id,
    type,
    status,
    raisedByTesterId: "33333333-0000-0000-0000-0000000000c1",
    raisedByUsername: "dana.whitfield@solsticeretail.example",
    agentAuthored: false,
    loggedByUsername: "dana.whitfield@solsticeretail.example",
    cancellationReason: null,
    description: type === "OTHER" ? "Counter display unit" : null,
    createdAt: new Date(now - age).toISOString(),
  }));
}

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
  // real-client-dashboard: the caller's own Client; any non-Tester, or an unlinked Tester, is a 404.
  if (url.pathname === "/api/me/client" && request.method === "GET") {
    if (caller.role !== "TESTER" || caller.unlinked) return send(response, 404);
    if (caller.failingIdentity) return send(response, 500);
    return send(response, 200, TESTER_CLIENT);
  }
  // real-agent-dashboard: the caller's own Agent — Jordan Ellis, with a salary and a non-zero
  // Rollout Advance so the dashboard's "+ <amount> Rollout Advance" meta is exercised.
  if (url.pathname === "/api/me/agent" && request.method === "GET") {
    if (caller.role !== "AGENT" || caller.unlinked) return send(response, 404);
    if (caller.failingIdentity) return send(response, 500);
    const { id, name, country, currency } = AGENTS[0];
    return send(response, 200, {
      agentId: id,
      name,
      country,
      currency,
      salaryAmount: 3200,
      rolloutAdvanceAmount: 500,
    });
  }
  // real-agent-dashboard: this month's Agent Invoice — a Draft with a fixed billingMonth, so the
  // Local Support Fees label never follows the wall clock. The degraded token gets a 500.
  const invoiceMatch = url.pathname.match(/^\/api\/agents\/([^/]+)\/invoice$/);
  if (invoiceMatch && request.method === "GET") {
    if (caller.role !== "AGENT" || invoiceMatch[1] !== AGENTS[0].id) return send(response, 403);
    if (caller.degraded) return send(response, 500);
    return send(response, 200, {
      id: "c0000000-0000-0000-0000-000000000001",
      agentId: AGENTS[0].id,
      billingMonth: "2026-09-01",
      status: "DRAFT",
      currency: AGENTS[0].currency,
      localSupportFees: 1250.5,
      salary: 3200,
      rolloutAdvanceRepayment: -250,
      rolloutAdvanceNewAdvance: 500,
      totalAmount: 4700.5,
      sentAt: null,
      approvedAt: null,
      paidAt: null,
    });
  }
  // reset-an-agents-password-ui ticket: the Manager's Agent page reads the Agent's standing amounts
  // and the reset route answers with a fixed password, so the reveal's golden never drifts. An
  // Agent without a Login answers the real route's 409 AGENT_HAS_NO_LOGIN.
  const standingMatch = url.pathname.match(/^\/api\/agents\/([^/]+)\/standing-amounts$/);
  if (standingMatch && request.method === "GET") {
    const agent = AGENTS.find((candidate) => candidate.id === standingMatch[1]);
    if (caller.role !== "MANAGER") return send(response, 403);
    return agent ? send(response, 200, { salaryAmount: agent.salaryAmount, rolloutAdvanceAmount: 500 }) : send(response, 404);
  }
  const resetMatch = url.pathname.match(/^\/api\/agents\/([^/]+)\/login\/password$/);
  if (resetMatch && request.method === "POST") {
    const agent = AGENTS.find((candidate) => candidate.id === resetMatch[1]);
    if (caller.role !== "MANAGER") return send(response, 403);
    if (!agent) return send(response, 404);
    if (!agent.loginUsername) return send(response, 409, { code: "AGENT_HAS_NO_LOGIN" });
    return send(response, 200, { password: RESET_PASSWORD });
  }
  // reset-a-testers-password-ui ticket: the Manager's Client page reads the Client list and its
  // Testers; the Tester reset route answers with the same fixed password as the Agent's.
  if (url.pathname === "/api/clients" && request.method === "GET") {
    return caller.role === "MANAGER" ? send(response, 200, [CLIENT]) : send(response, 403);
  }
  const testersMatch = url.pathname.match(/^\/api\/clients\/([^/]+)\/testers$/);
  if (testersMatch && request.method === "GET") {
    if (caller.role !== "MANAGER") return send(response, 403);
    return testersMatch[1] === CLIENT.id ? send(response, 200, CLIENT_TESTERS) : send(response, 404);
  }
  const testerResetMatch = url.pathname.match(/^\/api\/clients\/([^/]+)\/testers\/([^/]+)\/password$/);
  if (testerResetMatch && request.method === "POST") {
    if (caller.role !== "MANAGER") return send(response, 403);
    const known = testerResetMatch[1] === CLIENT.id && CLIENT_TESTERS.some((tester) => tester.id === testerResetMatch[2]);
    return known ? send(response, 200, { password: RESET_PASSWORD }) : send(response, 404);
  }
  if (url.pathname === "/api/contracts" && request.method === "GET") {
    if (caller.role === "TESTER") {
      return caller.contractsFailing ? send(response, 500) : send(response, 200, TESTER_CONTRACTS);
    }
    return caller.role === "AGENT" ? send(response, 200, AGENT_CONTRACTS) : send(response, 403);
  }
  // real-client-dashboard: a Tester's own Contracts' Fleet and Requests. The degraded token's second
  // Contract answers 500 on SIM Cards and Requests (Smartphones still answer).
  const testerContractMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/(smartphones|sim-cards|requests)$/);
  if (testerContractMatch && request.method === "GET" && caller.role === "TESTER") {
    const contractIndex = TESTER_CONTRACTS.findIndex((contract) => contract.id === testerContractMatch[1]);
    if (contractIndex === -1) return send(response, 403);
    const resource = testerContractMatch[2];
    if (caller.degraded && contractIndex === 1 && resource !== "smartphones") return send(response, 500);
    if (resource === "smartphones") return send(response, 200, testerSmartphones(contractIndex));
    if (resource === "sim-cards") return send(response, 200, testerSimCards(contractIndex));
    return send(response, 200, testerRequests(contractIndex));
  }
  const requestsMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/requests$/);
  if (requestsMatch && request.method === "GET") {
    const contractIndex = AGENT_CONTRACTS.findIndex((contract) => contract.id === requestsMatch[1]);
    if (caller.role !== "AGENT" || contractIndex === -1) return send(response, 403);
    if (caller.degraded && contractIndex === 1) return send(response, 500);
    return send(response, 200, agentRequests(contractIndex));
  }
  if (url.pathname === "/api/carriers" && request.method === "GET") {
    const country = url.searchParams.get("country") ?? caller.country;
    return CURRENCY[country] ? send(response, 200, catalog(country)) : send(response, 400);
  }
  if (url.pathname === "/api/pending-requests" && request.method === "GET") {
    return caller.role === "MANAGER" ? send(response, 200, PENDING_REQUESTS) : send(response, 403);
  }
  if (url.pathname === "/api/agents" && request.method === "GET") {
    return caller.role === "MANAGER" ? send(response, 200, AGENTS) : send(response, 403);
  }
  // agent-stock ticket: an Agent always sees their own Stock; the Manager sees every Agent's
  // unless `agentId` narrows it — mirrors StockController#resolveScopeAgentId.
  if (url.pathname === "/api/stock" && request.method === "GET") {
    if (caller.role === "TESTER") return send(response, 403);
    const agentId = url.searchParams.get("agentId");
    if (caller.role === "AGENT") {
      return send(response, 200, STOCK_UNITS.filter((unit) => unit.agentId === AGENTS[0].id));
    }
    const units = agentId ? STOCK_UNITS.filter((unit) => unit.agentId === agentId) : STOCK_UNITS;
    return send(response, 200, units);
  }
  return send(response, 404);
}).listen(PORT, "127.0.0.1");
