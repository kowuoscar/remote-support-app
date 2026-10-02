-- A Client Invoice holds its own lines (edit-client-invoice-lines spec, "The model" and
-- "Migration"; store-client-invoice-lines ticket). Additive only: nothing is deleted or rewritten;
-- snapshot_base_amount and client_invoice_fee_snapshots keep their data and stay unused-but-present.
--
-- `amount` is what the Agent billed, `computed_amount` what the computation said for that line;
-- a line is edited when they differ. `kind` fixes which reference is set: POSTPAID_SIM -> a SIM
-- Card, FEE -> a Fee, BASE_AMOUNT (only ever written by this backfill, for invoices sent before
-- lines existed) -> neither.
CREATE TABLE client_invoice_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    client_invoice_id UUID NOT NULL REFERENCES client_invoices (id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('POSTPAID_SIM', 'BASE_AMOUNT', 'FEE')),
    sim_card_id UUID REFERENCES sim_cards (id),
    fee_id UUID REFERENCES fees (id),
    computed_amount NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    edited_at TIMESTAMPTZ,
    edited_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_invoice_lines_invoice_sim UNIQUE (client_invoice_id, sim_card_id),
    CONSTRAINT uq_client_invoice_lines_invoice_fee UNIQUE (client_invoice_id, fee_id),
    CONSTRAINT chk_client_invoice_lines_reference CHECK (
        (kind = 'POSTPAID_SIM' AND sim_card_id IS NOT NULL AND fee_id IS NULL)
        OR (kind = 'FEE' AND fee_id IS NOT NULL AND sim_card_id IS NULL)
        OR (kind = 'BASE_AMOUNT' AND sim_card_id IS NULL AND fee_id IS NULL)
    )
);

CREATE INDEX idx_client_invoice_lines_tenant_id ON client_invoice_lines (tenant_id);
CREATE INDEX idx_client_invoice_lines_client_invoice_id ON client_invoice_lines (client_invoice_id);

ALTER TABLE client_invoices ADD COLUMN lines_stored BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill every SENT and APPROVED invoice; a DRAFT keeps computing live and gets no rows.
INSERT INTO client_invoice_lines (id, tenant_id, client_invoice_id, kind, computed_amount, amount)
SELECT gen_random_uuid(), ci.tenant_id, ci.id, 'BASE_AMOUNT',
       coalesce(ci.snapshot_base_amount, 0), coalesce(ci.snapshot_base_amount, 0)
FROM client_invoices ci
WHERE ci.status IN ('SENT', 'APPROVED');

INSERT INTO client_invoice_lines (id, tenant_id, client_invoice_id, kind, fee_id, computed_amount, amount)
SELECT gen_random_uuid(), s.tenant_id, s.client_invoice_id, 'FEE', s.fee_id, f.amount, f.amount
FROM client_invoice_fee_snapshots s
JOIN fees f ON f.id = s.fee_id
JOIN client_invoices ci ON ci.id = s.client_invoice_id
WHERE ci.status IN ('SENT', 'APPROVED');

UPDATE client_invoices SET lines_stored = TRUE WHERE status IN ('SENT', 'APPROVED');
