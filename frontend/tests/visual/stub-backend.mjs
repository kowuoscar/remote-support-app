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

function catalog(country) {
  const carriers = [
    { id: "c1", name: "AT&T", archivedAt: null },
    { id: "c2", name: "T-Mobile", archivedAt: null },
    { id: "c3", name: "Verizon", archivedAt: null },
    { id: "c4", name: "Sprint", archivedAt: "2024-04-01T00:00:00Z" },
  ].map((carrier) => ({ ...carrier, country }));
  return { country, currency: CURRENCY[country], carriers };
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
  if (url.pathname === "/api/carriers" && request.method === "GET") {
    const country = url.searchParams.get("country") ?? caller.country;
    return CURRENCY[country] ? send(response, 200, catalog(country)) : send(response, 400);
  }
  return send(response, 404);
}).listen(PORT, "127.0.0.1");
