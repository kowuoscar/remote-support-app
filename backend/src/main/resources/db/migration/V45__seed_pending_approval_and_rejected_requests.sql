-- Seed data for local development (manager-approves-requests ticket AC: "Seed data holds a
-- Pending Approval and a Rejected Request"), kept minimal -- the final demo-data pass rebuilds the
-- whole story. Both are on the Demo Contract (V17: tenant 111..., contract aaa..., Tester 888...,
-- its own login 999...); the Rejected one is decided by the seeded Manager (V2: user 222...).

-- A Pending Approval Provision Smartphone Request, Tester-raised.
INSERT INTO requests (
    id, tenant_id, contract_id, tester_id, raised_by_user_id, agent_authored, type, status,
    requested_model, created_at
)
VALUES (
    'f4400000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '88888888-8888-8888-8888-888888888888',
    '99999999-9999-9999-9999-999999999999',
    false,
    'PROVISION_SMARTPHONE',
    'PENDING_APPROVAL',
    'iPhone 15 Pro',
    now() - interval '2 days'
);

-- A Rejected Replace SIM Request, naming the Demo Contract's own seeded SIM Card (V17).
INSERT INTO requests (
    id, tenant_id, contract_id, tester_id, raised_by_user_id, agent_authored, type, status,
    target_sim_card_id, rejection_reason, decided_by_user_id, decided_at, created_at
)
VALUES (
    'f4400000-0000-0000-0000-000000000002',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '88888888-8888-8888-8888-888888888888',
    '99999999-9999-9999-9999-999999999999',
    false,
    'REPLACE_SIM',
    'REJECTED',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'The current SIM Card still works fine -- let''s hold off replacing it this month.',
    '22222222-2222-2222-2222-222222222222',
    now() - interval '1 day',
    now() - interval '3 days'
);
