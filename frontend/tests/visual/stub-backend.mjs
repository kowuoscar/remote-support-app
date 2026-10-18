// A fixed, in-memory stand-in for the backend, for the visual-regression suite only
// (playwright.config.ts). Goldens must not depend on a live database, so backend-driven pages
// captured by the suite (the Carriers pages) read this fixture instead. The caller's role comes
// from the placeholder session token the spec sets; an unknown token gets a 401 on every route,
// which is what the dashboards' backend-driven regions saw before this stub existed.
import { createServer } from "node:http";

const PORT = Number(process.env.STUB_BACKEND_PORT ?? 4174);

const ROLES = {
  "visual-agent-session": { username: "agent@example.com", role: "AGENT", country: "UNITED_STATES" },
  // real-manager-dashboard: the Manager is named, so the dashboard's chip reads
  // "Yazan Abuhassan · Manager". The empty session is an unnamed Manager (the chip falls back to the
  // username); the degraded one answers 500 on GET /api/me and GET /api/agents. Not captured as
  // goldens; both are asserted in surfaces.spec.ts.
  "visual-manager-session": { username: "manager@example.com", role: "MANAGER", name: "Yazan Abuhassan" },
  "visual-manager-empty-session": { username: "manager@example.com", role: "MANAGER", emptyTotals: true },
  "visual-manager-degraded-session": {
    username: "manager@example.com",
    role: "MANAGER",
    name: "Yazan Abuhassan",
    degraded: true,
  },
  // side-list-row-status: an Agent with four Contracts and one Client Invoice of each state, for the
  // Client Invoices page's goldens. A token of its own, because the Agent dashboard's goldens read the
  // two-Contract list below. The second token's Birch Telecom (UK) invoice read answers 500.
  "visual-agent-invoices-session": { username: "agent@example.com", role: "AGENT", country: "UNITED_STATES", invoices: true },
  // carrier-invoice-per-sim: the same Client Invoices with file storage unavailable (the paused files
  // section), and the Manager's by-id Client Invoice detail below.
  "visual-agent-invoices-paused-session": {
    username: "agent@example.com",
    role: "AGENT",
    country: "UNITED_STATES",
    invoices: true,
    filesPaused: true,
  },
  "visual-agent-invoice-failing-session": {
    username: "agent@example.com",
    role: "AGENT",
    country: "UNITED_STATES",
    invoices: true,
    failingInvoiceContract: "22222222-0000-0000-0000-0000000000d2",
  },
  // side-list-row-one-line: the same failing invoice, but the earlier sent-back invoice of that
  // Contract stays listed, so its row carries the "Sent back" mark with no status badge.
  "visual-agent-invoice-failed-marked-session": {
    username: "agent@example.com",
    role: "AGENT",
    country: "UNITED_STATES",
    invoices: true,
    failingInvoiceContract: "22222222-0000-0000-0000-0000000000d2",
    keepSentBackMark: true,
  },
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
  // deactivate-an-agents-login-ui ticket: an Agent whose Login is deactivated, for the reactivate
  // confirm step's golden. Its name is no wider than "Jordan Ellis" so /manager/stock's auto-width
  // Agent filter (fed by this list) does not change size.
  {
    id: "a0000000-0000-0000-0000-000000000003",
    name: "Ana Costa",
    country: "UNITED_STATES",
    currency: "USD",
    salaryAmount: 2600,
    contractCount: 1,
    loginUsername: "ana@example.com",
    loginDeactivatedAt: "2026-10-02T09:14:00Z",
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
  // deactivate-a-login: a Tester whose Login is deactivated, for the reactivate confirm step.
  { id: "33333333-0000-0000-0000-000000000004", clientId: CLIENT.id, username: "tomas.lindqvist@aurora.example", isPrimaryContact: false, deactivatedAt: "2026-10-02T09:14:00Z" },
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

// side-list-row-status: the Client Invoices page's four Contracts (two share a Client name, so the
// side list shows their Country) and each one's current-month invoice, with fixed dates so the
// goldens never follow the clock: a Draft, a SENT, an APPROVED and a Draft the Manager sent back.
const INVOICE_CONTRACTS = [
  ["d1", "Aurora Retail Group", "UNITED_STATES", "USD"],
  ["d2", "Birch Telecom", "UNITED_KINGDOM", "GBP"],
  ["d3", "Birch Telecom", "MEXICO", "MXN"],
  ["d4", "Cedar Mobile", "SPAIN", "EUR"],
].map(([key, clientName, country, currency], index) => ({
  id: `22222222-0000-0000-0000-0000000000${key}`,
  clientId: `55555555-0000-0000-0000-0000000000${key}`,
  clientName,
  agentId: AGENTS[0].id,
  agentName: AGENTS[0].name,
  country,
  currency,
  index,
}));
const INVOICE_STATES = [
  { status: "DRAFT" },
  { status: "SENT", sentAt: "2026-09-10T10:00:00Z" },
  { status: "APPROVED", sentAt: "2026-09-10T10:00:00Z", approvedAt: "2026-09-11T10:00:00Z" },
  { status: "DRAFT", sentBackAt: "2026-09-12T10:00:00Z", sentBackReason: "The roaming Fee is missing its receipt." },
];
// invoice-lines-layout: the first Contract's draft (Aurora, the page's first selection) carries the
// five line shapes the Agent's Client Invoice lays out: a plain, an edited and a cancelled Postpaid
// SIM, a Fee with a description and an edited Fee with none. Base 25 + 30 + 25 = 80, Fees 10 + 8 = 18.
const SIM_LINES = [
  { simCardId: "66666666-0000-0000-0000-0000000000a1", number: "+44 7700 900123", monthlyFeeAmount: 25, cancellationEffectiveDate: null, amount: 25, computedAmount: 25, edited: false, carrierName: "Verizon", carrierInvoiceFileId: "aaaaaaaa-0000-0000-0000-0000000000f1" },
  { simCardId: "66666666-0000-0000-0000-0000000000a2", number: "+44 7700 900456", monthlyFeeAmount: 25, cancellationEffectiveDate: null, amount: 30, computedAmount: 25, edited: true, carrierName: "Verizon", carrierInvoiceFileId: null },
  { simCardId: "66666666-0000-0000-0000-0000000000a3", number: "+44 7700 900789", monthlyFeeAmount: 25, cancellationEffectiveDate: "2026-09-03", amount: 25, computedAmount: 25, edited: false, carrierName: null, carrierInvoiceFileId: null },
];
// carrier-invoice-check-mark: the first line's SIM has its Carrier Invoice File (the check mark); the
// second has none. The name is the stub's own string.
const SIM_FILES = [
  { id: "aaaaaaaa-0000-0000-0000-0000000000f1", clientInvoiceId: "99999999-0000-0000-0000-0000000000d1", simCardId: "66666666-0000-0000-0000-0000000000a1", filename: "Aurora - September 2026 - Verizon - +44 7700 900123.pdf", contentType: "application/pdf", sizeBytes: 48213, uploadedAt: "2026-09-15T09:30:00Z", deletedAt: null },
];
const FEE_LINES = [
  { id: "77777777-0000-0000-0000-0000000000a1", contractId: "22222222-0000-0000-0000-0000000000d1", requestId: "88888888-0000-0000-0000-0000000000a1", requestType: "TOPUP", feeType: "TOPUP", amount: 10, currency: "USD", description: "Customer asked for extra data", billingMonth: "2026-09-01", createdAt: "2026-09-12T10:00:00Z", computedAmount: 10, edited: false },
  { id: "77777777-0000-0000-0000-0000000000a2", contractId: "22222222-0000-0000-0000-0000000000d1", requestId: "88888888-0000-0000-0000-0000000000a2", requestType: "TOPUP", feeType: "OTHER", amount: 8, currency: "USD", description: null, billingMonth: "2026-09-01", createdAt: "2026-09-14T10:00:00Z", computedAmount: 12, edited: true },
];
function clientInvoiceFor(contract, filesAvailable = true) {
  const withLines = contract.index === 0;
  return {
    id: `99999999-0000-0000-0000-0000000000${contract.id.slice(-2)}`,
    contractId: contract.id,
    billingMonth: "2026-09-01",
    currency: contract.currency,
    baseAmount: withLines ? 80 : 120 + contract.index * 10,
    basePostpaidSims: withLines ? SIM_LINES : [],
    feeLines: withLines ? FEE_LINES : [],
    totalAmount: withLines ? 98 : 120 + contract.index * 10,
    files: withLines ? SIM_FILES : [],
    filesAvailable,
    sentAt: null,
    approvedAt: null,
    ...INVOICE_STATES[contract.index],
  };
}

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

// [contract index, number, status, flavor (default PREPAID)]. A POSTPAID row sits on the stub
// catalog's first Carrier and its cheapest plan (correct-a-postpaid-plan ticket), so the Fleet
// goldens show the plan "Edit" button.
const TESTER_SIM_CARDS = [
  [0, "+1-555-0201", "ACTIVE", "POSTPAID"],
  [0, "+1-555-0202", "ACTIVE"],
  [0, "+1-555-0203", "RETIRED"],
  [1, "+44-7700-900201", "ACTIVE"],
  [1, "+44-7700-900202", "RETIRED"],
  // A 60-character Plan name (side-lists-and-tabs spec): the Fleet cuts it with "…". Retired so no
  // dashboard count moves, and on the Tester's second Contract, which the Manager's Contract
  // goldens never show.
  [1, "+44-7700-900203", "RETIRED", "POSTPAID"],
];

const LONG_PLAN_NAME = "Unlimited Premium Postpaid Plan with Roaming and 5G Datapack";

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
  return testerRows(TESTER_SIM_CARDS, contractIndex, ([, number, status, flavor = "PREPAID"], position) => {
    const sim = {
      id: `88888888-0000-0000-0000-00000000000${position + 1}`,
      contractId: TESTER_CONTRACTS[contractIndex].id,
      number,
      flavor,
      monthlyFeeAmount: null,
      status,
    };
    if (flavor !== "POSTPAID") return sim;
    const [plan] = offers("c1", "postpaidPlans");
    return {
      ...sim,
      carrierId: "c1",
      carrierName: "AT&T",
      carrierArchived: false,
      postpaidPlanId: plan.id,
      postpaidPlanName: contractIndex === 1 ? LONG_PLAN_NAME : plan.name,
      postpaidPlanArchived: false,
      monthlyFeeAmount: plan.price,
    };
  });
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
    if (caller.role === "MANAGER" && caller.degraded) return send(response, 500);
    return send(response, 200, { username: caller.username, role: caller.role, name: caller.name });
  }
  // real-manager-dashboard: two Review Queue items, one of each kind, `waitingSince` computed from the
  // stub's own clock (never a calendar date) so the card's "waiting 2 days" reads the same every run.
  if (url.pathname === "/api/review-queue" && request.method === "GET") {
    if (caller.role !== "MANAGER") return send(response, 403);
    const now = Date.now();
    return send(response, 200, [
      {
        kind: "CLIENT_INVOICE",
        id: "c1000000-0000-0000-0000-000000000001",
        billingMonth: "2026-09-01",
        status: "SENT",
        agentName: AGENTS[0].name,
        currency: "USD",
        totalAmount: 1284.5,
        waitingSince: new Date(now - 3 * DAY_MS - 2 * HOUR_MS).toISOString(),
        contractId: AGENT_CONTRACTS[0].id,
        clientName: "Aurora Retail Group",
        agentId: null,
      },
      {
        kind: "AGENT_INVOICE",
        id: "c2000000-0000-0000-0000-000000000001",
        billingMonth: "2026-09-01",
        status: "SENT",
        agentName: AGENTS[1].name,
        currency: "GBP",
        totalAmount: 2310,
        waitingSince: new Date(now - DAY_MS - 2 * HOUR_MS).toISOString(),
        contractId: null,
        clientName: null,
        agentId: AGENTS[1].id,
      },
    ]);
  }
  // real-manager-dashboard: the Tenant's money for a fixed Billing Month — billed in two currencies
  // (GBP then USD, as the read orders them), payout in one. The empty session has nothing counted
  // (both lists `[]`); the degraded one answers 500.
  if (url.pathname === "/api/tenant-totals" && request.method === "GET") {
    if (caller.role !== "MANAGER") return send(response, 403);
    if (caller.degraded) return send(response, 500);
    if (caller.emptyTotals) return send(response, 200, { billingMonth: "2026-09-01", billed: [], payout: [] });
    return send(response, 200, {
      billingMonth: "2026-09-01",
      billed: [
        { currency: "GBP", amount: 2310 },
        { currency: "USD", amount: 1284.5 },
      ],
      payout: [{ currency: "USD", amount: 2835.99 }],
    });
  }
  // real-client-dashboard: the caller's own Client; any non-Tester, or an unlinked Tester, is a 404.
  if (url.pathname === "/api/me/client" && request.method === "GET") {
    if (caller.role !== "TESTER" || caller.unlinked) return send(response, 404);
    if (caller.failingIdentity) return send(response, 500);
    return send(response, 200, TESTER_CLIENT);
  }
  // real-agent-dashboard: the caller's own Agent — Jordan Ellis, with a salary and a non-zero
  // Rollout Advance so the dashboard's "+ <amount> Rollout Advance" meta is exercised.
  // real-client-dashboard: the latest sent or approved Client Invoice of each of the Tester's
  // Contracts — a SENT one for the first, an APPROVED one for the second. The degraded token's read
  // answers 500.
  if (url.pathname === "/api/me/client/latest-client-invoices" && request.method === "GET") {
    if (caller.role !== "TESTER" || caller.unlinked) return send(response, 404);
    if (caller.degraded) return send(response, 500);
    return send(response, 200, [
      {
        contractId: TESTER_CONTRACTS[0].id,
        invoiceId: "99999999-0000-0000-0000-0000000000c1",
        billingMonth: "2026-08-01",
        status: "SENT",
        currency: "USD",
        totalAmount: 1284.5,
      },
      {
        contractId: TESTER_CONTRACTS[1].id,
        invoiceId: "99999999-0000-0000-0000-0000000000c2",
        billingMonth: "2026-08-01",
        status: "APPROVED",
        currency: "GBP",
        totalAmount: 912,
      },
    ]);
  }
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
    if (caller.invoices) return send(response, 200, INVOICE_CONTRACTS);
    if (caller.role === "TESTER") {
      return caller.contractsFailing ? send(response, 500) : send(response, 200, TESTER_CONTRACTS);
    }
    // real-manager-dashboard: the Manager's Contract count reads this list; it reuses the Agent's two.
    if (caller.role === "MANAGER") return send(response, 200, AGENT_CONTRACTS);
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
  // correct-a-postpaid-plan ticket: a Tester's Fleet reads each of their Contracts' own active
  // catalog, the Contract's Country's (GET /api/contracts/{id}/carriers).
  const testerCarriersMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/carriers$/);
  if (testerCarriersMatch && request.method === "GET" && caller.role === "TESTER") {
    const contract = TESTER_CONTRACTS.find((candidate) => candidate.id === testerCarriersMatch[1]);
    if (!contract) return send(response, 403);
    const active = catalog(contract.country);
    return send(response, 200, { ...active, carriers: active.carriers.filter((carrier) => carrier.archivedAt === null) });
  }
  // correct-a-smartphone-model ticket: the Manager's Contract page reads one Contract's Fleet. It
  // reuses the Agent's first Contract and the Tester fixtures' Smartphone and SIM Card rows, so the
  // golden shows the Edit buttons on a mixed-status Fleet. Its first Contract has a Client Invoice (below).
  const managerFleetMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/(smartphones|sim-cards)$/);
  if (managerFleetMatch && request.method === "GET" && caller.role === "MANAGER") {
    if (!AGENT_CONTRACTS.some((contract) => contract.id === managerFleetMatch[1])) return send(response, 404);
    const rows = managerFleetMatch[1] === AGENT_CONTRACTS[0].id;
    if (managerFleetMatch[2] === "smartphones") {
      return send(response, 200, rows ? testerSmartphones(0).map((phone) => ({ ...phone, contractId: AGENT_CONTRACTS[0].id })) : []);
    }
    return send(response, 200, rows ? testerSimCards(0).map((sim) => ({ ...sim, contractId: AGENT_CONTRACTS[0].id })) : []);
  }
  // agent-corrects-own-fleet ticket: the Agent's Fleet page reads each of their Contracts' Fleet.
  // The first Contract carries the Tester fixtures' Smartphone and SIM Card rows, the second none,
  // so the golden shows the status, serial, Installed-in and correction controls side by side.
  const agentFleetMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/(smartphones|sim-cards)$/);
  if (agentFleetMatch && request.method === "GET" && caller.role === "AGENT") {
    const contractIndex = AGENT_CONTRACTS.findIndex((contract) => contract.id === agentFleetMatch[1]);
    if (contractIndex === -1) return send(response, 403);
    const contractId = AGENT_CONTRACTS[contractIndex].id;
    if (contractIndex !== 0) return send(response, 200, []);
    const rows = agentFleetMatch[2] === "smartphones" ? testerSmartphones(0) : testerSimCards(0);
    return send(response, 200, rows.map((row) => ({ ...row, contractId })));
  }
  // manager-contracts-one-page: the Manager's Contracts page reads the selected Contract's current-month
  // Client Invoice; the first Contract carries the same draft the Agent's first Client Invoice does.
  const managerInvoiceMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/client-invoice$/);
  if (managerInvoiceMatch && request.method === "GET" && caller.role === "MANAGER") {
    return managerInvoiceMatch[1] === AGENT_CONTRACTS[0].id
      ? send(response, 200, clientInvoiceFor({ ...AGENT_CONTRACTS[0], index: 0 }))
      : send(response, 404);
  }
  // side-list-row-status: each Contract's current-month Client Invoice, and the Agent's sent-back list.
  const clientInvoiceMatch = url.pathname.match(/^\/api\/contracts\/([^/]+)\/client-invoice$/);
  if (clientInvoiceMatch && request.method === "GET" && caller.invoices) {
    const contract = INVOICE_CONTRACTS.find((candidate) => candidate.id === clientInvoiceMatch[1]);
    if (!contract) return send(response, 404);
    if (caller.failingInvoiceContract === contract.id) return send(response, 500);
    return send(response, 200, clientInvoiceFor(contract, !caller.filesPaused));
  }
  // carrier-invoice-per-sim: the Manager's Client Invoice detail page reads the same first draft by id.
  const clientInvoiceByIdMatch = url.pathname.match(/^\/api\/client-invoices\/([^/]+)$/);
  if (clientInvoiceByIdMatch && request.method === "GET" && caller.role === "MANAGER") {
    const invoice = clientInvoiceFor({ ...AGENT_CONTRACTS[0], index: 0 });
    return clientInvoiceByIdMatch[1] === invoice.id ? send(response, 200, invoice) : send(response, 404);
  }
  if (url.pathname === "/api/client-invoices/sent-back" && request.method === "GET" && caller.invoices) {
    const contract = INVOICE_CONTRACTS[3];
    const invoice = clientInvoiceFor(contract);
    // sent-back-in-the-side-list: an earlier month's invoice of the SENT Contract, so its row reads
    // "Awaiting approval" plus the "Sent back" mark; oldest first, as the API returns them.
    const sent = INVOICE_CONTRACTS[1];
    // The failing session leaves it out, so its failed row keeps pinning "no status badge" alone.
    const earlier = caller.failingInvoiceContract && !caller.keepSentBackMark ? [] : [
      {
        id: "99999999-0000-0000-0000-0000000000a2",
        contractId: sent.id,
        clientName: sent.clientName,
        country: sent.country,
        billingMonth: "2026-08-01",
        currency: sent.currency,
        sentBackAt: "2026-09-02T10:00:00Z",
        sentBackReason: "The August roaming Fee is missing its receipt.",
      },
    ];
    return send(response, 200, [
      ...earlier,
      {
        id: invoice.id,
        contractId: contract.id,
        clientName: contract.clientName,
        country: contract.country,
        billingMonth: invoice.billingMonth,
        currency: contract.currency,
        sentBackAt: invoice.sentBackAt,
        sentBackReason: invoice.sentBackReason,
      },
    ]);
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
    if (caller.role === "MANAGER" && caller.degraded) return send(response, 500);
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
