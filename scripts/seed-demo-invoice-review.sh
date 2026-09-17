#!/usr/bin/env bash
# Seeds demo data through the real HTTP API so a Manager can see the actual invoice review UI:
#   - /manager/contracts/<id>  → a SENT Client Invoice (postpaid base + Fees + a carrier invoice file)
#   - /manager/agents/<id>     → Jordan Ellis's SENT Agent Invoice (override / approve / mark paid)
#
# Jordan Ellis (agent@example.com) is the only seeded Agent with a login, so it's the only Agent
# whose invoice can be sent. Invoices are keyed to the *current* calendar month, which is why this
# is a script against a running backend rather than a Flyway migration (migrations also run in the
# Testcontainers integration tests, and seeded invoices would go stale the next month).
#
# Idempotent: existing entities are reused, and an invoice already sent/approved/paid is left
# alone. That makes the review screen one-shot per month — once you approve an invoice, re-running
# won't bring it back to SENT; wait for the next month or reset with `docker compose down -v`.
#
# Sending Jordan's Agent Invoice also makes frontend/tests/e2e/agent-invoice-submission-and-approval
# fail against the same database this month, since that test expects the invoice in draft.
#
# Requires curl and jq. Run after `docker compose up`:
#   scripts/seed-demo-invoice-review.sh [backend-url]   (default http://localhost:8080)
#   FRONTEND_URL=… overrides the printed review links   (default http://localhost:3000)
set -euo pipefail

API="${1:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
CLIENT_NAME="Northwind Labs"
TESTER_USERNAME="tester@northwind-labs.example"
TESTER_PASSWORD="NorthwindDemo123!"

command -v jq >/dev/null || { echo "✗ jq is required" >&2; exit 1; }

# request <label> <curl args...> — prints the response body, exits with the body on HTTP >= 400.
request() {
  local label="$1" out code
  shift
  out="$(curl -sS -w '\n%{http_code}' "$@")"
  code="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [[ "$code" -ge 400 ]]; then
    echo "✗ $label → $code: $out" >&2
    exit 1
  fi
  printf '%s' "$out"
}

# call <token> <method> <path> [json-body]
call() {
  local token="$1" method="$2" path="$3" body="${4:-}"
  local args=(-X "$method" "$API$path" -H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  request "$method $path" "${args[@]}"
}

login() {
  local body
  body="$(jq -n --arg u "$1" --arg p "$2" '{username: $u, password: $p}')"
  request "login $1" -X POST "$API/api/auth/login" -H 'Content-Type: application/json' -d "$body" | jq -r .token
}

# find_or_create <token> <list-path> <jq-filter> <create-path> <create-body> <label>
# Every value substituted before a test is assigned first: `set -e` doesn't fire inside `[[ ]]`.
find_or_create() {
  local list id
  list="$(call "$1" GET "$2")"
  id="$(jq -r "$3 | .id" <<<"$list" | head -1)"
  if [[ -z "$id" ]]; then
    id="$(call "$1" POST "$4" "$5" | jq -r .id)"
    echo "✓ created $6" >&2
  fi
  printf '%s' "$id"
}

MANAGER="$(login manager@example.com 'ChangeMe123!')"
AGENT="$(login agent@example.com 'AgentDemo123!')"
AGENT_ID="$(call "$AGENT" GET /api/me | jq -r .agentId)"

# --- Manager: Client, Tester, Contract with Jordan Ellis -------------------------------------
CLIENT_ID="$(find_or_create "$MANAGER" /api/clients \
  ".[] | select(.name == \"$CLIENT_NAME\")" \
  /api/clients "$(jq -n --arg n "$CLIENT_NAME" '{name: $n}')" "Client $CLIENT_NAME")"

TESTER_ID="$(find_or_create "$MANAGER" "/api/clients/$CLIENT_ID/testers" \
  ".[] | select(.username == \"$TESTER_USERNAME\")" \
  "/api/clients/$CLIENT_ID/testers" \
  "$(jq -n --arg u "$TESTER_USERNAME" --arg p "$TESTER_PASSWORD" '{username: $u, password: $p, isPrimaryContact: true}')" \
  "Tester $TESTER_USERNAME")"

CONTRACT_ID="$(find_or_create "$MANAGER" /api/contracts \
  ".[] | select(.clientId == \"$CLIENT_ID\" and .agentId == \"$AGENT_ID\")" \
  /api/contracts "$(jq -n --arg c "$CLIENT_ID" --arg a "$AGENT_ID" '{clientId: $c, agentId: $a}')" \
  "Contract $CLIENT_NAME — Jordan Ellis")"

# --- Manager: Fleet (each kind only when that list is empty) ---------------------------------
SMARTPHONES="$(call "$MANAGER" GET "/api/contracts/$CONTRACT_ID/smartphones")"
if [[ "$(jq length <<<"$SMARTPHONES")" == "0" ]]; then
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/smartphones" '{"model":"Pixel 9","serial":"NW-PX9-0001","assignedTo":"Northwind QA"}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/smartphones" '{"model":"iPhone 16","serial":"NW-IP16-0002","assignedTo":"Northwind QA"}' >/dev/null
  echo "✓ added 2 smartphones"
fi

SIM_CARDS="$(call "$MANAGER" GET "/api/contracts/$CONTRACT_ID/sim-cards")"
if [[ "$(jq length <<<"$SIM_CARDS")" == "0" ]]; then
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0101","carrier":"Verizon","flavor":"POSTPAID","monthlyFeeAmount":65.00}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0102","carrier":"T-Mobile","flavor":"POSTPAID","monthlyFeeAmount":50.00}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0103","carrier":"Mint Mobile","flavor":"PREPAID"}' >/dev/null
  echo "✓ added 2 postpaid SIMs (\$115/mo) and 1 prepaid SIM"
fi

# --- Agent: this month's Fees, carrier invoice file, send both invoices ----------------------
# The draft Client Invoice's feeLines/files are this month's only (the /fees list spans all months).
CLIENT_INVOICE="$(call "$AGENT" GET "/api/contracts/$CONTRACT_ID/client-invoice")"
CLIENT_INVOICE_STATUS="$(jq -r .status <<<"$CLIENT_INVOICE")"
if [[ "$CLIENT_INVOICE_STATUS" == "DRAFT" ]]; then
  if [[ "$(jq '.feeLines | length' <<<"$CLIENT_INVOICE")" == "0" ]]; then
    call "$AGENT" POST "/api/contracts/$CONTRACT_ID/fees" \
      "$(jq -n --arg t "$TESTER_ID" '{feeType: "TOPUP", amount: 25.00, description: "Prepaid top-up for Mint Mobile SIM", testerId: $t}')" >/dev/null
    call "$AGENT" POST "/api/contracts/$CONTRACT_ID/fees" \
      "$(jq -n --arg t "$TESTER_ID" '{feeType: "REPAIR", amount: 140.00, description: "Pixel 9 screen replacement", testerId: $t}')" >/dev/null
    echo "✓ logged Fees: top-up \$25, repair \$140"
  fi

  if [[ "$(jq '.files | length' <<<"$CLIENT_INVOICE")" == "0" ]]; then
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT
    PDF="$TMP_DIR/carrier-invoice.pdf"
    printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' >"$PDF"
    request "upload carrier invoice file" -X POST "$API/api/contracts/$CONTRACT_ID/client-invoice/files" \
      -H "Authorization: Bearer $AGENT" \
      -F "file=@$PDF;type=application/pdf;filename=verizon-invoice-$(date +%Y-%m).pdf" >/dev/null
    echo "✓ attached carrier invoice file"
  fi

  call "$AGENT" POST "/api/contracts/$CONTRACT_ID/client-invoice/send" >/dev/null
  echo "✓ sent Client Invoice"
else
  echo "• Client Invoice already $CLIENT_INVOICE_STATUS — left as is"
fi

AGENT_INVOICE="$(call "$AGENT" GET "/api/agents/$AGENT_ID/invoice")"
AGENT_INVOICE_STATUS="$(jq -r .status <<<"$AGENT_INVOICE")"
if [[ "$AGENT_INVOICE_STATUS" == "DRAFT" ]]; then
  call "$AGENT" POST "/api/agents/$AGENT_ID/invoice/send" >/dev/null
  echo "✓ sent Agent Invoice"
else
  echo "• Agent Invoice already $AGENT_INVOICE_STATUS — left as is"
fi

echo
echo "Log in as manager@example.com / ChangeMe123! and review:"
echo "  Client Invoice: $FRONTEND_URL/manager/contracts/$CONTRACT_ID"
echo "  Agent Invoice:  $FRONTEND_URL/manager/agents/$AGENT_ID"
