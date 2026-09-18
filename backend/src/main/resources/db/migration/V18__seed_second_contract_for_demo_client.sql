-- Give the seeded Demo Client (V17) a second Contract with a second Agent, so the Client
-- Portal's ContractSwitcher (frontend/components/ui/contract-switcher.tsx) has more than one
-- Contract to switch between when logged in as demo.tester@example.com -- V17 alone left every
-- Client in the tenant with at most one Contract, so that dropdown path never rendered locally.
--
-- A different country/currency (UNITED_KINGDOM/GBP vs. the first Contract's USD) makes the two
-- Contracts visibly distinct in the switcher rather than differing only by id.
INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '11111111-1111-1111-1111-111111111111',
    'Priya Shah',
    'UNITED_KINGDOM',
    'GBP',
    1800.00
);

INSERT INTO contracts (id, tenant_id, client_id, agent_id, currency)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '11111111-1111-1111-1111-111111111111',
    '77777777-7777-7777-7777-777777777777',
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'GBP'
);

-- A couple of Fleet items so switching to this Contract in the Client Portal isn't an empty view.
INSERT INTO smartphones (id, tenant_id, contract_id, model, serial, assigned_to, status)
VALUES (
    '12121212-1212-1212-1212-121212121212',
    '11111111-1111-1111-1111-111111111111',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'iPhone 15',
    'DEMO-IP15-0001',
    'Demo Tester',
    'ACTIVE'
);

INSERT INTO sim_cards (id, tenant_id, contract_id, number, carrier, flavor, monthly_fee_amount, status)
VALUES (
    '34343434-3434-3434-3434-343434343434',
    '11111111-1111-1111-1111-111111111111',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '+44 7700 900123',
    'EE',
    'POSTPAID',
    38.00,
    'ACTIVE'
);
