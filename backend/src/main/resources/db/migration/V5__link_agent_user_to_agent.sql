-- Prefactor for fleet-management: an Agent-role User needs to resolve to the Agent record it
-- corresponds to (the same shape Tester already has — a User subtype carrying a foreign key back
-- to its owning entity), so "an Agent views the Fleet of their own Contracts" can be authorized.
-- Unlike Tester (where the User and its owning entity are always created together), an Agent
-- record already exists as a standalone Manager-created entity, separate from an Agent's login —
-- so the link lives as a nullable FK on `users` rather than as a new join table: most Users
-- (MANAGER, TESTER) never populate it, and TESTER already owns its link the other way around
-- (`testers.user_id`).
ALTER TABLE users ADD COLUMN agent_id UUID NULL REFERENCES agents (id);

CREATE INDEX idx_users_agent_id ON users (agent_id);

-- Link the seeded agent@example.com login (V3) to a real Agent row so it resolves like any
-- Manager-created Agent would, keeping the existing login e2e/integration tests working. No
-- Agent row existed for this seeded user before now.
INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)
VALUES (
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    'Jordan Ellis',
    'UNITED_STATES',
    'USD',
    2500.00
);

UPDATE users
SET agent_id = '55555555-5555-5555-5555-555555555555'
WHERE id = '33333333-3333-3333-3333-333333333333';
