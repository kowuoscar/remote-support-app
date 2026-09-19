-- Seed one Topup Fee bought from a Topup Option (carrier-catalog spec, Seed data; topup-fee-from-
-- option ticket), so local testing shows a Fee that traces back to the catalog. It sits on the
-- Demo Client's United States Contract (V17), raised for the demo Tester, and names V22's T-Mobile
-- "Data Pass 5GB" Option. Like every Fee it traces to a Request: a completed Topup the demo Tester
-- submitted. Its billing month is the month this migration runs in, as a Fee logged then would be.
INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id, agent_authored)
VALUES (
    'f1000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '88888888-8888-8888-8888-888888888888',
    'TOPUP',
    'COMPLETED',
    '99999999-9999-9999-9999-999999999999',
    FALSE
);

INSERT INTO fees (
    id, tenant_id, contract_id, request_id, fee_type, amount, currency, description, billing_month,
    topup_option_id
)
VALUES (
    'f2000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'f1000000-0000-0000-0000-000000000001',
    'TOPUP',
    15.00,
    'USD',
    'Data pass for the demo SIM',
    date_trunc('month', now())::date,
    'd0000000-0000-0000-0000-000000000003'
);
