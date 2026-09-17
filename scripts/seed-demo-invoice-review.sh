#!/usr/bin/env bash
# Seeds demo data through the real HTTP API so a Manager can see the actual invoice review UI:
#   - /manager/contracts/<id>  → a SENT Client Invoice (postpaid base + Fees + a carrier invoice file)
#   - /manager/agents/<id>     → Jordan Ellis's SENT Agent Invoice (override / approve / mark paid)
#
# Jordan Ellis (agent@example.com) is the only seeded Agent with a login, so it's the only Agent
# whose invoice can be sent. Invoices are keyed to the *current* calendar month, which is why this
# is a script against a running backend rather than a Flyway migration: re-run it next month and
# it seeds that month's invoices. It's idempotent within a month — existing entities are reused,
# and invoices already sent/approved are left alone.
#
# Usage: scripts/seed-demo-invoice-review.sh [backend-url]   (default http://localhost:8080)
set -euo pipefail

API="${1:-http://localhost:8080}"
AGENT_ID="55555555-5555-5555-5555-555555555555"
CLIENT_NAME="Northwind Labs"
TESTER_USERNAME="tester@northwind-labs.example"
TESTER_PASSWORD="NorthwindDemo123!"

login() {
  curl -sf -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | jq -r .token
}

# call <token> <method> <path> [json-body]
call() {
  local token="$1" method="$2" path="$3" body="${4:-}"
  local args=(-sS -X "$method" "$API$path" -H "Authorization: Bearer $token" -w '\n%{http_code}')
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out code
  out="$(curl "${args[@]}")"
  code="${out##*$'\n'}"
  out="${out%$'\n'*}"
  if [[ "$code" -ge 400 ]]; then
    echo "✗ $method $path → $code: $out" >&2
    exit 1
  fi
  printf '%s' "$out"
}

MANAGER="$(login manager@example.com 'ChangeMe123!')"
AGENT="$(login agent@example.com 'AgentDemo123!')"

# --- Manager: Client, Tester, Contract with Jordan Ellis -------------------------------------
CLIENT_ID="$(call "$MANAGER" GET /api/clients | jq -r --arg n "$CLIENT_NAME" '.[] | select(.name == $n) | .id' | head -1)"
if [[ -z "$CLIENT_ID" ]]; then
  CLIENT_ID="$(call "$MANAGER" POST /api/clients "{\"name\":\"$CLIENT_NAME\"}" | jq -r .id)"
  echo "✓ created Client $CLIENT_NAME"
fi

TESTER_ID="$(call "$MANAGER" GET "/api/clients/$CLIENT_ID/testers" | jq -r --arg u "$TESTER_USERNAME" '.[] | select(.username == $u) | .id' | head -1)"
if [[ -z "$TESTER_ID" ]]; then
  TESTER_ID="$(call "$MANAGER" POST "/api/clients/$CLIENT_ID/testers" \
    "{\"username\":\"$TESTER_USERNAME\",\"password\":\"$TESTER_PASSWORD\",\"isPrimaryContact\":true}" | jq -r .id)"
  echo "✓ created Tester $TESTER_USERNAME"
fi

CONTRACT_ID="$(call "$MANAGER" GET /api/contracts | jq -r --arg c "$CLIENT_ID" --arg a "$AGENT_ID" '.[] | select(.clientId == $c and .agentId == $a) | .id' | head -1)"
if [[ -z "$CONTRACT_ID" ]]; then
  CONTRACT_ID="$(call "$MANAGER" POST /api/contracts "{\"clientId\":\"$CLIENT_ID\",\"agentId\":\"$AGENT_ID\"}" | jq -r .id)"
  echo "✓ created Contract $CLIENT_NAME — Jordan Ellis"
fi

# --- Manager: Fleet (only on an empty Fleet) ----------------------------------------------------
if [[ "$(call "$MANAGER" GET "/api/contracts/$CONTRACT_ID/sim-cards" | jq length)" == "0" ]]; then
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/smartphones" '{"model":"Pixel 9","serial":"NW-PX9-0001","assignedTo":"Northwind QA"}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/smartphones" '{"model":"iPhone 16","serial":"NW-IP16-0002","assignedTo":"Northwind QA"}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0101","carrier":"Verizon","flavor":"POSTPAID","monthlyFeeAmount":65.00}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0102","carrier":"T-Mobile","flavor":"POSTPAID","monthlyFeeAmount":50.00}' >/dev/null
  call "$MANAGER" POST "/api/contracts/$CONTRACT_ID/sim-cards" '{"number":"+1 415 555 0103","carrier":"Mint Mobile","flavor":"PREPAID"}' >/dev/null
  echo "✓ added Fleet: 2 smartphones, 2 postpaid SIMs (\$115/mo), 1 prepaid SIM"
fi

# --- Agent: Fees, carrier invoice file, send both invoices -----------------------------------
CLIENT_INVOICE_STATUS="$(call "$AGENT" GET "/api/contracts/$CONTRACT_ID/client-invoice" | jq -r .status)"
if [[ "$CLIENT_INVOICE_STATUS" == "DRAFT" ]]; then
  if [[ "$(call "$AGENT" GET "/api/contracts/$CONTRACT_ID/fees" | jq length)" == "0" ]]; then
    call "$AGENT" POST "/api/contracts/$CONTRACT_ID/fees" \
      "{\"feeType\":\"TOPUP\",\"amount\":25.00,\"description\":\"Prepaid top-up for Mint Mobile SIM\",\"testerId\":\"$TESTER_ID\"}" >/dev/null
    call "$AGENT" POST "/api/contracts/$CONTRACT_ID/fees" \
      "{\"feeType\":\"REPAIR\",\"amount\":140.00,\"description\":\"Pixel 9 screen replacement\",\"testerId\":\"$TESTER_ID\"}" >/dev/null
    echo "✓ logged Fees: top-up \$25, repair \$140"
  fi

  if [[ "$(call "$AGENT" GET "/api/contracts/$CONTRACT_ID/client-invoice/files" | jq length)" == "0" ]]; then
    PDF="$(mktemp -t verizon-invoice).pdf"
    printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\ntrailer<</Root 1 0 R>>\n%%%%EOF\n' >"$PDF"
    curl -sf -X POST "$API/api/contracts/$CONTRACT_ID/client-invoice/files" -H "Authorization: Bearer $AGENT" \
      -F "file=@$PDF;type=application/pdf;filename=verizon-invoice-$(date +%Y-%m).pdf" >/dev/null
    rm -f "$PDF"
    echo "✓ attached carrier invoice file"
  fi

  call "$AGENT" POST "/api/contracts/$CONTRACT_ID/client-invoice/send" >/dev/null
  echo "✓ sent Client Invoice"
else
  echo "• Client Invoice already $CLIENT_INVOICE_STATUS — left as is"
fi

AGENT_INVOICE_STATUS="$(call "$AGENT" GET "/api/agents/$AGENT_ID/invoice" | jq -r .status)"
if [[ "$AGENT_INVOICE_STATUS" == "DRAFT" ]]; then
  call "$AGENT" POST "/api/agents/$AGENT_ID/invoice/send" >/dev/null
  echo "✓ sent Agent Invoice"
else
  echo "• Agent Invoice already $AGENT_INVOICE_STATUS — left as is"
fi

echo
echo "Log in as manager@example.com / ChangeMe123! and review:"
echo "  Client Invoice: http://localhost:3000/manager/contracts/$CONTRACT_ID"
echo "  Agent Invoice:  http://localhost:3000/manager/agents/$AGENT_ID"
