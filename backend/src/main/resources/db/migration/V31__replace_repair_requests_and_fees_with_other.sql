-- Repair becomes Other everywhere it already exists (request-types-and-flow spec, Types;
-- other-replaces-repair ticket AC: "A migration turns every Repair Request and Repair Fee into
-- Other, setting the description 'Repair' on a Request that had none"). No Fee amount, billing
-- month or Client/Agent Invoice total moves: this touches only `requests.type`,
-- `requests.description` and `fees.fee_type` -- nothing `fees.amount`, `fees.billing_month`,
-- `client_invoices.snapshot_base_amount` or `client_invoice_fee_snapshots` read from is written
-- here, and Fee rows are never re-keyed (same `id`), so the frozen snapshot's Fee-membership join
-- (V11 migration) still resolves to exactly the same Fees it always did (see
-- SimCardCarrierMigrationTest for the prior-art invoice-total assertion this ticket's own
-- migration test follows).
UPDATE requests
SET type = 'OTHER',
    description = COALESCE(description, 'Repair')
WHERE type = 'REPAIR';

UPDATE fees
SET fee_type = 'OTHER'
WHERE fee_type = 'REPAIR';

-- Narrow both CHECKs back down now that no row still says REPAIR -- the type is gone, not merely
-- unused, matching how V23 dropped sim_cards.carrier once every SIM Card was linked.
ALTER TABLE requests DROP CONSTRAINT requests_type_check;
ALTER TABLE requests ADD CONSTRAINT requests_type_check CHECK (
    type IN ('REBOOT', 'TOPUP', 'SIM_SWAP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'OTHER')
);

ALTER TABLE fees DROP CONSTRAINT fees_fee_type_check;
ALTER TABLE fees ADD CONSTRAINT fees_fee_type_check CHECK (
    fee_type IN ('TOPUP', 'PROVISION_SMARTPHONE', 'PROVISION_SIM', 'OTHER')
);

-- Seed data (other-replaces-repair ticket AC: "Seed data holds at least one Other Request with a
-- description"), kept minimal -- a later demo-data pass rebuilds the whole story. Raised by the
-- demo Tester (V17) on their own demo Contract.
INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id, agent_authored, description)
VALUES (
    'f3000000-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '88888888-8888-8888-8888-888888888888',
    'OTHER',
    'SUBMITTED',
    '99999999-9999-9999-9999-999999999999',
    FALSE,
    'Screen protector is peeling and needs replacing'
);
