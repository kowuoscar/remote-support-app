-- Seed one Agent and one Tester in the same dev Tenant as the seeded Manager (V2), purely so the
-- login flow built in auth-login-flow can be exercised end-to-end for every role that has a
-- dedicated shell this iteration (SuperAdmin has no shell yet; see spec.md Non-goals).
--
-- Password for the seeded Agent is "AgentDemo123!" (bcrypt hash below), for local dev and
-- integration-test login only.
INSERT INTO users (id, tenant_id, username, password_hash, role)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'agent@example.com',
    '$2a$10$gKAMAmQqBCJNkj6.eup.l.nzojXOTBmPogd.iltpclgxgJIF7vmuK',
    'AGENT'
);

-- Password for the seeded Tester is "TesterDemo123!" (bcrypt hash below), for local dev and
-- integration-test login only.
INSERT INTO users (id, tenant_id, username, password_hash, role)
VALUES (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    'tester@example.com',
    '$2a$10$UjbhPGQP9rmaQgzOyHLE2u6tsUDl/aM1bFBpCbv.0FuU1edkZ5Jj.',
    'TESTER'
);
