-- A seeded Postpaid SIM Card on a seeded Postpaid Plan (carrier-catalog spec, Seed data;
-- postpaid-sim-plan ticket), so local testing starts with a Fleet row whose monthly fee came from
-- the catalog rather than from a typed-in number.
--
-- On the demo Contract (V17), the only Contract of a United States Agent -- the Country V20/V22
-- seeded Carriers and Plans for. Verizon's "Unlimited Welcome" (V22) prices it; the fee is copied,
-- exactly as SimCardFactory copies it at runtime. The Contract's existing Postpaid SIM (V17) keeps
-- its own 45.00 fee and stays without a Plan, like every Postpaid SIM from before the catalog.
INSERT INTO sim_cards (
    id, tenant_id, contract_id, number, carrier_id, flavor, postpaid_plan_id, monthly_fee_amount, status
)
VALUES (
    '56565656-5656-5656-5656-565656565656',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '+1 555 0101',
    'c0000000-0000-0000-0000-000000000003',
    'POSTPAID',
    'e0000000-0000-0000-0000-000000000005',
    65.00,
    'ACTIVE'
);
