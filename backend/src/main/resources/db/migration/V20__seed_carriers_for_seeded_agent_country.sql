-- Seed a Carrier catalog for the seeded Agent's Country (Jordan Ellis, V5: UNITED_STATES), so
-- local testing of the Carriers page starts with data (carrier-catalog spec, Seed data). One
-- Carrier is archived, so the "show archived" toggle has something to reveal.
INSERT INTO carriers (id, tenant_id, country, name, archived_at)
VALUES
    ('c0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'UNITED_STATES', 'AT&T', NULL),
    ('c0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'UNITED_STATES', 'T-Mobile', NULL),
    ('c0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'UNITED_STATES', 'Verizon', NULL),
    ('c0000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'UNITED_STATES', 'Sprint', '2024-04-01T00:00:00Z');
