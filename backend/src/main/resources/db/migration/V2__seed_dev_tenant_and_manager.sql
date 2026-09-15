-- Seed data for local development and tests: one Tenant, one Manager user. The MVP runs a
-- single tenant (spec.md Constraints), so this is safe to always apply, not profile-gated.
--
-- Password for the seeded Manager is "ChangeMe123!" (bcrypt hash below), for local dev and
-- integration-test login only.
INSERT INTO tenants (id, name)
VALUES ('11111111-1111-1111-1111-111111111111', 'Default Tenant');

INSERT INTO users (id, tenant_id, username, password_hash, role)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'manager@example.com',
    '$2a$10$95pyl6/AnG6iqnu.s0zRYO6e2jNMzAJI6iQW8r5BSWdSoV8RRafPm',
    'MANAGER'
);
