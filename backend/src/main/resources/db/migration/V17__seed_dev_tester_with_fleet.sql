-- Seed a second dev Tester login, fully wired to a Client/Contract/Fleet, for local manual
-- testing. The original tester@example.com (V3) is deliberately left unlinked -- it's the
-- fixture ContractApiTest#anUnlinkedAgentOrTesterSeesNoContracts and IntegrationTest's
-- createTesterAndLogin javadoc rely on to exercise the "no Tester profile for this login" /
-- "sees no Contracts" paths -- so this adds a distinct login rather than repurposing it.
--
-- Password for the seeded demo Tester is "DemoTesterDemo123!" (bcrypt hash below), for local dev
-- and integration-test login only.
INSERT INTO users (id, tenant_id, username, password_hash, role)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    '11111111-1111-1111-1111-111111111111',
    'demo.tester@example.com',
    '$2y$10$bQ3tnJiDsdhOiIWFIO4rdeDSJb90fhI7rtQTMBNyf98PeZsFO0LlG',
    'TESTER'
);

-- A dev Client for this Tester to belong to. The Contract below needs its own Agent, distinct
-- from the already-seeded "Jordan Ellis" (V5): ContractApiTest#anUnlinkedAgentOrTesterSeesNoContracts
-- asserts that agent@example.com's Agent holds zero Contracts, so reusing it here would break
-- that invariant.
INSERT INTO clients (id, tenant_id, name)
VALUES (
    '77777777-7777-7777-7777-777777777777',
    '11111111-1111-1111-1111-111111111111',
    'Demo Client'
);

INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '11111111-1111-1111-1111-111111111111',
    'Demo Agent',
    'UNITED_STATES',
    'USD',
    2000.00
);

INSERT INTO testers (id, tenant_id, client_id, user_id, is_primary_contact)
VALUES (
    '88888888-8888-8888-8888-888888888888',
    '11111111-1111-1111-1111-111111111111',
    '77777777-7777-7777-7777-777777777777',
    '99999999-9999-9999-9999-999999999999',
    true
);

-- currency copied from the Demo Agent's currency (USD), matching how ContractController
-- resolves it at creation time.
INSERT INTO contracts (id, tenant_id, client_id, agent_id, currency)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '11111111-1111-1111-1111-111111111111',
    '77777777-7777-7777-7777-777777777777',
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    'USD'
);

-- A couple of Fleet items so the demo Tester's Fleet view isn't empty either.
INSERT INTO smartphones (id, tenant_id, contract_id, model, serial, assigned_to, status)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Pixel 8',
    'DEMO-PX8-0001',
    'Demo Tester',
    'ACTIVE'
);

INSERT INTO sim_cards (id, tenant_id, contract_id, number, carrier, flavor, monthly_fee_amount, status)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '+1 555 0100',
    'Verizon',
    'POSTPAID',
    45.00,
    'ACTIVE'
);
